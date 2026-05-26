package org.greenplum.pxf.plugins.s3;

import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class S3SelectDatabaseDialectTest {
    private static final String PRODUCT_NAME = "S3 SELECT";
    private final DatabaseDialect dialect = new S3SelectDatabaseDialect();

    @Test
    void getName() {
        assertEquals(PRODUCT_NAME, dialect.getName());
    }

    @Test
    void wrapDate() {
        String date = "2004-03-17";
        String expected = "TO_TIMESTAMP('2004-03-17')";
        LocalDate ld = LocalDate.parse(date);
        assertEquals(expected, dialect.wrapDate(ld, false));
    }

    @Test
    void wrapDateWithTime() {
        String date = "2004-03-17 10:30";
        String expected = "TO_TIMESTAMP('2004-03-17 10:30')";
        assertEquals(expected, dialect.wrapDateWithTime(date));
    }

    @Test
    void wrapTimestamp() {
        String date = "2004-03-17 10:30";
        String expected = "TO_TIMESTAMP('2004-03-17 10:30')";
        assertEquals(expected, dialect.wrapTimestamp(date));
    }
}
