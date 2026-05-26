package org.greenplum.pxf.plugins.jdbc.partitioning;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.greenplum.pxf.plugins.jdbc.dialect.PostgresDatabaseDialect;
import org.junit.jupiter.api.Test;

import java.sql.Date;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class DatePartitionTestGenerate {

    @Test
    public void testPartitionByDateIntervalDay() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2008-01-11";
        final String INTERVAL = "1:day";

        DatePartition[] parts = PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (DatePartition) p).toArray(DatePartition[]::new);

        assertEquals(12, parts.length);
        assertDatePartitionEquals(parts[0], null, Date.valueOf("2008-01-01"));
        assertDatePartitionEquals(parts[1], Date.valueOf("2008-01-11"), null);
        assertDatePartitionEquals(parts[2], Date.valueOf("2008-01-01"), Date.valueOf("2008-01-02"));
        assertDatePartitionEquals(parts[6], Date.valueOf("2008-01-05"), Date.valueOf("2008-01-06"));
        assertDatePartitionEquals(parts[11], Date.valueOf("2008-01-10"), Date.valueOf("2008-01-11"));
    }

    @Test
    public void testPartitionByDateIntervalMonth() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2008-12-31";
        final String INTERVAL = "1:month";

        DatePartition[] parts = PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (DatePartition) p).toArray(DatePartition[]::new);

        assertEquals(14, parts.length);
        assertDatePartitionEquals(parts[0], null, Date.valueOf("2008-01-01"));
        assertDatePartitionEquals(parts[1], Date.valueOf("2008-12-31"), null);
        assertDatePartitionEquals(parts[2], Date.valueOf("2008-01-01"), Date.valueOf("2008-02-01"));
        assertDatePartitionEquals(parts[3], Date.valueOf("2008-02-01"), Date.valueOf("2008-03-01"));
        assertDatePartitionEquals(parts[4], Date.valueOf("2008-03-01"), Date.valueOf("2008-04-01"));
        assertDatePartitionEquals(parts[5], Date.valueOf("2008-04-01"), Date.valueOf("2008-05-01"));
        assertDatePartitionEquals(parts[6], Date.valueOf("2008-05-01"), Date.valueOf("2008-06-01"));
        assertDatePartitionEquals(parts[7], Date.valueOf("2008-06-01"), Date.valueOf("2008-07-01"));
        assertDatePartitionEquals(parts[12], Date.valueOf("2008-11-01"), Date.valueOf("2008-12-01"));
    }

    @Test
    public void testPartitionByDateIntervalYear() {
        final String COLUMN = "col";
        final String RANGE = "2008-02-03:2018-02-02";
        final String INTERVAL = "1:year";

        DatePartition[] parts = PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (DatePartition) p).toArray(DatePartition[]::new);

        assertEquals(12, parts.length);
        assertDatePartitionEquals(parts[0], null, Date.valueOf("2008-02-03"));
        assertDatePartitionEquals(parts[1], Date.valueOf("2018-02-02"), null);
        assertDatePartitionEquals(parts[2], Date.valueOf("2008-02-03"), Date.valueOf("2009-02-03"));
        assertDatePartitionEquals(parts[7], Date.valueOf("2013-02-03"), Date.valueOf("2014-02-03"));
        assertDatePartitionEquals(parts[10], Date.valueOf("2016-02-03"), Date.valueOf("2017-02-03"));
    }

    @Test
    public void testPartitionByDateIntervalYearDateWideRange() {
        final String COLUMN = "col";
        final String RANGE = "0101-02-03 BC:10001-02-02 AD";
        final String INTERVAL = "1000:year";
        final DatabaseDialect dialect = new PostgresDatabaseDialect();
        final boolean WRAP_DATE_WITH_TIME = false;

        DatePartition[] parts = PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, true).stream()
                .map(p -> (DatePartition) p).toArray(DatePartition[]::new);

        assertEquals(13, parts.length);
        assertEquals("col < date'0101-02-03 BC'", parts[0].toSqlConstraint("", dialect));
        assertEquals("col >= date'10001-02-02 AD'", parts[1].toSqlConstraint("", dialect));
        assertEquals("col >= date'0101-02-03 BC' AND col < date'0900-02-03 AD'", parts[2].toSqlConstraint("", dialect));
        assertEquals("col >= date'4900-02-03 AD' AND col < date'5900-02-03 AD'", parts[7].toSqlConstraint("", dialect));
        assertEquals("col >= date'9900-02-03 AD' AND col < date'10001-02-02 AD'", parts[12].toSqlConstraint("", dialect));
    }

    @Test
    public void testPartitionByDateIntervalYearIncomplete() {
        final String COLUMN = "col";
        final String RANGE = "2008-02-03:2010-01-15";
        final String INTERVAL = "1:year";

        DatePartition[] parts = PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false).stream()
                .map(p -> (DatePartition) p).toArray(DatePartition[]::new);

        assertEquals(4, parts.length);
        assertDatePartitionEquals(parts[0], null, Date.valueOf("2008-02-03"));
        assertDatePartitionEquals(parts[1], Date.valueOf("2010-01-15"), null);
        assertDatePartitionEquals(parts[2], Date.valueOf("2008-02-03"), Date.valueOf("2009-02-03"));
        assertDatePartitionEquals(parts[3], Date.valueOf("2009-02-03"), Date.valueOf("2010-01-15"));
    }

    @Test
    public void testRangeDateFormatInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008/01/01:2009-01-01";
        final String INTERVAL = "1:month";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalValueInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2009-01-01";
        final String INTERVAL = "-1:month";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalTypeInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2011-01-01";
        final String INTERVAL = "6:hour";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testIntervalTypeMissingInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2011-01-01";
        final String INTERVAL = "6";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testRangeMissingEndInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01";
        final String INTERVAL = "1:year";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    @Test
    public void testRangeDateSwappedInvalid() {
        final String COLUMN = "col";
        final String RANGE = "2008-01-01:2001-01-01";
        final String INTERVAL = "1:month";

        assertThrows(IllegalArgumentException.class,
            () -> PartitionType.DATE.generate(COLUMN, RANGE, INTERVAL, false));
    }

    /**
     * Assert partition and given range of dates match.
     *
     * @param partition  the data partition
     * @param rangeStart (null is allowed)
     * @param rangeEnd   (null is allowed)
     */
    private void assertDatePartitionEquals(DatePartition partition, Date rangeStart, Date rangeEnd) {
        assertEquals(rangeStart, convertToDate(partition.getStart()));
        assertEquals(rangeEnd, convertToDate(partition.getEnd()));
    }

    private static Date convertToDate(LocalDate date) {
        if (date == null) {
            return null;
        }
        return Date.valueOf(date);
    }
}
