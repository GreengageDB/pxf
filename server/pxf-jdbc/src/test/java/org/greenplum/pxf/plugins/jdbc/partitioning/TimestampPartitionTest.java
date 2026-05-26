package org.greenplum.pxf.plugins.jdbc.partitioning;

import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.greenplum.pxf.plugins.jdbc.dialect.PostgresDatabaseDialect;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimestampPartitionTest {

    private final DatabaseDialect dialect = new PostgresDatabaseDialect();

    private final String COL_RAW = "col";
    private final String QUOTE = "\"";
    private final String COL = QUOTE + COL_RAW + QUOTE;

    @Test
    public void testNormal() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, LocalDateTime.parse("2024-01-02T17:19:10"), LocalDateTime.parse("2024-01-02T17:19:11"));
        String constraint = partition.toSqlConstraint(QUOTE, dialect);

        assertEquals(
                COL + " >= '2024-01-02T17:19:10' AND " + COL + " < '2024-01-02T17:19:11'",
                constraint
        );
        assertEquals(COL_RAW, partition.getColumn());
    }

    @Test
    public void testDateWideRange() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, LocalDateTime.of(-1, 2,3, 12, 30, 5), LocalDateTime.of(-1, 2,3, 12, 50, 5), true);
        String constraint = partition.toSqlConstraint(QUOTE, dialect);

        assertEquals(
                COL + " >= '0002-02-03 12:30:05 BC' AND " + COL + " < '0002-02-03 12:50:05 BC'",
                constraint
        );
        assertEquals(COL_RAW, partition.getColumn());
    }

    @Test
    public void testWrapDateWithTime() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, LocalDateTime.parse("2024-01-02T17:19:10"), LocalDateTime.parse("2024-01-02T17:19:11"));
        String constraint = partition.toSqlConstraint(QUOTE, dialect);

        assertEquals(
                COL + " >= '2024-01-02T17:19:10' AND " + COL + " < '2024-01-02T17:19:11'",
                constraint
        );
        assertEquals(COL_RAW, partition.getColumn());
    }

    @Test
    public void testRightBounded() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, null, LocalDateTime.parse("2024-01-02T17:19:11"));
        String constraint = partition.toSqlConstraint(QUOTE, dialect);

        assertEquals(
                COL + " < '2024-01-02T17:19:11'",
                constraint
        );
    }

    @Test
    public void testLeftBounded() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, LocalDateTime.parse("2024-01-02T17:19:10"), null);
        String constraint = partition.toSqlConstraint(QUOTE, dialect);

        assertEquals(
                COL + " >= '2024-01-02T17:19:10'",
                constraint
        );
    }

    @Test
    public void testInvalidBothBoundariesNull() {
        Exception ex = assertThrows(RuntimeException.class,
                () -> new TimestampPartition(COL_RAW, null, null));
        assertEquals("Both boundaries cannot be null", ex.getMessage());
    }

    @Test
    public void testInvalidColumnNull() {
        assertThrows(RuntimeException.class,
                () -> new TimestampPartition(null, LocalDateTime.parse("2024-01-02T17:19:10"), LocalDateTime.parse("2024-01-02T17:19:10")));
    }

    @Test
    public void testInvalidEqualBoundaries() {
        assertThrows(RuntimeException.class,
                () -> new TimestampPartition(COL_RAW, LocalDateTime.parse("2000-01-02T17:19:10"), LocalDateTime.parse("2000-01-02T17:19:10")));
    }

    @Test
    public void testInvalidNullDbProduct() {
        TimestampPartition partition = new TimestampPartition(COL_RAW, LocalDateTime.parse("2000-01-02T17:19:10"), LocalDateTime.parse("2000-01-02T17:45:10"));
        assertThrows(RuntimeException.class,
                () -> partition.toSqlConstraint(COL, null));
    }
}
