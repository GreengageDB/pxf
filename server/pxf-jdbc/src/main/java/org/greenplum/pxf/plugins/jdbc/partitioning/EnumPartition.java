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

import java.util.StringJoiner;

@Getter
public class EnumPartition extends BaseValuePartition implements JdbcFragmentMetadata {

    private final String value;

    private final String[] excluded;

    /**
     * Construct an EnumPartition with given column and constraint
     *
     * @param column the partitioned column
     * @param value  the value for the partition
     */
    public EnumPartition(String column, String value) {
        this(column, value, null);
        if (value == null) {
            throw new RuntimeException("Value cannot be null");
        }
    }

    /**
     * Construct an EnumPartition with given column and a special (exclusion) constraint.
     * The partition created by this constructor contains all values that differ from the given ones.
     *
     * @param column   column name to use as a partition column
     * @param excluded array of values this partition must NOT include
     */
    public EnumPartition(String column, String[] excluded) {
        this(column, null, excluded);
        if (excluded == null) {
            throw new RuntimeException("Excluded values cannot be null");
        }
        if (excluded.length == 0) {
            throw new RuntimeException("Array of excluded values cannot be of zero length");
        }
    }

    public EnumPartition(String column, String value, String[] excluded) {
        super(column);
        this.value = value;
        this.excluded = excluded;
    }

    @Override
    public String toSqlConstraint(String quoteString, DatabaseDialect dialect) {
        String quotedColumn = getQuotedColumn(quoteString);

        if (excluded != null && excluded.length > 0) {
            // We use inequality operator as it is the widest supported method
            StringJoiner joiner = new StringJoiner( " AND ", "( ", " )");
            for (String excludedValue : excluded) {
                joiner.add(quotedColumn + " <> '" + excludedValue + "'");
            }
            return joiner.toString();
        }

        return generateConstraint(quotedColumn, "'" + value + "'");
    }
}
