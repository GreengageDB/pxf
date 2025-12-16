package org.greenplum.pxf.automation.features.jdbc;

import annotations.WorksWithFDW;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import jsystem.framework.system.SystemManagerImpl;
import org.greenplum.pxf.automation.JdbcDbType;
import org.greenplum.pxf.automation.components.cluster.MultiNodeCluster;
import org.greenplum.pxf.automation.components.cluster.PhdCluster;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.Node;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.SegmentNode;
import org.greenplum.pxf.automation.components.mysql.Mysql;
import org.greenplum.pxf.automation.components.oracle.Oracle;
import org.greenplum.pxf.automation.features.BaseFeature;
import org.greenplum.pxf.automation.structures.tables.basic.Table;
import org.greenplum.pxf.automation.structures.tables.pxf.ExternalTable;
import org.greenplum.pxf.automation.structures.tables.utils.TableFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.List;

import static org.greenplum.pxf.automation.JdbcDbType.*;
import static org.greenplum.pxf.automation.PxfTestConstant.*;
import static org.greenplum.pxf.automation.PxfTestUtil.getCmdResult;
import static org.junit.Assert.assertEquals;

@WorksWithFDW
@Feature("JDBC Timestamp partitioning")
public class JdbcTimestampPartitioningTest extends BaseFeature {

    private static final String[] TABLE_FIELDS = new String[]{
            "id    int",
            "datetime   timestamp"};
    private static final String SELECT_QUERY = "SELECT * FROM ${pxf_read_table}";
    private static final String PXF_TEMP_LOG_PATH = "/tmp/pxf-service-partition.log";
    private static final String SEGMENT_LOG_GREP_COMMAND = "cat " + PXF_TEMP_LOG_PATH + " | grep 'Returning 14 fragments' | wc -l";
    private static final String SOURCE_TABLE_NAME = "source_table";
    private static final String PXF_TABLE_NAME = "${db}_ext_table";
    private ExternalTable externalTable;
    private List<Node> pxfNodes;
    private String pxfLogFile;
    private Table sourceTable;
    private Oracle oracle;
    private Mysql mysql;

    @Override
    protected void beforeClass() throws Exception {
        pxfNodes = ((MultiNodeCluster) cluster).getNode(SegmentNode.class, PhdCluster.EnumClusterServices.pxf);
        String pxfHome = cluster.getPxfHome();
        String restartCommand = pxfHome + "/bin/pxf restart";
        cluster.runCommandOnNodes(pxfNodes, String.format("export PXF_LOG_LEVEL=%s;%s", "trace", restartCommand));
        pxfLogFile = pxfHome + "/" + PXF_LOG_RELATIVE_PATH;
        oracle = (Oracle) SystemManagerImpl.getInstance().getSystemObject(ORACLE.getServerName());
        mysql = (Mysql) SystemManagerImpl.getInstance().getSystemObject(MYSQL.getServerName());
    }

    @BeforeMethod
    protected void beforeMethod() throws Exception {
        sourceTable = new Table(SOURCE_TABLE_NAME, TABLE_FIELDS);
        cleanLogs();
    }

    @Test(groups = {"features", "jdbc"}, dataProvider = "jdbcDbProvider")
    public void testTimestampPartitioning(JdbcDbType jdbcDbType) throws Exception {
        prepareSourceTable(jdbcDbType);
        externalTable = createExternalTable(jdbcDbType);
        gpdb.runQuery(SELECT_QUERY.replace("${pxf_read_table}", externalTable.getName()));
        checkPxfLogs();
        clearDbs(jdbcDbType);
    }

    @DataProvider(name = "jdbcDbProvider")
    private static Object[][] jdbcDbProvider() {
        return new Object[][]{
                {ORACLE},
                {MYSQL},
                {POSTGRES}
        };
    }

    @Step("Prepare source table")
    private void prepareSourceTable(JdbcDbType jdbcDbType) throws Exception {
        switch (jdbcDbType) {
            case ORACLE:
                sourceTable.setSchema("system");
                oracle.createTableAndVerify(sourceTable);
                oracle.runQuery(getInsertQuery(jdbcDbType));
                break;
            case MYSQL:
                mysql.createTableAndVerify(sourceTable);
                mysql.runQuery(getInsertQuery(jdbcDbType));
                break;
            default:
                sourceTable.setDistributionFields(new String[]{"id"});
                gpdb.createTableAndVerify(sourceTable);
                gpdb.runQuery(getInsertQuery(jdbcDbType));
        }
    }

    @Step("Clean logs")
    private void cleanLogs() throws Exception {
        cluster.runCommandOnNodes(pxfNodes, "> " + pxfLogFile);
    }

    private String getInsertQuery(JdbcDbType jdbcDbType) {
        if (jdbcDbType == ORACLE) {
            return "INSERT ALL " +
                    "INTO source_table VALUES(1, TIMESTAMP'2023-01-01 10:00:00') " +
                    "INTO source_table VALUES (1, TIMESTAMP'2023-01-01 10:00:00') " +
                    "INTO source_table VALUES (2, TIMESTAMP'2023-01-02 11:15:00') " +
                    "INTO source_table VALUES (3, TIMESTAMP'2023-01-03 12:30:00') " +
                    "INTO source_table VALUES (4, TIMESTAMP'2023-01-04 13:45:00') " +
                    "INTO source_table VALUES (5, TIMESTAMP'2023-01-05 15:00:00') " +
                    "INTO source_table VALUES (6, TIMESTAMP'2023-01-06 16:15:00') " +
                    "INTO source_table VALUES (7, TIMESTAMP'2023-01-07 17:30:00') " +
                    "INTO source_table VALUES (8, TIMESTAMP'2023-01-08 18:45:00') " +
                    "INTO source_table VALUES (9, TIMESTAMP'2023-01-09 20:00:00') " +
                    "INTO source_table VALUES (10, TIMESTAMP'2023-01-10 21:15:00') " +
                    "SELECT * FROM DUAL";
        } else {
            return "INSERT INTO source_table VALUES " +
                    "(1, '2023-01-01 10:00:00'), " +
                    "(2, '2023-01-02 11:15:00'), " +
                    "(3, '2023-01-03 12:30:00'), " +
                    "(4, '2023-01-04 13:45:00'), " +
                    "(5, '2023-01-05 15:00:00'), " +
                    "(6, '2023-01-06 16:15:00'), " +
                    "(7, '2023-01-07 17:30:00'), " +
                    "(8, '2023-01-08 18:45:00'), " +
                    "(9, '2023-01-09 20:00:00'), " +
                    "(10, '2023-01-10 21:15:00');";
        }
    }

    @Step("Create pxf external table for {jdbcDbType}")
    private ExternalTable createExternalTable(JdbcDbType jdbcDbType) throws Exception {
        ExternalTable pxfExtTable = TableFactory.getPxfJdbcReadableTable(
                PXF_TABLE_NAME.replace("${db}", jdbcDbType.getServerName()),
                TABLE_FIELDS,
                SOURCE_TABLE_NAME,
                jdbcDbType.getServerName());
        pxfExtTable.addUserParameter("PARTITION_BY=datetime:TIMESTAMP");
        pxfExtTable.addUserParameter("RANGE=20240101T124910:20240101T125001");
        pxfExtTable.addUserParameter("INTERVAL=5:second");
        gpdb.createTableAndVerify(pxfExtTable);
        return pxfExtTable;
    }

    @Step("Check that partitioning logs are present")
    private void checkPxfLogs() throws Exception {
        int result = 0;
        for (Node pxfNode : pxfNodes) {
            cluster.copyFromRemoteMachine(pxfNode.getUserName(), pxfNode.getPassword(), pxfNode.getHost(), pxfLogFile, PXF_TEMP_LOG_PATH);
            result += Integer.parseInt(getCmdResult(cluster, SEGMENT_LOG_GREP_COMMAND));
            cluster.deleteFileFromNodes(PXF_TEMP_LOG_PATH, false);
        }
        assertEquals(2, result);
    }

    @Step("Clear source database and pxf")
    private void clearDbs(JdbcDbType jdbcDbType) throws Exception {
        switch (jdbcDbType) {
            case ORACLE:
                oracle.dropTable(sourceTable, false);
                break;
            case MYSQL:
                mysql.dropTable(sourceTable, false);
                break;
            default:
                gpdb.dropTable(sourceTable, false);
        }
        gpdb.dropTable(externalTable, false);
    }
}
