package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PostgresDatabaseDialectTest {
    private static final String PRODUCT_NAME = "POSTGRESQL";
    private static final DatabaseDialect dialect = new PostgresDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void testWrapDateWideRangeFalse() {
        String date = "2004-03-17";
        String expected = "date'2004-03-17'";
        LocalDate ld = LocalDate.parse(date);
        assertEquals(expected, dialect.wrapDate(ld, false));
    }

    @Test
    void testWrapDateWideRangeTrue() {
        List<String> dates = List.of(
                "1985-05-11",
                "-1432-05-13"
        );
        List<String> expected = List.of(
                "date'1985-05-11 AD'",
                "date'1433-05-13 BC'"
        );
        for (int i = 0; i < dates.size(); i++) {
            LocalDate ld = LocalDate.parse(dates.get(i));
            assertEquals(expected.get(i), dialect.wrapDate(ld, true));
        }
    }

    @Test
    void wrapTimestampWithDateWideRangeTrue() {
        List<String> timestampsTZ = List.of(
                "1985-05-11T15:10:00.12",
                "1985-05-12T15:10:01.123",
                "-1432-05-13T15:10:02.1234",
                "1985-05-14T15:10:05"
        );
        List<String> expected = List.of(
                "'1985-05-11 15:10:00.12 AD'",
                "'1985-05-12 15:10:01.123 AD'",
                "'1433-05-13 15:10:02.1234 BC'",
                "'1985-05-14 15:10:05 AD'"
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
                "'1985-05-11T15:10:00.120'",
                "'1985-05-12T15:10:01.123'",
                "'1432-05-13T15:10:02.123400'",
                "'1985-05-14T15:10:05'"
        );
        for (int i = 0; i < timestamps.size(); i++) {
            LocalDateTime ldt = LocalDateTime.parse(timestamps.get(i));
            assertEquals(expected.get(i), dialect.wrapTimestamp(ldt, false));
        }
    }

    @Test
    void wrapTimestampWithTZ() {
        List<String> timestampsTZ = List.of(
                "1985-05-11 15:10:00.12+03",
                "1985-05-12 15:10:00.123+05:30",
                "1985-05-13 15:10:00.1234+3",
                "1985-05-14 15:10:00-04:45"
        );
        List<String> expected = List.of(
                "'1985-05-11 15:10:00.12+03'",
                "'1985-05-12 15:10:00.123+05:30'",
                "'1985-05-13 15:10:00.1234+3'",
                "'1985-05-14 15:10:00-04:45'"
        );
        for (int i = 0; i < timestampsTZ.size(); i++) {
            assertEquals(expected.get(i), dialect.wrapTimestampWithTZ(timestampsTZ.get(i)));
        }
    }
}