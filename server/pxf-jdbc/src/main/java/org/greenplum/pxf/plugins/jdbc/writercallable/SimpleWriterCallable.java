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

import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.plugins.jdbc.JdbcResolver;
import org.greenplum.pxf.plugins.jdbc.JdbcBasePlugin;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * This writer makes simple, one-by-one INSERTs.
 * A call() is required after every supply()
 */
@Slf4j
class SimpleWriterCallable implements WriterCallable {
    private final JdbcBasePlugin plugin;
    private final String query;
    private OneRow row;
    private final Runnable onComplete;
    private final DatabaseDialect dialect;

    SimpleWriterCallable(JdbcBasePlugin plugin, String query, Runnable onComplete, DatabaseDialect dialect) {
        if (plugin == null) {
            throw new IllegalArgumentException("Plugin must not be null");
        } else if (query == null) {
            throw new IllegalArgumentException("Query must not be null");
        } else if (onComplete == null) {
            throw new IllegalArgumentException("onComplete must not be null");
        }
        this.plugin = plugin;
        this.query = query;
        this.onComplete = onComplete;
        this.dialect = dialect;
        row = null;
    }

    @Override
    public void supply(OneRow row) throws IllegalStateException {
        if (this.row != null) {
            throw new IllegalStateException("Trying to supply() a OneRow object to a full WriterCallable");
        }
        if (row == null) {
            throw new IllegalArgumentException("Trying to supply() a null OneRow object");
        }
        this.row = row;
    }

    @Override
    public boolean isCallRequired() {
        return this.row != null;
    }

    @Override
    public SQLException call() throws SQLException {
        log.trace("Writer {}: call() to insert row", this);
        long start = System.nanoTime();
        if (row == null) {
            return null;
        }

        PreparedStatement statement = null;
        try {
            statement = plugin.getPreparedStatement(plugin.getConnection(), query);
            log.trace("Writer {}: got statement", this);
            JdbcResolver.decodeOneRowToPreparedStatement(row, statement, dialect);
            statement.executeUpdate();
            // some drivers will not react to timeout interrupt
            if (Thread.interrupted())
                throw new SQLException("Writer was interrupted by timeout or by request");
        } catch (SQLException e) {
            log.error("Writer {}: call() failed: SQLException", this, e);
            return e;
        } catch (Throwable t) {
            log.error("Writer {}: call() failed: Throwable", this, t);
            if (t.getCause() instanceof SQLException) {
                return (SQLException) t.getCause();
            } else {
                return new SQLException(t);
            }
        } finally {
            if (log.isTraceEnabled()) {
                long duration = System.nanoTime() - start;
                log.trace("Writer {}: call() done in {} ms", this, duration / 1000000);
            }
            row = null;
            try {
                JdbcBasePlugin.closeStatementAndConnection(statement);
            } finally {
                log.trace("Writer {} completed inserting the batch", this);
                onComplete.run();
            }
        }

        return null;
    }

    @Override
    public String toString() {
        return String.format("SimpleWriterCallable@%d", hashCode());
    }
}
