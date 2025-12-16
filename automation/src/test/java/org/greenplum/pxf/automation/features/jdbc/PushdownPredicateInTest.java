package org.greenplum.pxf.automation.features.jdbc;

import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import jsystem.framework.system.SystemManagerImpl;
import org.greenplum.pxf.automation.PxfTestUtil;
import org.greenplum.pxf.automation.components.cluster.MultiNodeCluster;
import org.greenplum.pxf.automation.components.cluster.PhdCluster;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.Node;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.SegmentNode;
import org.greenplum.pxf.automation.components.oracle.Oracle;
import org.greenplum.pxf.automation.features.BaseFeature;
import org.greenplum.pxf.automation.structures.tables.basic.Table;
import org.greenplum.pxf.automation.structures.tables.pxf.ExternalTable;
import org.greenplum.pxf.automation.structures.tables.utils.TableFactory;
import org.greenplum.pxf.automation.utils.system.FDWUtils;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;

import static org.greenplum.pxf.automation.PxfTestConstant.*;
import static org.greenplum.pxf.automation.PxfTestUtil.getCmdResult;
import static org.junit.Assert.assertEquals;

@Feature("Pushdown predicate IN")
public class PushdownPredicateInTest extends BaseFeature {
    private static final String SOURCE_TABLE_NAME = "predicate_in_source_table";
    private static final String SOURCE_TABLE_SCHEMA = "system";
    private static final String PXF_ORACLE_SERVER_PROFILE = "oracle-predicate";
    private static final String PXF_JDBC_SITE_CONF_TEMPLATE_RELATIVE_PATH = "templates/oracle/jdbc-site.xml";
    private static final String PXF_TEMP_LOG_PATH = "/tmp/pxf-service-pushdown.log";
    private static final String[] POSTGRES_SOURCE_TABLE_FIELDS = new String[]{
            "id    int",
            "descr   text"};
    private static final String[] ORACLE_SOURCE_TABLE_FIELDS = new String[]{
            "id    NUMBER",
            "descr   VARCHAR2(100)"};

    private static final String ORACLE_INSERT_QUERY = "INSERT ALL\n" +
            "INTO system.predicate_in_source_table VALUES (1, 'text1')\n" +
            "INTO system.predicate_in_source_table VALUES (2, 'text2')\n" +
            "INTO system.predicate_in_source_table VALUES (3, 'text3')\n" +
            "INTO system.predicate_in_source_table VALUES (4, 'text4')\n" +
            "INTO system.predicate_in_source_table VALUES (5, 'text5')\n" +
            "SELECT 1 FROM DUAL";
    private static final String GET_MASTER_LOG_DIRECTORY_COMMAND = "source $GPHOME/greengage_path.sh;psql -d %s -Atc 'show log_directory'";
    private static final String GET_LATEST_MASTER_LOG_COMMAND = "$(stat -c '%%Y %%n' %s/gpdb-*.csv | sort -k1,1nr | head -1 | awk '{ print $2 }')";
    private static final String POSTGRES_SEGMENT_LOG_GREP_COMMAND = "cat " + PXF_TEMP_LOG_PATH + " | grep ' FILTER target:  WHERE id IN (2,3)' | wc -l";
    private static final String GET_STATS_QUERY = "SELECT count(*) FROM v$sqlstats " +
            "WHERE SQL_FULLTEXT LIKE 'SELECT id, descr FROM " + SOURCE_TABLE_SCHEMA + "." + SOURCE_TABLE_NAME + " WHERE id IN (3,4,5)'";
    private Oracle oracle;
    private List<Node> pxfNodes;
    private String pxfJdbcSiteConfFile;
    private String pxfLogFile;
    private Table gpdbPredicateInSourceTable;
    private Table oraclePredicateInSourceTable;
    private String restartCommand;
    private String latestMasterLog;

    @Override
    protected void beforeClass() throws Exception {
        if (!FDWUtils.useFDW) {
            latestMasterLog = String.format(GET_LATEST_MASTER_LOG_COMMAND, getMasterLogDirectory());
            String pxfHome = cluster.getPxfHome();
            restartCommand = pxfHome + "/bin/pxf restart";
            String pxfJdbcSiteConfPath = String.format(PXF_JDBC_SITE_CONF_FILE_PATH_TEMPLATE, pxfHome, PXF_ORACLE_SERVER_PROFILE);
            pxfJdbcSiteConfFile = pxfJdbcSiteConfPath + "/" + PXF_JDBC_SITE_CONF_FILE_NAME;
            String pxfJdbcSiteConfTemplate = pxfHome + "/" + PXF_JDBC_SITE_CONF_TEMPLATE_RELATIVE_PATH;
            cluster.copyFileToNodes(pxfJdbcSiteConfTemplate, pxfJdbcSiteConfPath, true, false);
            if (cluster instanceof MultiNodeCluster) {
                pxfNodes = ((MultiNodeCluster) cluster).getNode(SegmentNode.class, PhdCluster.EnumClusterServices.pxf);
            }
            pxfLogFile = pxfHome + "/" + PXF_LOG_RELATIVE_PATH;
            oracle = (Oracle) SystemManagerImpl.getInstance().getSystemObject("oracle");
            prepareData();
            changeLogLevel("debug");
        }
    }

    @Override
    public void afterClass() throws Exception {
        if (!FDWUtils.useFDW) {
            changeLogLevel("info");
        }
    }

    @Step("Prepare data")
    protected void prepareData() throws Exception {
        preparePostgresPredicateInSourceTable();
        prepareOraclePredicateInSourceTable();
        createGpdbReadablePgTable();
        createGpdbReadableOracleTable();
    }

    @Step("Prepare Postgres source table")
    private void preparePostgresPredicateInSourceTable() throws Exception {
        gpdbPredicateInSourceTable = new Table(SOURCE_TABLE_NAME, POSTGRES_SOURCE_TABLE_FIELDS);
        gpdbPredicateInSourceTable.setDistributionFields(new String[]{"id"});
        gpdb.createTableAndVerify(gpdbPredicateInSourceTable);
        String[][] rows = new String[][]{
                {"1", "text1"},
                {"2", "text2"},
                {"3", "text3"},
                {"4", "text4"},
                {"5", "text5"}};
        Table dataTable = new Table("dataTable", POSTGRES_SOURCE_TABLE_FIELDS);
        dataTable.addRows(rows);
        gpdb.insertData(dataTable, gpdbPredicateInSourceTable);
    }

    @Step("Prepare Oracle source table")
    private void prepareOraclePredicateInSourceTable() throws Exception {
        oraclePredicateInSourceTable = new Table(SOURCE_TABLE_NAME, ORACLE_SOURCE_TABLE_FIELDS);
        oraclePredicateInSourceTable.setSchema("system");
        oracle.createTableAndVerify(oraclePredicateInSourceTable);
        oracle.runQuery(ORACLE_INSERT_QUERY);
    }

    @Step("Create GPDB readable PG table")
    private void createGpdbReadablePgTable() throws Exception {
        ExternalTable gpdbReadablePgTable = TableFactory.getPxfJdbcReadableTable(
                "predicate_in_pg_ext_table",
                POSTGRES_SOURCE_TABLE_FIELDS,
                gpdbPredicateInSourceTable.getName(),
                "default");
        gpdb.createTableAndVerify(gpdbReadablePgTable);
    }

    @Step("Create GPDB readable Oracle table")
    private void createGpdbReadableOracleTable() throws Exception {
        ExternalTable gpdbReadableOracleTable = TableFactory.getPxfJdbcReadableTable(
                "predicate_in_oracle_ext_table",
                POSTGRES_SOURCE_TABLE_FIELDS,
                oraclePredicateInSourceTable.getSchema() + "." + oraclePredicateInSourceTable.getName(),
                PXF_ORACLE_SERVER_PROFILE);
        gpdb.createTableAndVerify(gpdbReadableOracleTable);
    }

    @Test(groups = {"features", "jdbc"}, description = "Check pushdown predicate 'IN' for Postgres")
    public void testPredicateInPostgres() throws Exception {
        cleanLogs();
        runSqlTest("features/jdbc/predicate-in/postgres");
        String result = getCmdResult(cluster,
                "cat " + latestMasterLog + " | grep -v snapshot | grep -e 'SELECT id, descr FROM " + SOURCE_TABLE_NAME + " WHERE id IN (2,3)' | wc -l");
        assertEquals("1", result);
    }

    @Test(groups = {"features", "jdbc"}, description = "Check pushdown predicate 'IN' logging")
    public void testPredicateInLogging() throws Exception {
        cleanLogs();
        runSqlTest("features/jdbc/predicate-in/postgres");
        int result = 0;
        for (Node pxfNode : pxfNodes) {
            cluster.copyFromRemoteMachine(pxfNode.getUserName(), pxfNode.getPassword(), pxfNode.getHost(), pxfLogFile, PXF_TEMP_LOG_PATH);
            result += Integer.parseInt(getCmdResult(cluster, POSTGRES_SEGMENT_LOG_GREP_COMMAND));
            cluster.deleteFileFromNodes(PXF_TEMP_LOG_PATH, false);
        }
        assertEquals("Check that filter is present in the log at least on the one segment host", 1, result);
    }

    @Test(groups = {"features", "jdbc"}, description = "Check pushdown predicate 'IN' for Oracle")
    public void testPredicateInOracle() throws Exception {
        runSqlTest("features/jdbc/predicate-in/oracle");
        Assert.assertEquals(1, oracle.getIntValueFromQuery(GET_STATS_QUERY));
        cluster.deleteFileFromNodes(pxfJdbcSiteConfFile, false);
    }

    private void changeLogLevel(String level) throws Exception {
        cluster.runCommandOnNodes(pxfNodes, String.format("export PXF_LOG_LEVEL=%s;%s", level, restartCommand));
    }

    private void cleanLogs() throws Exception {
        cluster.runCommand("> " + latestMasterLog);
        cluster.runCommandOnNodes(pxfNodes, "> " + pxfLogFile);
    }

    private String getMasterLogDirectory() throws Exception {
        String command = String.format(GET_MASTER_LOG_DIRECTORY_COMMAND, gpdb.getDb());
        PxfTestUtil.getCmdResult(cluster, command);
        return "$MASTER_DATA_DIRECTORY/" + PxfTestUtil.getCmdResult(cluster, command);
    }
}
