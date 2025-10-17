package org.greenplum.pxf.automation.features.hive;

import annotations.WorksWithFDW;
import io.qameta.allure.Feature;
import io.qameta.allure.Step;
import jsystem.framework.system.SystemManagerImpl;
import org.greenplum.pxf.automation.components.cluster.MultiNodeCluster;
import org.greenplum.pxf.automation.components.cluster.PhdCluster;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.Node;
import org.greenplum.pxf.automation.components.cluster.installer.nodes.SegmentNode;
import org.greenplum.pxf.automation.components.hive.Hive;
import org.greenplum.pxf.automation.features.hive.HiveBaseTest;
import org.greenplum.pxf.automation.structures.tables.hive.HiveTable;
import org.greenplum.pxf.automation.structures.tables.pxf.ReadableExternalTable;
import org.greenplum.pxf.automation.structures.tables.utils.TableFactory;
import org.testng.annotations.Test;

import java.util.List;

import static org.greenplum.pxf.automation.PxfTestConstant.PXF_LOG_RELATIVE_PATH;
import static org.greenplum.pxf.automation.PxfTestUtil.getCmdResult;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@WorksWithFDW
@Feature("Reading HDFS files from Hive Metastore")
public class HiveMetastoreHdfsReadTest extends HiveBaseTest {
    private static final String[] HIVE_TABLE_FIELDS = {
            "location   STRING",
            "month STRING",
            "number_of_orders   INT",
            "total_sales   DOUBLE"
    };
    private static final String[] EXT_TABLE_FIELDS = {
            "location   TEXT",
            "month TEXT",
            "number_of_orders   INT",
            "total_sales   FLOAT8"
    };
    private static final String HIVE_DATAFILE = "pxf_hive_datafile.txt";
    private static final String SELECT_QUERY = "SELECT * FROM ${pxf_read_table}";
    private static final String SOURCE_TEXT_TABLE_NAME = "sales_info";
    private static final String SOURCE_PARQUET_TABLE_NAME = "hive_parquet_table";
    private static final String PXF_TABLE_NAME = SOURCE_PARQUET_TABLE_NAME + "_ext";
    private static final String PXF_TEMP_LOG_PATH = "/tmp/pxf";
    private static final String PXF_TEMP_LOG_FILE = PXF_TEMP_LOG_PATH + "/pxf-service.log";
    private static final String SQL_COUNT_QUERY = "SELECT count(*) FROM ${table}";
    private List<Node> pxfNodes;
    private String pxfLogFile;
    private Hive hive;
    private HiveTable sourceTextTable;
    private HiveTable sourceParquetTable;
    private ReadableExternalTable pxfExternalTable;

    @Override
    protected void beforeClass() throws Exception {
        pxfNodes = ((MultiNodeCluster) cluster).getNode(SegmentNode.class, PhdCluster.EnumClusterServices.pxf);
        String pxfHome = cluster.getPxfHome();
        pxfLogFile = pxfHome + "/" + PXF_LOG_RELATIVE_PATH;
        String restartCommand = pxfHome + "/bin/pxf restart";
        cluster.runCommandOnNodes(pxfNodes, String.format("export PXF_LOG_LEVEL=%s;%s", "debug", restartCommand));
        cluster.runCommand("mkdir -p " + PXF_TEMP_LOG_PATH);
    }

    @Override
    protected void beforeMethod() throws Exception {
        cleanLogs();
    }

    @Override
    protected void afterMethod() throws Exception {
        clearDbs();
    }


    @Test(groups = { "hive", "hcatalog", "features", "gpdb" }, description = "Check PXF support for reading HDFS files from Hive metastore")
    public void testPxfReadHDFSFilesHiveMetastore() throws Exception {
        hive = (Hive) SystemManagerImpl.getInstance().getSystemObject("hive");

        sourceTextTable = TableFactory.getHiveByRowCommaTable(SOURCE_TEXT_TABLE_NAME, HIVE_TABLE_FIELDS);
        sourceTextTable.setStoredAs("textfile");
        hive.createTableAndVerify(sourceTextTable);
        loadDataIntoHive(hdfs, hive, HIVE_DATAFILE, sourceTextTable);
        hive.runQuery(SELECT_QUERY.replace("${pxf_read_table}", "default." + SOURCE_TEXT_TABLE_NAME));

        sourceParquetTable = TableFactory.getHiveByRowCommaTable(SOURCE_PARQUET_TABLE_NAME, HIVE_TABLE_FIELDS);
        sourceParquetTable.setStoredAs("parquet");
        hive.createTableAndVerify(sourceParquetTable);
        hive.insertData(sourceTextTable, sourceParquetTable);

        createExternalTable();
        gpdb.createTableAndVerify(pxfExternalTable);

        assertEquals(hive.getIntValueFromQuery(SQL_COUNT_QUERY.replace("${table}", SOURCE_PARQUET_TABLE_NAME)),
                gpdb.getIntValueFromQuery(SQL_COUNT_QUERY.replace("${table}", PXF_TABLE_NAME)));

        gpdb.runQuery(SELECT_QUERY.replace("${pxf_read_table}", PXF_TABLE_NAME));
        checkPxfLogs();
    }

    @Step("Create pxf external table")
    private void createExternalTable() {
       pxfExternalTable = TableFactory.getPxfReadableCustomTable(
                PXF_TABLE_NAME,
                EXT_TABLE_FIELDS,
                "default."+ SOURCE_PARQUET_TABLE_NAME,
                "");
        pxfExternalTable.setProfile("hive:parquet_custom");
        pxfExternalTable.setServer("server=default");
    }

    @Step("Check that partitioning logs are present")
    private void checkPxfLogs() throws Exception {
        int fragmentLogCount = 0;
        int accessorLogCount = 0;
        for (Node pxfNode : pxfNodes) {
            cluster.copyFromRemoteMachine(pxfNode.getUserName(), pxfNode.getPassword(), pxfNode.getHost(), pxfLogFile, PXF_TEMP_LOG_PATH);
            cluster.runCommand("cp " + PXF_TEMP_LOG_FILE + " " + PXF_TEMP_LOG_FILE + "-" + getMethodName() + "-" + pxfNode.getHost());
            fragmentLogCount += Integer.parseInt(getCmdResult(cluster, grepLog("Returning 1/1 fragment")));
            accessorLogCount += Integer.parseInt(getCmdResult(cluster, grepLog("Creating accessor 'org.greenplum.pxf.plugins.hdfs.ParquetFileAccessor' " +
                    "and resolver 'org.greenplum.pxf.plugins.hdfs.ParquetResolver")));
            cluster.deleteFileFromNodes(PXF_TEMP_LOG_FILE, false);
        }
        assertTrue("Check that log is present at least once on one of segment hosts", fragmentLogCount > 0);
        assertTrue("Check that log is present at least once on one of segment hosts", accessorLogCount > 0);
    }

    @Step("Grep verifying log")
    private String grepLog(String searchedLog) {
        return "cat " + PXF_TEMP_LOG_FILE + " | grep \"" + searchedLog + "\" | wc -l";
    }

    @Step("Cleaning logs before test run")
    private void cleanLogs() throws Exception {
        cluster.runCommandOnNodes(pxfNodes, "> " + pxfLogFile);
    }

    private String getMethodName() {
        return Thread.currentThread()
                .getStackTrace()[2]
                .getMethodName();
    }

    @Step("Cleaning tables created for test")
    private void clearDbs() throws Exception {
        hive.dropTable(sourceTextTable, false);
        hive.dropTable(sourceParquetTable, false);
        gpdb.dropTable(pxfExternalTable, false);
    }
}
