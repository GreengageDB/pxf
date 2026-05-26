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

import lombok.Getter;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

import java.time.LocalDate;
import java.util.Objects;

@Getter
class DatePartition extends BaseRangePartition {

    private final LocalDate start;
    private final LocalDate end;
    private final boolean isDateWideRange;

    /**
     * Construct a DatePartition covering a range of values from 'start' to 'end'
     *
     * @param column the partitioned column
     * @param start  null for right-bounded interval
     * @param end    null for left-bounded interval
     */
    public DatePartition(String column, LocalDate start, LocalDate end) {
        this(column, start, end, false);
    }

    /**
     * Construct a DatePartition covering a range of values from 'start' to 'end'
     *
     * @param column          the partitioned column
     * @param start           null for right-bounded interval
     * @param end             null for left-bounded interval
     * @param isDateWideRange flag which is used when the year might contain more than 4 digits
     */
    public DatePartition(String column, LocalDate start, LocalDate end, boolean isDateWideRange) {
        super(column);
        this.start = start;
        this.end = end;
        this.isDateWideRange = isDateWideRange;
        if (start == null && end == null) {
            throw new RuntimeException("Both boundaries cannot be null");
        }
        if (Objects.equals(start, end)) {
            throw new RuntimeException(String.format(
                    "Boundaries cannot be equal for partition of type '%s'", PartitionType.DATE
            ));
        }
    }

    @Override
    public String toSqlConstraint(String quoteString, DatabaseDialect dialect) {
        if (dialect == null) {
            throw new RuntimeException(String.format(
                    "Partitioning by %s is not supported for this DB", PartitionType.DATE
            ));
        }

        return generateRangeConstraint(
                getQuotedColumn(quoteString),
                convert(start, dialect),
                convert(end, dialect)
        );
    }

    private String convert(LocalDate value, DatabaseDialect dialect) {
        if (value == null) {
            return null;
        }
        return dialect.wrapDate(value, isDateWideRange);
    }
}
