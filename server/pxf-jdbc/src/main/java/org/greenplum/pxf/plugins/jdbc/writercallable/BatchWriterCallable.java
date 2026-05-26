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
import org.greenplum.pxf.plugins.jdbc.JdbcBasePlugin;
import org.greenplum.pxf.plugins.jdbc.JdbcResolver;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

import java.sql.BatchUpdateException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * This writer makes batch INSERTs.
 * <p>
 * A call() is required after a certain number of supply() calls
 */
@Slf4j
class BatchWriterCallable implements WriterCallable {
    private final JdbcBasePlugin plugin;
    private final String query;
    private final List<OneRow> rows;
    private final int batchSize;
    private final Runnable onComplete;
    private final DatabaseDialect dialect;

    /**
     * Construct a new batch writer
     */
    BatchWriterCallable(JdbcBasePlugin plugin, String query, int batchSize, Runnable onComplete, DatabaseDialect dialect) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("Batch size must be greater than 0");
        } else if (plugin == null) {
            throw new IllegalArgumentException("Plugin must not be null");
        } else if (query == null) {
            throw new IllegalArgumentException("Query must not be null");
        } else if (onComplete == null) {
            throw new IllegalArgumentException("onComplete must not be null");
        }

        this.plugin = plugin;
        this.query = query;
        this.batchSize = batchSize;
        this.onComplete = onComplete;
        this.dialect = dialect;
        rows = new ArrayList<>();
    }

    @Override
    public void supply(OneRow row) throws IllegalStateException {
        if ((batchSize > 0) && (rows.size() >= batchSize)) {
            throw new IllegalStateException("Trying to supply() a OneRow object to a full WriterCallable");
        }
        if (row == null) {
            throw new IllegalArgumentException("Trying to supply() a null OneRow object");
        }
        rows.add(row);
    }

    @Override
    public boolean isCallRequired() {
        return (batchSize > 0) && (rows.size() >= batchSize);
    }

    @Override
    public SQLException call() throws SQLException {
        log.trace("Writer {}: call() to insert {} rows", this, rows.size());
        long start = System.nanoTime();
        if (rows.isEmpty()) {
            return null;
        }

        PreparedStatement statement = null;
        try {
            statement = plugin.getPreparedStatement(plugin.getConnection(), query);
            log.trace("Writer {}: got statement", this);
            for (OneRow row : rows) {
                JdbcResolver.decodeOneRowToPreparedStatement(row, statement, dialect);
                statement.addBatch();
            }

            statement.executeBatch();
            log.trace("Writer {}: executeBatch() finished", this);
            // some drivers will not react to timeout interrupt
            if (Thread.interrupted())
                throw new SQLException("Writer was interrupted by timeout or by request");
        } catch (BatchUpdateException bue) {
            SQLException cause = bue.getNextException();
            cause = cause != null ? cause : bue;
            log.error("Writer {}: call() failed: BatchUpdateException", this, cause);
            return cause;
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
            rows.clear();
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
        return String.format("BatchWriterCallable@%d", hashCode());
    }
}
