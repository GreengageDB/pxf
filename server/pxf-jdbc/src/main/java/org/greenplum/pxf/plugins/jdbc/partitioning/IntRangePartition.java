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

import java.util.Objects;

import static org.greenplum.pxf.plugins.jdbc.partitioning.IntPartition.convert;

@Getter
public class IntRangePartition extends BaseRangePartition implements IntPartition {

    private final Long start;
    private final Long end;

    /**
     * @param column the partition column
     * @param start  null for right-bounded interval
     * @param end    null for left-bounded interval
     */
    public IntRangePartition(String column, Long start, Long end) {
        super(column);
        this.start = start;
        this.end = end;
        if (Objects.equals(start, end)) {
            throw new RuntimeException("Start and end boundaries cannot be the same");
        }
    }

    @Override
    public String toSqlConstraint(String quoteString, DatabaseDialect dialect) {
        return generateRangeConstraint(
                getQuotedColumn(quoteString),
                convert(start),
                convert(end)
        );
    }
}
