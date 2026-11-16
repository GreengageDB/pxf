package org.greenplum.pxf.plugins.hdfs.parquet;

import org.apache.parquet.io.api.Binary;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ParquetIntervalUtilitiesTest {

    @Test
    void read() {
        byte[] bytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 101, 80};
        String actual = ParquetIntervalUtilities.read(bytes);
        assertEquals("1 years 2 mons 3 days 4 hours 5 mins 6 secs", actual);
        bytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 104, 101};
        actual = ParquetIntervalUtilities.read(bytes);
        assertEquals("1 years 2 mons 3 days 4 hours 5 mins 6.789 secs", actual);
    }

    @Test
    void write() {
        Binary binary = ParquetIntervalUtilities.write("1 year 2 month 3 day 4 hour 5 minute 6 second");
        assertNotNull(binary);
        byte[] actualBytes = binary.getBytes();
        assertEquals(ParquetIntervalUtilities.INTERVAL_TYPE_LENGTH, actualBytes.length);
        byte[] expectedBytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 101, 80};
        assertArrayEquals(expectedBytes, actualBytes);

        binary = ParquetIntervalUtilities.write("1 year 2 month 3 day 4 hour 5 minute 6.789 second");
        assertNotNull(binary);
        actualBytes = binary.getBytes();
        assertEquals(ParquetIntervalUtilities.INTERVAL_TYPE_LENGTH, actualBytes.length);
        expectedBytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 104, 101};
        assertArrayEquals(expectedBytes, actualBytes);
     }

    @Test
    void testRoundTripString() {
        String round = ParquetIntervalUtilities.read(Objects.requireNonNull(ParquetIntervalUtilities.write("1 year 2 month 3 day 4 hour 5 minute 6 second")).getBytes());
        assertEquals("1 years 2 mons 3 days 4 hours 5 mins 6 secs", round);
        round = ParquetIntervalUtilities.read(Objects.requireNonNull(ParquetIntervalUtilities.write("1 year 2 month 3 day 4 hour 5 minute 6.789 second")).getBytes());
        assertEquals("1 years 2 mons 3 days 4 hours 5 mins 6.789 secs", round);
    }

    @Test
    void testRoundTripBytes() {
        byte[] expectedBytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 101, 80};
        byte[] actualBytes = Objects.requireNonNull(ParquetIntervalUtilities.write(ParquetIntervalUtilities.read(expectedBytes))).getBytes();
        assertArrayEquals(expectedBytes, actualBytes);
        expectedBytes = new byte[] {0, 0, 0, 14, 0, 0, 0, 3, 0, -32, 104, 101};
        actualBytes = Objects.requireNonNull(ParquetIntervalUtilities.write(ParquetIntervalUtilities.read(expectedBytes))).getBytes();
        assertArrayEquals(expectedBytes, actualBytes);
    }
}