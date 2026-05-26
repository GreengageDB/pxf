package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultDatabaseDialectTest {
    private static final String PRODUCT_NAME = "OTHER";
    private static final DatabaseDialect dialect = new DefaultDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void testWrapDate() {
        String date = "2004-03-17";
        String expected = "'2004-03-17'";
        assertEquals(expected, dialect.wrapDate(date));
    }

    @Test
    void testWrapDateWideRangeFalse() {
        String date = "2004-03-17";
        String expected = "'2004-03-17'";
        LocalDate ld = LocalDate.parse(date);
        assertEquals(expected, dialect.wrapDate(ld, false));
    }

    @Test
    void testWrapDateWideRangeTrue() {
        List<String> dates = List.of(
                "+123456-05-01",
                "2004-03-17"
        );
        List<String> expected = List.of(
                "'+123456-05-01'",
                "'2004-03-17'"
        );
        for (int i = 0; i < dates.size(); i++) {
            LocalDate ld = LocalDate.parse(dates.get(i));
            assertEquals(expected.get(i), dialect.wrapDate(ld, true));
        }
    }

    @Test
    void wrapDateWithTime() {
        String expected = "'1977-12-11 17:00:00'";
        assertEquals(expected, dialect.wrapDateWithTime("1977-12-11 17:00:00"));
    }

    @Test
    void wrapTimestamp() {
        String expected = "'1977-12-11 17:00:00'";
        assertEquals(expected, dialect.wrapDateWithTime("1977-12-11 17:00:00"));
    }

    @Test
    void wrapTimestampWithDateWideRangeTrue() {
        List<String> timestampsTZ = List.of(
                "+123456-05-01T15:10:00.12",
                "1985-05-12T15:10:01.123",
                "-1432-05-13T15:10:02.1234",
                "1985-05-14T15:10:05"
        );
        List<String> expected = List.of(
                "'+123456-05-01T15:10:00.12'",
                "'1985-05-12T15:10:01.123'",
                "'-1432-05-13T15:10:02.1234'",
                "'1985-05-14T15:10:05'"
        );
        for (int i = 0; i < timestampsTZ.size(); i++) {
            LocalDateTime ldt = LocalDateTime.parse(timestampsTZ.get(i));
            assertEquals(expected.get(i), dialect.wrapTimestamp(ldt, true));
        }
    }

    @Test
    void wrapTimestampWithDateWideRangeFalse() {
        List<String> timestamps = List.of(
                "1985-05-11T15:10:00.12",
                "1985-05-12T15:10:01.123",
                "1432-05-13T15:10:02.1234",
                "1985-05-14T15:10:05"
        );
        List<String> expected = List.of(
                "'1985-05-11 15:10:00'",
                "'1985-05-12 15:10:01'",
                "'1432-05-13 15:10:02'",
                "'1985-05-14 15:10:05'"
        );
        for (int i = 0; i < timestamps.size(); i++) {
            LocalDateTime ldt = LocalDateTime.parse(timestamps.get(i));
            assertEquals(expected.get(i), dialect.wrapTimestamp(ldt, false));
        }
    }

    @Test
    void buildSessionQuery() {
        assertEquals("SET timeout = 200", dialect.buildSessionQuery("timeout", "200"));
    }

    @Test
    void wrapTimestampWithTZ() {
        Exception err = assertThrows(
                UnsupportedOperationException.class,
                () -> dialect.wrapTimestampWithTZ("1985-05-11 15:10:00.12+03")
        );
        assertEquals("The database doesn't support pushdown of the `TIMESTAMP WITH TIME ZONE` data type", err.getMessage());
    }

    @Test
    void getDateValueWithDateWideRangeFalse() throws SQLException {
        String date = "1977-12-11";
        String colName = "dt";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getDate(colName)).thenReturn(Date.valueOf(date));
        assertEquals(date, dialect.getDateValue(resultSet, colName, false));
    }

    @Test
    void getDateValueWithDateWideRangeTrue() throws SQLException {
        String date = "1977-12-11";
        String colName = "dt";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(colName, LocalDate.class)).thenReturn(LocalDate.parse(date));
        assertEquals("1977-12-11 AD", dialect.getDateValue(resultSet, colName, true));
    }

    @Test
    void getDateTimestampWithDateWideRangeFalse() throws SQLException {
        String timestamp = "1977-12-11 13:10:00.123";
        String colName = "ts";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getTimestamp(colName)).thenReturn(Timestamp.valueOf(timestamp));
        assertEquals(timestamp, dialect.getTimestampValue(resultSet, colName, false));
    }

    @Test
    void getDateTimestampWithDateWideRangeTrue() throws SQLException {
        String timestamp = "1977-12-11T13:10:00.123";
        String colName = "ts";
        ResultSet resultSet = mock(ResultSet.class);
        when(resultSet.getObject(colName, LocalDateTime.class)).thenReturn(LocalDateTime.parse(timestamp));
        assertEquals("1977-12-11 13:10:00.123 AD", dialect.getTimestampValue(resultSet, colName, true));
    }

    @Test
    void getTimestampTZValue() throws SQLException {
        String value = "1977-12-11T13:12:00.123+03:00";
        String expected = "1977-12-11 13:12:00.123+03:00 AD";
        String colName = "tstz";
        ResultSet resultSet = mock(ResultSet.class);
        OffsetDateTime odt = OffsetDateTime.parse(value);
        when(resultSet.getObject(colName, OffsetDateTime.class)).thenReturn(odt);
        assertEquals(expected, dialect.getTimestampTZValue(resultSet, colName));
    }
}