package org.greenplum.pxf.automation.features.parquet;

import annotations.WorksWithFDW;
import io.qameta.allure.Feature;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.schema.PrimitiveType;
import org.apache.parquet.schema.Type;
import org.greenplum.pxf.automation.features.BaseFeature;
import org.greenplum.pxf.automation.structures.tables.utils.TableFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.apache.parquet.schema.LogicalTypeAnnotation;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.hadoop.metadata.ParquetMetadata;
import org.apache.parquet.hadoop.ParquetFileReader;

import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT64;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.INT96;
import static org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY;

import java.util.List;

import static org.greenplum.pxf.automation.PxfTestConstant.PXF_DEFAULT_PROFILE_NAME;
import static org.greenplum.pxf.automation.PxfTestConstant.PXF_JDBC_SITE_CONF_FILE_PATH_TEMPLATE;
import static org.greenplum.pxf.automation.components.cluster.PhdCluster.EnumClusterServices.pxf;
import static org.junit.Assert.*;

@WorksWithFDW
@Feature("PXF Parquet timezone parameters")
public class PxfParquetTimezoneParametersTest extends BaseFeature {
    private static final String PARQUET_FORMAT = "parquet";
    private static final String PXF_HDFS_PARQUET_PROFILE = "hdfs:parquet";
    private static final String WRITABLE_EXTERNAL_TABLE_NAME = "writable_parquet_table";
    private static final String READABLE_EXTERNAL_TABLE_NAME = "readable_parquet_table";
    private static final String USE_INT64_TIMESTAMPS_PARAM = "USE_INT64_TIMESTAMPS=";
    private static final String USE_LOCAL_PXF_TIMEZONE_WRITE_PARAM = "USE_LOCAL_PXF_TIMEZONE_WRITE=";
    private static final String USE_LOCAL_PXF_TIMEZONE_READ_PARAM = "USE_LOCAL_PXF_TIMEZONE_READ=";
    private static final String USE_LOGICAL_TYPE_INTERVAL = "USE_LOGICAL_TYPE_INTERVAL=";
    private static final String USE_LOGICAL_TYPE_TIME = "USE_LOGICAL_TYPE_TIME=";
    private static final String USE_LOGICAL_TYPE_UUID = "USE_LOGICAL_TYPE_UUID=";
    private static final String PXF_ENV_FILE_RELATIVE_PATH = "conf/pxf-env.sh";
    private static final String TIMESTAMP = "('2022-06-22 19:10:25')";
    private static final String TIMESTAMP_WITH_TIMEZONE = "('2022-06-22 19:10:25.123456+04')";
    private static final String TIME_VALUE = "('18:15:30.123456')";
    private static final String UUID_VALUE = "('4e8d9b02-6d6a-4b49-9537-8f4b0d750f0e')";
    private static final String INTERVAL_VALUE = "('3 days 4 hours 15 minutes 30 seconds')";
    private static final String[] TIMESTAMP_TABLE_SCHEMA = new String[]{"tmp TIMESTAMP"};
    private static final String[] TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE = new String[]{"tmp TIMESTAMP WITH TIME ZONE"};
    private static final String[] TIME_TABLE_SCHEMA = new String[]{"tmp TIME"};
    private static final String[] UUID_TABLE_SCHEMA = new String[]{"tmp UUID"};
    private static final String[] INTERVAL_TABLE_SCHEMA = new String[]{"tmp INTERVAL"};
    private static final String[] TIMESTAMP_TYPES_TABLE_SCHEMA_WITH_TIMEZONE = new String[]{"ts_millis TIMESTAMP", "ts_micros TIMESTAMP", "ts_nanos TIMESTAMP"};
    private static final String TIMESTAMP_PARQUET_FILE_NAME = "tmp_no_timezone.parquet";
    private static final String TIMESTAMP_WITH_TIMEZONE_PARQUET_FILE_NAME = "tmp_with_timezone.parquet";
    private static final String PXF_PARQUET_SERVER_PROFILE = "parquet_config";
    private String hdfsPath;
    private String pxfEnvFile;

    @Override
    protected void beforeClass() throws Exception {
        hdfsPath = hdfs.getWorkingDirectory() + "/parquet/";
        pxfEnvFile = cluster.getPxfHome() + "/" + PXF_ENV_FILE_RELATIVE_PATH;
        String command = "sed -i -E '/PXF_JVM_OPTS=/{ /-Duser\\.timezone=/! s#\"$# -Duser.timezone=Europe/Moscow\"#; }' " + pxfEnvFile;
        cluster.runCommandOnAllNodes(command);
        cluster.restart(pxf);
        // Prepare config
        String pxfHome = cluster.getPxfHome();
        String parquetProfilePath = String.format(PXF_JDBC_SITE_CONF_FILE_PATH_TEMPLATE, pxfHome, PXF_PARQUET_SERVER_PROFILE);
        String defaultProfilePath = String.format(PXF_JDBC_SITE_CONF_FILE_PATH_TEMPLATE, pxfHome, PXF_DEFAULT_PROFILE_NAME);
        cluster.runCommandOnAllNodes("cp -r " + defaultProfilePath + "/* " + parquetProfilePath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "parquetTypeParametersForExtTable")
    public void testParquetParameterFromExtTable(
            int index,
            String[] tableSchema,
            String value,
            String parameter,
            PrimitiveType.PrimitiveTypeName primitiveTypeName,
            Class<?> logicalTypeAnnotation
    ) throws Exception {
        String hdfsPath = hdfs.getWorkingDirectory() + "/parquet_option_" + index + "/";
        exTable = TableFactory.getPxfHcfsWritableTable(WRITABLE_EXTERNAL_TABLE_NAME, tableSchema, hdfsPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{parameter});
        createTable(exTable);
        gpdb.insertData(value, exTable);
        assertParquetFile(primitiveTypeName, logicalTypeAnnotation, hdfsPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "parquetTypeParametersForConfig")
    public void testParquetParameterFromConfig(
            int index,
            String[] tableSchema,
            String value,
            PrimitiveType.PrimitiveTypeName primitiveTypeName,
            Class<?> logicalTypeAnnotation
    ) throws Exception {
        String hdfsPath = hdfs.getWorkingDirectory() + "/parquet_config_" + index + "/";
        exTable = TableFactory.getPxfWritableCustomTable(WRITABLE_EXTERNAL_TABLE_NAME, tableSchema, hdfsPath);
        exTable.setProfile(PXF_HDFS_PARQUET_PROFILE);
        exTable.setServer("SERVER=" + PXF_PARQUET_SERVER_PROFILE);
        createTable(exTable);
        gpdb.insertData(value, exTable);
        assertParquetFile(primitiveTypeName, logicalTypeAnnotation, hdfsPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "useLocalPxfTimezoneWriteProviderForExtTable")
    public void testUseLocalPxfTimezoneWriteFromExtTable(
            String[] tableSchema,
            String timestampValue,
            boolean useLocalPxfTimezoneWriteIsEnabled,
            String sqlPath
    ) throws Exception {
        String fullTestPath = hdfsPath + "use_local_pxf_timezone_write_option";
        String useLocalPxfTimezoneWrite = USE_LOCAL_PXF_TIMEZONE_WRITE_PARAM + useLocalPxfTimezoneWriteIsEnabled;
        exTable = TableFactory.getPxfHcfsWritableTable(WRITABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneWrite});
        createTable(exTable);
        gpdb.insertData(timestampValue, exTable);
        String useLocalPxfTimezoneRead = USE_LOCAL_PXF_TIMEZONE_READ_PARAM + false;
        exTable = TableFactory.getPxfHcfsReadableTable(READABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneRead});
        createTable(exTable);
        runSqlTest(sqlPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "useLocalPxfTimezoneWriteProviderForConfig")
    public void testUseLocalPxfTimezoneWriteFromConfig(
            String[] tableSchema,
            String timestampValue,
            String sqlPath
    ) throws Exception {
        String fullTestPath = hdfsPath + "use_local_pxf_timezone_write_config";
        exTable = TableFactory.getPxfWritableCustomTable(WRITABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath);
        exTable.setProfile(PXF_HDFS_PARQUET_PROFILE);
        exTable.setServer("SERVER=" + PXF_PARQUET_SERVER_PROFILE);
        createTable(exTable);
        gpdb.insertData(timestampValue, exTable);
        String useLocalPxfTimezoneRead = USE_LOCAL_PXF_TIMEZONE_READ_PARAM + false;
        exTable = TableFactory.getPxfHcfsReadableTable(READABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneRead});
        createTable(exTable);
        runSqlTest(sqlPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "useLocalPxfTimezoneReadProviderForExtTable")
    public void testUseLocalPxfTimezoneReadFromExtTable(
            String[] tableSchema,
            String resourceFile,
            boolean useLocalPxfTimezoneReadIsEnabled,
            String sqlPath
    ) throws Exception {
        String resourcePath = localDataResourcesFolder + "/parquet/";
        hdfs.copyFromLocal(resourcePath + resourceFile, hdfsPath);
        String useLocalPxfTimezoneRead = USE_LOCAL_PXF_TIMEZONE_READ_PARAM + useLocalPxfTimezoneReadIsEnabled;
        exTable = TableFactory.getPxfHcfsReadableTable(READABLE_EXTERNAL_TABLE_NAME, tableSchema, hdfsPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneRead});
        createTable(exTable);
        runSqlTest(sqlPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "useLocalPxfTimezoneReadProviderForConfig")
    public void testUseLocalPxfTimezoneReadFromConfig(
            String[] tableSchema,
            String resourceFile,
            String sqlPath
    ) throws Exception {
        String resourcePath = localDataResourcesFolder + "/parquet/";
        hdfs.copyFromLocal(resourcePath + resourceFile, hdfsPath);
        exTable = TableFactory.getPxfReadableCustomTable(READABLE_EXTERNAL_TABLE_NAME, tableSchema, hdfsPath, PARQUET_FORMAT);
        exTable.setServer("SERVER=" + PXF_PARQUET_SERVER_PROFILE);
        createTable(exTable);
        runSqlTest(sqlPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"}, dataProvider = "parquetTimestampWithSessionTimeZone")
    public void testParquetTimestampWithSessionTimeZone(
            int index,
            String[] tableSchema,
            String timestampValue,
            boolean isUseInt64,
            String sqlPath
    ) throws Exception {
        String fullTestPath = hdfsPath + "parquet_timestamp_with_timezone_"  + index + "/";
        exTable = TableFactory.getPxfHcfsWritableTable(WRITABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{USE_INT64_TIMESTAMPS_PARAM + isUseInt64, USE_LOCAL_PXF_TIMEZONE_WRITE_PARAM + false});
        createTable(exTable);
        gpdb.insertData(timestampValue, exTable);
        String useLocalPxfTimezoneRead = USE_LOCAL_PXF_TIMEZONE_READ_PARAM + false;
        exTable = TableFactory.getPxfHcfsReadableTable(READABLE_EXTERNAL_TABLE_NAME, tableSchema, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneRead});
        createTable(exTable);
        runSqlTest(sqlPath);
    }

    @Test(groups = {"features", "gpdb", "hcfs"})
    public void testParquetTimestampTypes() throws Exception {
        String fullTestPath = hdfsPath + "parquet_timestamp_types/";
        // The file timestamps_type.parquet contains: {"ts_millis":1748736000123,"ts_micros":1748736000123456,"ts_nanos":1748736000123456789}
        String resourcePath = localDataResourcesFolder + "/parquet/timestamps_type.parquet";
        hdfs.copyFromLocal(resourcePath, fullTestPath);
        String useLocalPxfTimezoneRead = USE_LOCAL_PXF_TIMEZONE_READ_PARAM + false;
        exTable = TableFactory.getPxfHcfsReadableTable(READABLE_EXTERNAL_TABLE_NAME, TIMESTAMP_TYPES_TABLE_SCHEMA_WITH_TIMEZONE, fullTestPath, hdfs.getBasePath(), PARQUET_FORMAT);
        exTable.setUserParameters(new String[]{useLocalPxfTimezoneRead});
        createTable(exTable);
        runSqlTest("features/parquet/timestamp-types");
    }

    private void assertParquetFile(
            PrimitiveType.PrimitiveTypeName primitiveTypeName,
            Class<?> logicalTypeAnnotation,
            String hdfsPath
    ) throws Exception {
        Path parquetPath = new Path(hdfs.list(hdfsPath).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No parquet file is found in this path")));
        try (ParquetFileReader reader = ParquetFileReader.open(HadoopInputFile.fromPath(parquetPath, hdfs.getConfiguration()))) {
            ParquetMetadata parquetMetadata = reader.getFooter();
            MessageType parquetData = parquetMetadata.getFileMetaData().getSchema();
            List<Type> parquetSchema = parquetData.getFields();
            assertEquals(1, parquetSchema.size());
            PrimitiveType type = (PrimitiveType) parquetSchema.get(0);
            assertEquals("tmp", type.getName());
            assertTrue(type.isPrimitive());
            assertEquals(primitiveTypeName, type.getPrimitiveTypeName());
            if (logicalTypeAnnotation != null) {
                assertEquals(logicalTypeAnnotation, type.getLogicalTypeAnnotation().getClass());
            } else {
                assertNull(type.getLogicalTypeAnnotation());
            }
        }
    }

    @Override
    protected void afterMethod() throws Exception {
        super.afterMethod();
        hdfs.removeDirectory(hdfsPath);
    }

    @AfterClass
    protected void afterClass() throws Exception {
        super.afterClass();
        String command = "sed -i -E '/PXF_JVM_OPTS=/s#[[:space:]]+-Duser\\.timezone=Europe/Moscow##' " + pxfEnvFile;
        cluster.runCommandOnAllNodes(command);
        cluster.restart(pxf);
    }

    @DataProvider
    private Object[][] parquetTypeParametersForExtTable() {
        return new Object[][]{
                {1, TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, USE_INT64_TIMESTAMPS_PARAM + true, INT64, LogicalTypeAnnotation.TimestampLogicalTypeAnnotation.class},
                {2, TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, USE_INT64_TIMESTAMPS_PARAM + true, INT64, LogicalTypeAnnotation.TimestampLogicalTypeAnnotation.class},
                {3, TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, USE_INT64_TIMESTAMPS_PARAM + false, INT96, null},
                {4, TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, USE_INT64_TIMESTAMPS_PARAM + false, INT96, null},
                {5, INTERVAL_TABLE_SCHEMA, INTERVAL_VALUE, USE_LOGICAL_TYPE_INTERVAL + true, FIXED_LEN_BYTE_ARRAY, LogicalTypeAnnotation.IntervalLogicalTypeAnnotation.class},
                {6, INTERVAL_TABLE_SCHEMA, INTERVAL_VALUE, USE_LOGICAL_TYPE_INTERVAL + false, FIXED_LEN_BYTE_ARRAY, null},
                {7, TIME_TABLE_SCHEMA, TIME_VALUE, USE_LOGICAL_TYPE_TIME + true, INT64, LogicalTypeAnnotation.TimeLogicalTypeAnnotation.class},
                {8, TIME_TABLE_SCHEMA, TIME_VALUE, USE_LOGICAL_TYPE_TIME + false, INT64, null},
                {9, UUID_TABLE_SCHEMA, UUID_VALUE, USE_LOGICAL_TYPE_UUID + true, FIXED_LEN_BYTE_ARRAY, LogicalTypeAnnotation.UUIDLogicalTypeAnnotation.class},
                {10, UUID_TABLE_SCHEMA, UUID_VALUE, USE_LOGICAL_TYPE_UUID + false, FIXED_LEN_BYTE_ARRAY, null},
        };
    }

    // According to the config from "automation/env/conf/parquet/pxf-site.xml"
    @DataProvider
    private Object[][] parquetTypeParametersForConfig() {
        return new Object[][]{
                {1, TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, INT64, LogicalTypeAnnotation.TimestampLogicalTypeAnnotation.class},
                {2, TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, INT64, LogicalTypeAnnotation.TimestampLogicalTypeAnnotation.class},
                {3, INTERVAL_TABLE_SCHEMA, INTERVAL_VALUE, FIXED_LEN_BYTE_ARRAY, LogicalTypeAnnotation.IntervalLogicalTypeAnnotation.class},
                {4, TIME_TABLE_SCHEMA, TIME_VALUE, INT64, LogicalTypeAnnotation.TimeLogicalTypeAnnotation.class},
                {5, UUID_TABLE_SCHEMA, UUID_VALUE, FIXED_LEN_BYTE_ARRAY, LogicalTypeAnnotation.UUIDLogicalTypeAnnotation.class},
        };
    }

    @DataProvider
    private Object[][] useLocalPxfTimezoneWriteProviderForExtTable() {
        return new Object[][]{
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, true, "features/parquet/parameters/check-writable-tables/timestamp/true"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, true, "features/parquet/parameters/check-writable-tables/timestamp-with-timezone"},
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, false, "features/parquet/parameters/check-writable-tables/timestamp/false"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, false, "features/parquet/parameters/check-writable-tables/timestamp-with-timezone"},
        };
    }

    // According to the config from "automation/env/conf/parquet/pxf-site.xml"
    @DataProvider
    private Object[][] useLocalPxfTimezoneWriteProviderForConfig() {
        return new Object[][]{
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, "features/parquet/parameters/check-writable-tables/timestamp/false"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, "features/parquet/parameters/check-writable-tables/timestamp-with-timezone"},
        };
    }

    @DataProvider
    private Object[][] useLocalPxfTimezoneReadProviderForExtTable() {
        return new Object[][]{
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP_PARQUET_FILE_NAME, true, "features/parquet/parameters/check-readable-tables/timestamp/true"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE_PARQUET_FILE_NAME, true, "features/parquet/parameters/check-readable-tables/timestamp-with-timezone/true"},
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP_PARQUET_FILE_NAME, false, "features/parquet/parameters/check-readable-tables/timestamp/false"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE_PARQUET_FILE_NAME, false, "features/parquet/parameters/check-readable-tables/timestamp-with-timezone/false"}
        };
    }

    // According to the config from "automation/env/conf/parquet/pxf-site.xml"
    @DataProvider
    private Object[][] useLocalPxfTimezoneReadProviderForConfig() {
        return new Object[][]{
                {TIMESTAMP_TABLE_SCHEMA, TIMESTAMP_PARQUET_FILE_NAME, "features/parquet/parameters/check-readable-tables/timestamp/false"},
                {TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE_PARQUET_FILE_NAME, "features/parquet/parameters/check-readable-tables/timestamp-with-timezone/false"}
        };
    }

    @DataProvider
    private Object[][] parquetTimestampWithSessionTimeZone() {
        return new Object[][]{
                {1, TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, true, "features/parquet/parameters/check-session-timezone/timestamp/int64"},
                {2, TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, true, "features/parquet/parameters/check-session-timezone/timestamp-with-timezone/int64"},
                {3, TIMESTAMP_TABLE_SCHEMA, TIMESTAMP, false, "features/parquet/parameters/check-session-timezone/timestamp/int96"},
                {4, TIMESTAMP_TABLE_SCHEMA_WITH_TIMEZONE, TIMESTAMP_WITH_TIMEZONE, false, "features/parquet/parameters/check-session-timezone/timestamp-with-timezone/int96"},
        };
    }
}
