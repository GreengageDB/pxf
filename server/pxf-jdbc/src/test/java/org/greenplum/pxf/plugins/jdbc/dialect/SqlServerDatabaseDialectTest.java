package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SqlServerDatabaseDialectTest {
    private static final String PRODUCT_NAME = "MICROSOFT";
    private static final DatabaseDialect dialect = new SqlServerDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void buildSessionQuery() {
        assertEquals("SET timeout 300", dialect.buildSessionQuery("timeout", "300"));
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
                "CONVERT(DATETIMEOFFSET, '1985-05-11T15:10:00.12+03:00')",
                "CONVERT(DATETIMEOFFSET, '1985-05-12T15:11:00.123+05:30')",
                "CONVERT(DATETIMEOFFSET, '1985-05-13T15:12:00.1234+03:00')",
                "CONVERT(DATETIMEOFFSET, '1985-05-14T15:13:00-04:45')",
                "CONVERT(DATETIMEOFFSET, '1985-05-15T15:14:00-04:00')",
                "CONVERT(DATETIMEOFFSET, '1985-05-15T15:16:25.1+00:00')"
        );
        for (int i = 0; i < timestampsTZ.size(); i++) {
            assertEquals(expected.get(i), dialect.wrapTimestampWithTZ(timestampsTZ.get(i)));
        }
    }
}