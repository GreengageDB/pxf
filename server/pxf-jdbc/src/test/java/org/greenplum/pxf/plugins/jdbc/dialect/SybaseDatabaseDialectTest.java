package org.greenplum.pxf.plugins.jdbc.dialect;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SybaseDatabaseDialectTest {
    private static final String PRODUCT_NAME = "ADAPTIVE SERVER ENTERPRISE";
    private static final DatabaseDialect dialect = new SybaseDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void buildSessionQuery() {
        assertEquals("SET timeout 10", dialect.buildSessionQuery("timeout", "10"));
    }

    @Test
    void wrapTimestampWithTZ() {
        Exception err = assertThrows(
                UnsupportedOperationException.class,
                () -> dialect.wrapTimestampWithTZ("1985-05-11 15:10:00.12+03")
        );
        assertEquals("The database %s doesn't support the TIMESTAMP WITH TIME ZONE data type".formatted(PRODUCT_NAME), err.getMessage());
    }
}