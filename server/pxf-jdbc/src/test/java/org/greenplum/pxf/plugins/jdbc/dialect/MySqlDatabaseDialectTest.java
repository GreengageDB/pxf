package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MySqlDatabaseDialectTest {
    private static final String PRODUCT_NAME = "MYSQL";
    private static final DatabaseDialect dialect = new MySqlDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void wrapDate() {
        String date = "2004-03-17";
        String expected = "DATE('2004-03-17')";
        LocalDate ld = LocalDate.parse(date);
        assertEquals(expected, dialect.wrapDate(ld, false));
    }
}
