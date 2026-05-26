package org.greenplum.pxf.plugins.jdbc.partitioning;

import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.greenplum.pxf.plugins.jdbc.dialect.PostgresDatabaseDialect;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TimestampPartitionTestGenerate {
    @Test
    public void testPartitionByTimestampIntervalSecond() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124910:20240101T125001";
        final String INTERVAL = "5:second";

        TimestampPartition[] parts = PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (TimestampPartition) p).toArray(TimestampPartition[]::new);

        assertEquals(13, parts.length);
        assertTimestampPartitionEquals(parts[0], null, Timestamp.valueOf("2024-01-01 12:49:10"));
        assertTimestampPartitionEquals(parts[1], Timestamp.valueOf("2024-01-01 12:50:01"), null);
        assertTimestampPartitionEquals(parts[2], Timestamp.valueOf("2024-01-01 12:49:10"), Timestamp.valueOf("2024-01-01 12:49:15"));
        assertTimestampPartitionEquals(parts[6], Timestamp.valueOf("2024-01-01 12:49:30"), Timestamp.valueOf("2024-01-01 12:49:35"));
        assertTimestampPartitionEquals(parts[12], Timestamp.valueOf("2024-01-01 12:50:00"), Timestamp.valueOf("2024-01-01 12:50:01"));
    }

    @Test
    public void testPartitionByTimestampIntervalMinute() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T130530";
        final String INTERVAL = "1:minute";

        TimestampPartition[] parts = PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (TimestampPartition) p).toArray(TimestampPartition[]::new);

        assertEquals(20, parts.length);
        assertTimestampPartitionEquals(parts[0], null, Timestamp.valueOf("2024-01-01 12:48:10"));
        assertTimestampPartitionEquals(parts[1], Timestamp.valueOf("2024-01-01 13:05:30"), null);
        assertTimestampPartitionEquals(parts[2], Timestamp.valueOf("2024-01-01 12:48:10"), Timestamp.valueOf("2024-01-01 12:49:10"));
        assertTimestampPartitionEquals(parts[6], Timestamp.valueOf("2024-01-01 12:52:10"), Timestamp.valueOf("2024-01-01 12:53:10"));
        assertTimestampPartitionEquals(parts[13], Timestamp.valueOf("2024-01-01 12:59:10"), Timestamp.valueOf("2024-01-01 13:00:10"));
        assertTimestampPartitionEquals(parts[19], Timestamp.valueOf("2024-01-01 13:05:10"), Timestamp.valueOf("2024-01-01 13:05:30"));
    }

    @Test
    public void testPartitionByTimestampIntervalHour() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T200530";
        final String INTERVAL = "1:hour";

        TimestampPartition[] parts = PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (TimestampPartition) p).toArray(TimestampPartition[]::new);

        assertEquals(10, parts.length);
        assertTimestampPartitionEquals(parts[0], null, Timestamp.valueOf("2024-01-01 12:48:10"));
        assertTimestampPartitionEquals(parts[1], Timestamp.valueOf("2024-01-01 20:05:30"), null);
        assertTimestampPartitionEquals(parts[2], Timestamp.valueOf("2024-01-01 12:48:10"), Timestamp.valueOf("2024-01-01 13:48:10"));
        assertTimestampPartitionEquals(parts[6], Timestamp.valueOf("2024-01-01 16:48:10"), Timestamp.valueOf("2024-01-01 17:48:10"));
        assertTimestampPartitionEquals(parts[9], Timestamp.valueOf("2024-01-01 19:48:10"), Timestamp.valueOf("2024-01-01 20:05:30"));
    }

    @Test
    public void testPartitionByTimestampIntervalMinuteWideRange() {
        final String COLUMN = "col";
        final String RANGE = "101010203T124810 BC:101010203T130530 BC";
        final String INTERVAL = "1:minute";
        final DatabaseDialect dialect = new PostgresDatabaseDialect();
        final boolean WRAP_DATE_WITH_TIME = false;

        TimestampPartition[] parts = PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, true).stream()
                .map(p -> (TimestampPartition) p).toArray(TimestampPartition[]::new);

        assertEquals(20, parts.length);
        assertEquals("col < '10101-02-03 12:48:10 BC'", parts[0].toSqlConstraint("", dialect));
        assertEquals("col >= '10101-02-03 13:05:30 BC'", parts[1].toSqlConstraint("", dialect));
        assertEquals("col >= '10101-02-03 12:48:10 BC' AND col < '10101-02-03 12:49:10 BC'", parts[2].toSqlConstraint("", dialect));
        assertEquals("col >= '10101-02-03 12:53:10 BC' AND col < '10101-02-03 12:54:10 BC'", parts[7].toSqlConstraint("", dialect));
        assertEquals("col >= '10101-02-03 13:05:10 BC' AND col < '10101-02-03 13:05:30 BC'", parts[19].toSqlConstraint("", dialect));
    }

    @Test
    public void testRangeTimestampFormatInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2024-01-01 12:48:10:2024-01-01 20:05:30";
        final String INTERVAL = "1:month";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalValueInvalid() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T130530";
        final String INTERVAL = "-1:hour";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalTypeInvalid() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T130530";
        final String INTERVAL = "6:millisecond";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalTypeMissingInvalid() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T130530";
        final String INTERVAL = "6";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testRangeMissingEndInvalid() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810";
        final String INTERVAL = "1:second";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testEqualRangeParts() {
        final String COLUMN = "col";
        final String RANGE = "20240101T124810:20240101T124810";
        final String INTERVAL = "1:second";

        assertThrows(IllegalArgumentException.class,
                () -> PartitionType.TIMESTAMP.generate(COLUMN, RANGE, INTERVAL, false));
    }

    private void assertTimestampPartitionEquals(TimestampPartition partition, Timestamp rangeStart, Timestamp rangeEnd) {
        assertEquals(rangeStart, convertToTimestamp(partition.getStart()));
        assertEquals(rangeEnd, convertToTimestamp(partition.getEnd()));
    }

    private static Timestamp convertToTimestamp(LocalDateTime date) {
        if (date == null) {
            return null;
        }
        return Timestamp.valueOf(date);
    }
}
