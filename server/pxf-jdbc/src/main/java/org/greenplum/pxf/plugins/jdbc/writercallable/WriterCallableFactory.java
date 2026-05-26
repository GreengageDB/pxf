package org.greenplum.pxf.plugins.jdbc.writercallable;

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

import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.plugins.jdbc.JdbcBasePlugin;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

/**
 * An object that processes INSERT operation on {@link OneRow} objects
 */
public class WriterCallableFactory {

    private final int batchSize;
    private final JdbcBasePlugin plugin;
    private final String query;
    private final Runnable onComplete;
    private final DatabaseDialect dialect;

    /**
     * Create a new instance of the factory.
     *
     */
    public WriterCallableFactory(JdbcBasePlugin plugin,
                                 String query,
                                 int batchSize,
                                 Runnable onComplete,
                                 DatabaseDialect dialect) {
        this.plugin = plugin;
        this.query = query;
        this.batchSize = batchSize;
        this.onComplete = onComplete;
        this.dialect = dialect;
    }

    /**
     * Get an instance of WriterCallable
     *
     * @return an implementation of WriterCallable, chosen based on parameters that were set for this factory
     */
    public WriterCallable get() {
        if (batchSize > 1) {
            return new BatchWriterCallable(plugin, query, batchSize, onComplete, dialect);
        }
        return new SimpleWriterCallable(plugin, query, onComplete, dialect);
    }

}
