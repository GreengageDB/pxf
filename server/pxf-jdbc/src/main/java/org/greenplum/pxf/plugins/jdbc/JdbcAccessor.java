package org.greenplum.pxf.plugins.jdbc;

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

import io.arenadata.security.encryption.client.service.DecryptClient;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.error.PxfRuntimeException;
import org.greenplum.pxf.api.model.Accessor;
import org.greenplum.pxf.api.model.CancelableOperation;
import org.greenplum.pxf.api.model.ConfigurationFactory;
import org.greenplum.pxf.api.security.SecureLogin;
import org.greenplum.pxf.api.utilities.Utilities;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialectProvider;
import org.greenplum.pxf.plugins.jdbc.utils.ConnectionManager;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * JDBC tables accessor
 * <p>
 * The SELECT queries are processed by {@link java.sql.Statement}
 * <p>
 * The INSERT queries are processed by {@link java.sql.PreparedStatement} and
 * built-in JDBC batches of arbitrary size
 */
@Slf4j
public class JdbcAccessor extends JdbcBasePlugin implements Accessor, CancelableOperation {

    private static final String JDBC_READ_PREPARED_STATEMENT_PROPERTY_NAME = "jdbc.read.prepared-statement";

    private Statement statementRead = null;
    private ResultSet resultSetRead = null;

    private JdbcWriter writer;
    private boolean isCanceled;
    private DatabaseDialect dialect;

    /**
     * Creates a new instance of the JdbcAccessor
     */
    public JdbcAccessor() {
        super();
    }

    /**
     * Creates a new instance of accessor with provided connection manager.
     *
     * @param connectionManager connection manager
     * @param secureLogin       the instance of the secure login
     */
    JdbcAccessor(ConnectionManager connectionManager, SecureLogin secureLogin, DecryptClient decryptClient, DatabaseDialectProvider dialectProvider) {
        super(connectionManager, secureLogin, decryptClient, dialectProvider);
    }

    /**
     * openForRead() implementation
     * Create query, open JDBC connection, execute query and store the result into resultSet
     *
     * @return true if successful
     *
     * @throws SQLException if a database access error occurs
     */
    @Override
    public boolean openForRead() throws SQLException {
        log.debug("Accessor starts openForRead()");
        if (statementRead != null && !statementRead.isClosed()) {
            return true;
        }

        Connection connection = getConnection();
        dialect = getDatabaseDialect(connection.getMetaData().getDatabaseProductName());
        try {
            return openForReadInner(connection, dialect);
        } catch (Throwable e) {
            if (statementRead == null) {
                closeConnection(connection);
            }
            throw new PxfRuntimeException(e.getMessage(), e);
        }
    }

    private boolean openForReadInner(Connection connection, DatabaseDialect dialect) throws SQLException {
        String queryRead = buildSelectQuery(connection, dialect);
        log.trace("Select query: {}", queryRead);

        // Execute queries
        // Certain features of third-party JDBC drivers may require the use of a PreparedStatement, even if there are no

        // bind parameters. For example, Teradata's FastExport only works with PreparedStatements
        // https://teradata-docs.s3.amazonaws.com/doc/connectivity/jdbc/reference/current/jdbcug_chapter_2.html#BGBFBBEG
        boolean usePreparedStatement = parseJdbcUsePreparedStatementProperty();
        if (usePreparedStatement) {
            log.debug("Using a PreparedStatement instead of a Statement because {} was set to true", JDBC_READ_PREPARED_STATEMENT_PROPERTY_NAME);
        }
        statementRead = usePreparedStatement ?
                connection.prepareStatement(queryRead) :
                connection.createStatement();

        statementRead.setFetchSize(fetchSize);

        if (queryTimeout != null) {
            log.debug("Setting query timeout to {} seconds", queryTimeout);
            statementRead.setQueryTimeout(queryTimeout);
        }

        resultSetRead = usePreparedStatement ?
                ((PreparedStatement) statementRead).executeQuery() :
                statementRead.executeQuery(queryRead);

        return true;
    }

    private String buildSelectQuery(Connection connection, DatabaseDialect dialect) throws SQLException {
        SQLQueryBuilder sqlQueryBuilder = new SQLQueryBuilder(context, connection.getMetaData(), getQueryText(), dialect);

        // Build SELECT query
        if (quoteColumns == null) {
            sqlQueryBuilder.autoSetQuoteString();
        } else if (quoteColumns) {
            sqlQueryBuilder.forceSetQuoteString();
        }

        if (wrapDateWithTime) {
            sqlQueryBuilder.setWrapDateWithTime(true);
        }

        // Read variables
        return sqlQueryBuilder.buildSelectQuery();
    }

    /**
     * readNextObject() implementation
     * Retreive the next tuple from resultSet and return it
     *
     * @return row
     * @throws SQLException if a problem in resultSet occurs
     */
    @Override
    public OneRow readNextObject() throws SQLException {
        if (isCanceled) {
            String message = "The read operation was canceled";
            LOG.warn(message);
            throw new PxfRuntimeException(message);
        }

        if (resultSetRead.next()) {
            return new JdbcOneRow(resultSetRead, dialect);
        }
        return null;
    }

    /**
     * closeForRead() implementation
     */
    @Override
    public void closeForRead() throws SQLException {
        closeStatementAndConnection(statementRead);
    }

    /**
     * Cancel read operation.
     */
    @Override
    public void cancelRead() {
        // We don't need to close statement and connection here because closeForRead() will be invoked anyway
        // Sometimes resultSetRead.next() has unpredictable behaviour while closing statement and connection
        isCanceled = true;
    }

    /**
     * openForWrite() implementation
     * Create query template and open JDBC connection
     *
     * @return true if successful
     * @throws SQLException if a database access error occurs
     */
    @Override
    public boolean openForWrite() throws SQLException {
        log.debug("Accessor starts openForWrite()");
        if (queryName != null) {
            throw new IllegalArgumentException("specifying query name in data path is not supported for JDBC writable external tables");
        }

        Connection connection = getConnection();
        log.debug("Accessor got connection {}", connection);
        dialect = getDatabaseDialect(connection.getMetaData().getDatabaseProductName());
        String queryWrite = buildInsertQuery(connection, dialect);
        log.debug("Insert query: {}", queryWrite);

        // Process batchSize
        if (!connection.getMetaData().supportsBatchUpdates()) {
            if ((batchSizeIsSetByUser) && (batchSize > 1)) {
                throw new SQLException("The external database does not support batch updates");
            } else {
                batchSize = 1;
            }
        }
        writer = JdbcWriter.fromProps(
                JdbcWriterProperties.builder()
                        .terminationTimeoutSeconds(JdbcWriter.TERMINATION_TIMEOUT)
                        .batchTimeout(batchTimeout)
                        .batchSize(batchSize)
                        .poolSize(poolSize)
                        .query(queryWrite)
                        .plugin(this)
                        .databaseDialect(dialect)
                        .build()
        );
        closeConnection(connection);
        return true;
    }

    private String buildInsertQuery(Connection connection, DatabaseDialect dialect) throws SQLException {
        SQLQueryBuilder sqlQueryBuilder = new SQLQueryBuilder(context, connection.getMetaData(), null, dialect);

        // Build INSERT query
        if (quoteColumns == null) {
            sqlQueryBuilder.autoSetQuoteString();
        } else if (quoteColumns) {
            sqlQueryBuilder.forceSetQuoteString();
        }
        // Write variables
        return sqlQueryBuilder.buildInsertQuery();
    }

    /**
     * writeNextObject() implementation
     * <p>
     * If batchSize is not 0 or 1, add a tuple to the batch of statementWrite
     * Otherwise, execute an INSERT query immediately
     * <p>
     * In both cases, a {@link java.sql.PreparedStatement} is used
     *
     * @param row one row
     * @return true if successful
     * @throws SQLException           if a database access error occurs
     * @throws IOException            if the data provided by {@link JdbcResolver} is corrupted
     * @throws ClassNotFoundException if pooling is used and the JDBC driver was not found
     * @throws IllegalStateException  if writerCallableFactory was not properly initialized
     * @throws Exception              if it happens in writerCallable.call()
     */
    @Override
    public boolean writeNextObject(OneRow row) throws Exception {
        return writer.write(row);
    }

    /**
     * closeForWrite() implementation
     *
     * @throws Exception if it happens in writerCallable.call() or due to runtime errors in thread pool
     */
    @Override
    public void closeForWrite() throws Exception {
        writer.close();
    }

    /**
     * Cancel write operation.
     */
    @Override
    public void cancelWrite() {
        LOG.debug("Accessor starts cancelWrite()");
        if (writer != null) {
            writer.cancelWrite();
        } else {
            log.debug("Accessor didn't find any write operations to cancel");
        }
    }

    /**
     * Gets the text of the query by reading the file from the server configuration directory. The name of the file
     * is expected to be the same as the name of the query provided by the user and have extension ".sql"
     *
     * @return text of the query
     */
    private String getQueryText() {
        if (StringUtils.isBlank(queryName)) {
            return null;
        }
        // read the contents of the file holding the text of the query with a given name
        String serverDirectory = context.getConfiguration().get(ConfigurationFactory.PXF_CONFIG_SERVER_DIRECTORY_PROPERTY);
        if (StringUtils.isBlank(serverDirectory)) {
            throw new IllegalStateException("No server configuration directory found for server " + context.getServerName());
        }

        String queryText;
        try {
            File queryFile = new File(serverDirectory, queryName + ".sql");
            log.debug("Reading text of query={} from {}", queryName, queryFile.getCanonicalPath());
            queryText = FileUtils.readFileToString(queryFile, Charset.defaultCharset());
        } catch (IOException e) {
            throw new RuntimeException(String.format("Failed to read text of query %s : %s", queryName, e.getMessage()), e);
        }
        if (StringUtils.isBlank(queryText)) {
            throw new RuntimeException(String.format("Query text file is empty for query %s", queryName));
        }

        // Remove one or more semicolons followed by optional blank space
        // happening at the end of the query
        queryText = queryText.replaceFirst("(;+\\s*)+$", "");

        return queryText;
    }

    private boolean parseJdbcUsePreparedStatementProperty() {
        return Utilities.parseBooleanProperty(configuration, JDBC_READ_PREPARED_STATEMENT_PROPERTY_NAME, false);
    }

    private DatabaseDialect getDatabaseDialect(String productName) {
        return dialectProvider.get(productName, treatUnknownDbmsAsPostgreSql);
    }
}
