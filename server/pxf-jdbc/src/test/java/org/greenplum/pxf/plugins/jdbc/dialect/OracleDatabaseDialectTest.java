package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OracleDatabaseDialectTest {
    private static final String PRODUCT_NAME = "ORACLE";
    private static final DatabaseDialect dialect = new OracleDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void wrapDate() {
        assertEquals("to_date('2001-01-01', 'YYYY-MM-DD')", dialect.wrapDate("2001-01-01"));
        assertEquals("to_date('2001-03-04', 'YYYY-MM-DD')", dialect.wrapDate(LocalDate.of(2001, 3, 4), false));
        assertEquals("to_date('2001-03-04', 'YYYY-MM-DD')", dialect.wrapDate(LocalDate.of(2001, 3, 4), true));
        assertEquals("to_date('-0500-03-04', 'YYYY-MM-DD')", dialect.wrapDate(LocalDate.of(-500, 3, 4), true));
    }

    @Test
    void wrapDateWithTime() {
        String expected = "to_date('2001-01-01 00:00:00', 'YYYY-MM-DD HH24:MI:SS')";
        assertEquals(expected, dialect.wrapDateWithTime("2001-01-01 00:00:00"));
    }

    @Test
    void wrapTimestamp() {
        String expected = "to_timestamp('2001-01-01 00:00:00.0', 'YYYY-MM-DD HH24:MI:SS.FF')";
        assertEquals(expected, dialect.wrapTimestamp("2001-01-01 00:00:00.0"));
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
                "to_timestamp_tz('1985-05-11 15:10:00.12+03', 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM')",
                "to_timestamp_tz('1985-05-12 15:10:00.123+05:30', 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM')",
                "to_timestamp_tz('1985-05-13 15:10:00.1234+3', 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM')",
                "to_timestamp_tz('1985-05-14 15:10:00-04:45', 'YYYY-MM-DD HH24:MI:SS.FFTZH:TZM')"
        );
        for (int i = 0; i < timestampsTZ.size(); i++) {
            assertEquals(expected.get(i), dialect.wrapTimestampWithTZ(timestampsTZ.get(i)));
        }
    }

    @Test
    void buildSessionQuery() {
    }
}