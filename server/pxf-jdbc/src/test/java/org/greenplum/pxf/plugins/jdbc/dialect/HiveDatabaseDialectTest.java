package org.greenplum.pxf.plugins.jdbc.dialect;

import org.apache.hadoop.hive.common.type.TimestampTZ;
import org.apache.hadoop.hive.common.type.TimestampTZUtil;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HiveDatabaseDialectTest {
    private static final String PRODUCT_NAME = "APACHE HIVE";
    private static final DatabaseDialect dialect = new HiveDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void wrapTimestampWithTZ() {
        List<String> timestampsTZ = List.of(
                "1985-05-11 15:10:00.12+03",
                "1985-05-12 15:11:00.123+05:30",
                "1985-05-13 15:12:00.1234+3",
                "1985-05-14 15:13:00-04:45",
                "1985-05-15 15:14:00-04",
                "1985-05-15 15:16:25.10+00"
        );
        List<String> expected = List.of(
                "TIMESTAMPLOCALTZ'1985-05-11 15:10:00.12+03:00'",
                "TIMESTAMPLOCALTZ'1985-05-12 15:11:00.123+05:30'",
                "TIMESTAMPLOCALTZ'1985-05-13 15:12:00.1234+03:00'",
                "TIMESTAMPLOCALTZ'1985-05-14 15:13:00-04:45'",
                "TIMESTAMPLOCALTZ'1985-05-15 15:14:00-04:00'",
                "TIMESTAMPLOCALTZ'1985-05-15 15:16:25.1+00:00'"
        );
        for (int i = 0; i < timestampsTZ.size(); i++) {
            assertEquals(expected.get(i), dialect.wrapTimestampWithTZ(timestampsTZ.get(i)));
        }
    }

    @Test
    void getDateValue() throws SQLException {
        String date = "1977-12-11";
        String colName = "dt";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getDate(colName)).thenReturn(Date.valueOf(date));
        assertEquals(date, dialect.getDateValue(resultSet, colName, true));
        assertEquals(date, dialect.getDateValue(resultSet, colName, false));
    }

    @Test
    void getTimestampValue() throws SQLException {
        String date = "1977-12-11 13:12:00";
        String colName = "tstz";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getTimestamp(colName)).thenReturn(Timestamp.valueOf(date));
        assertEquals(date, dialect.getTimestampValue(resultSet, colName, true));
        assertEquals(date, dialect.getTimestampValue(resultSet, colName, false));
    }

    @Test
    void getTimestampTZValue() throws SQLException {
        String value = "1977-12-11 13:12:00.123+03:00";
        String expected = "1977-12-11 13:12:00.123+03:00 AD";
        TimestampTZ tstz = TimestampTZUtil.parse(value);
        String colName = "ts";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(colName)).thenReturn(tstz);
        assertEquals(expected, dialect.getTimestampTZValue(resultSet, colName));
    }
}