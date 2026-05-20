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
import org.greenplum.pxf.api.OneField;
import org.greenplum.pxf.api.OneRow;
import org.greenplum.pxf.api.io.DataType;
import org.greenplum.pxf.api.model.Resolver;
import org.greenplum.pxf.api.security.SecureLogin;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;
import org.greenplum.pxf.plugins.jdbc.utils.ConnectionManager;
import org.greenplum.pxf.plugins.jdbc.utils.DbProduct;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.greenplum.pxf.api.GreenplumDateTime.DATETIME_FORMATTER;
import static org.greenplum.pxf.api.GreenplumDateTime.DATE_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.LOCAL_DATE_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.LOCAL_DATE_TIME_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.OFFSET_DATE_TIME_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.getLocalDate;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.getLocalDateTime;

/**
 * JDBC tables resolver
 */
public class JdbcResolver extends JdbcBasePlugin implements Resolver {
    private static final Set<DataType> DATATYPES_SUPPORTED = EnumSet.of(
            DataType.VARCHAR,
            DataType.BPCHAR,
            DataType.TEXT,
            DataType.BYTEA,
            DataType.BOOLEAN,
            DataType.INTEGER,
            DataType.FLOAT8,
            DataType.REAL,
            DataType.BIGINT,
            DataType.SMALLINT,
            DataType.NUMERIC,
            DataType.TIMESTAMP,
            DataType.TIMESTAMP_WITH_TIME_ZONE,
            DataType.DATE,
            DataType.JSON,
            DataType.JSONB
    );

    /**
     * Creates a new instance of the JdbcResolver
     */
    public JdbcResolver() {
        super();
    }

    /**
     * Creates a new instance of the resolver with provided connection manager.
     *
     * @param connectionManager connection manager
     * @param secureLogin       the instance of the secure login
     */
    JdbcResolver(ConnectionManager connectionManager, SecureLogin secureLogin, DecryptClient decryptClient) {
        super(connectionManager, secureLogin, decryptClient);
    }

    /**
     * Pre-compiled patterns for timestamptz normalization.
     * Used to avoid recompiling regex on every method call.
     */
    private static final Pattern TZ_OFFSET_HH = Pattern.compile("([+-])(\\d{2})$");
    private static final Pattern TZ_OFFSET_HHMM = Pattern.compile("([+-])(\\d{2})(\\d{2})$");

    /**
     * Minimal normalization of PostgreSQL timestamptz string for OffsetDateTime.parse().
     * - Replaces space with 'T' between date and time
     * - Adds :00 to timezone offset if missing (e.g., +03 → +03:00)
     * - For strings with era markers (BC/AD), parsing is delegated to OFFSET_DATE_TIME_FORMATTER
     *
     * @param pgTimestamp the timestamp string from PostgreSQL
     * @return normalized ISO 8601 compatible string, or null if input is null
     */
    private static String normalizePostgresTimestamp(String pgTimestamp) {
        if (pgTimestamp == null) return null;

        var result = pgTimestamp.trim();

        // Replace first space (date/time separator) with 'T' for ISO 8601
        int spaceIdx = result.indexOf(' ');
        if (spaceIdx > 0) {
            result = result.substring(0, spaceIdx) + 'T' + result.substring(spaceIdx + 1);
        }

        // Normalize timezone offset at the end of the string using pre-compiled patterns
        // +HH or -HH → +HH:00 or -HH:00
        if (TZ_OFFSET_HH.matcher(result).find()) {
            result = TZ_OFFSET_HH.matcher(result).replaceAll("$1$2:00");
        }
        // +HHMM or -HHMM → +HH:MM or -HH:MM (rare, but handled for robustness)
        else if (TZ_OFFSET_HHMM.matcher(result).find()) {
            result = TZ_OFFSET_HHMM.matcher(result).replaceAll("$1$2:$3");
        }

        return result;
    }

    /**
     * getFields() implementation
     *
     * @param row one row
     * @throws SQLException if the provided {@link OneRow} object is invalid
     */
    @Override
    public List<OneField> getFields(OneRow row) throws SQLException {
        ResultSet result = (ResultSet) row.getData();
        LinkedList<OneField> fields = new LinkedList<>();

        for (ColumnDescriptor column : columns) {
            String colName = column.columnName();
            Object value;

            OneField oneField = new OneField();
            oneField.type = column.columnTypeCode();

            fields.add(oneField);

            /*
             * Non-projected columns get null values
             */
            if (!column.isProjected()) continue;

            switch (DataType.get(oneField.type)) {
                case INTEGER:
                    value = result.getInt(colName);
                    break;
                case FLOAT8:
                    value = result.getDouble(colName);
                    break;
                case REAL:
                    value = result.getFloat(colName);
                    break;
                case BIGINT:
                    value = result.getLong(colName);
                    break;
                case SMALLINT:
                    value = result.getShort(colName);
                    break;
                case BOOLEAN:
                    value = result.getBoolean(colName);
                    break;
                case BYTEA:
                    value = result.getBytes(colName);
                    break;
                case VARCHAR:
                case BPCHAR:
                case TEXT:
                case NUMERIC:
                case JSONB:
                case JSON:
                    value = result.getString(colName);
                    break;
                case DATE:
                    if (isDateWideRange) {
                        LocalDate localDate = result.getObject(colName, LocalDate.class);
                        value = localDate != null ? localDate.format(LOCAL_DATE_FORMATTER) : null;
                    } else {
                        Date date = result.getDate(colName);
                        value = date != null ? date.toLocalDate().format(DATE_FORMATTER) : null;
                    }
                    break;
                case TIMESTAMP:
                    if (isDateWideRange) {
                        LocalDateTime localDateTime = result.getObject(colName, LocalDateTime.class);
                        value = localDateTime != null ? localDateTime.format(LOCAL_DATE_TIME_FORMATTER) : null;
                    } else {
                        Timestamp timestamp = result.getTimestamp(colName);
                        value = timestamp != null ? timestamp.toLocalDateTime().format(DATETIME_FORMATTER) : null;
                    }
                    break;
                case TIMESTAMP_WITH_TIME_ZONE:
                    if (isDateWideRange) {
                        OffsetDateTime offsetDateTime = result.getObject(colName, OffsetDateTime.class);
                        value = offsetDateTime != null ? offsetDateTime.format(OFFSET_DATE_TIME_FORMATTER) : null;
                    } else {
                        throw new UnsupportedOperationException(
                                String.format("Field type '%s' (column '%s') is not supported. Try to use the property DATE_WIDE_RANGE=true",
                                        DataType.get(oneField.type),
                                        column));
                    }
                    break;
                case UUID:
                    value = result.getObject(colName, java.util.UUID.class);
                    break;
                default:
                    throw new UnsupportedOperationException(
                            String.format("Field type '%s' (column '%s') is not supported",
                                    DataType.get(oneField.type),
                                    column));
            }

            oneField.val = result.wasNull() ? null : value;
        }
        return fields;
    }

    /**
     * setFields() implementation
     *
     * @param record List of fields
     * @return OneRow with the data field containing a List of fields
     * OneFields are not reordered before being passed to Accessor; at the
     * moment, there is no way to correct the order of the fields if it is not.
     * In practice, the 'record' provided is always ordered the right way.
     * @throws UnsupportedOperationException if field of some type is not supported
     */
    @Override
    public OneRow setFields(List<OneField> record) throws UnsupportedOperationException {
        int columnIndex = 0;

        for (OneField oneField : record) {
            ColumnDescriptor column = columns.get(columnIndex++);

            DataType oneFieldType = DataType.get(oneField.type);
            DataType columnType = column.getDataType();

            if (!DATATYPES_SUPPORTED.contains(oneFieldType)) {
                throw new UnsupportedOperationException(
                        String.format("Field type '%s' (column '%s') is not supported",
                                oneFieldType, column));
            }

            // Convert TEXT columns into native data types
            if ((oneFieldType == DataType.TEXT) && (columnType != DataType.TEXT)) {
                oneField.type = columnType.getOID();

                if (oneField.val == null) {
                    continue;
                }

                String rawVal = (String) oneField.val;
                switch (columnType) {
                    case VARCHAR:
                    case BPCHAR:
                    case BYTEA:
                    case JSON:
                    case JSONB:
                        break;
                    case BOOLEAN:
                        oneField.val = Boolean.parseBoolean(rawVal);
                        break;
                    case INTEGER:
                        oneField.val = Integer.parseInt(rawVal);
                        break;
                    case FLOAT8:
                        oneField.val = Double.parseDouble(rawVal);
                        break;
                    case REAL:
                        oneField.val = Float.parseFloat(rawVal);
                        break;
                    case BIGINT:
                        oneField.val = Long.parseLong(rawVal);
                        break;
                    case SMALLINT:
                        oneField.val = Short.parseShort(rawVal);
                        break;
                    case NUMERIC:
                        oneField.val = new BigDecimal(rawVal);
                        break;
                    case TIMESTAMP:
                        if (isDateWideRange) {
                            oneField.val = getLocalDateTime(rawVal);
                        } else {
                            oneField.val = Timestamp.valueOf(rawVal);
                        }
                        break;
                    case TIMESTAMP_WITH_TIME_ZONE:
                        if (isDateWideRange) {
                            if (rawVal.contains(" BC") || rawVal.contains(" AD")) {
                                oneField.val = OFFSET_DATE_TIME_FORMATTER.parse(rawVal, OffsetDateTime::from);
                            } else {
                                String normalized = normalizePostgresTimestamp(rawVal);
                                oneField.val = OffsetDateTime.parse(normalized);
                            }
                        } else {
                            throw new UnsupportedOperationException(
                                    String.format("Column type '%s' (column '%s') is not supported. Try to use the property DATE_WIDE_RANGE=true",
                                            columnType, column));
                        }
                        break;
                    case DATE:
                        if (isDateWideRange) {
                            oneField.val = getLocalDate(rawVal);
                        } else {
                            oneField.val = Date.valueOf(rawVal);
                        }
                        break;
                    case UUID:
                        oneField.val = UUID.fromString(rawVal);
                        break;
                    default:
                        throw new UnsupportedOperationException(
                                String.format("Column type '%s' (column '%s') is not supported",
                                        columnType, column));
                }
            }
        }

        return new OneRow(record);
    }

    /**
     * Decode OneRow object and pass all its contents to a PreparedStatement
     *
     * @param row       one row
     * @param statement PreparedStatement
     * @throws IOException  if data in a OneRow is corrupted
     * @throws SQLException if the given statement is broken
     */
    @SuppressWarnings("unchecked")
    public static void decodeOneRowToPreparedStatement(OneRow row, PreparedStatement statement, DbProduct dbProduct) throws IOException, SQLException {
        // This is safe: OneRow comes from JdbcResolver
        List<OneField> tuple = (List<OneField>) row.getData();
        for (int i = 1; i <= tuple.size(); i++) {
            OneField field = tuple.get(i - 1);
            switch (DataType.get(field.type)) {
                case INTEGER:
                    if (field.val == null) {
                        statement.setNull(i, Types.INTEGER);
                    } else {
                        statement.setInt(i, (int) field.val);
                    }
                    break;
                case BIGINT:
                    if (field.val == null) {
                        statement.setNull(i, Types.INTEGER);
                    } else {
                        statement.setLong(i, (long) field.val);
                    }
                    break;
                case SMALLINT:
                    if (field.val == null) {
                        statement.setNull(i, Types.INTEGER);
                    } else {
                        statement.setShort(i, (short) field.val);
                    }
                    break;
                case REAL:
                    if (field.val == null) {
                        statement.setNull(i, Types.FLOAT);
                    } else {
                        statement.setFloat(i, (float) field.val);
                    }
                    break;
                case FLOAT8:
                    if (field.val == null) {
                        statement.setNull(i, Types.DOUBLE);
                    } else {
                        statement.setDouble(i, (double) field.val);
                    }
                    break;
                case BOOLEAN:
                    if (field.val == null) {
                        statement.setNull(i, Types.BOOLEAN);
                    } else {
                        statement.setBoolean(i, (boolean) field.val);
                    }
                    break;
                case NUMERIC:
                    if (field.val == null) {
                        statement.setNull(i, Types.NUMERIC);
                    } else {
                        statement.setBigDecimal(i, (BigDecimal) field.val);
                    }
                    break;
                case VARCHAR:
                case BPCHAR:
                case TEXT:
                    if (field.val == null) {
                        statement.setNull(i, Types.VARCHAR);
                    } else {
                        statement.setString(i, (String) field.val);
                    }
                    break;
                case BYTEA:
                    if (field.val == null) {
                        statement.setNull(i, Types.BINARY);
                    } else {
                        statement.setBytes(i, (byte[]) field.val);
                    }
                    break;
                case TIMESTAMP:
                    if (field.val == null) {
                        statement.setNull(i, Types.TIMESTAMP);
                    } else {
                        if (field.val instanceof LocalDateTime) {
                            statement.setObject(i, field.val);
                        } else {
                            statement.setTimestamp(i, (Timestamp) field.val);
                        }
                    }
                    break;
                case TIMESTAMP_WITH_TIME_ZONE:
                    if (field.val == null) {
                        statement.setNull(i, Types.TIMESTAMP_WITH_TIMEZONE);
                    } else {
                        if (field.val instanceof OffsetDateTime) {
                            statement.setObject(i, field.val);
                        } else if (field.val instanceof String) {
                            String normalized = normalizePostgresTimestamp((String) field.val);
                            OffsetDateTime odt = OffsetDateTime.parse(normalized);
                            statement.setObject(i, odt);
                        } else {
                            throw new IOException("Unexpected value type for TIMESTAMP_WITH_TIME_ZONE: " + field.val.getClass());
                        }
                    }
                    break;
                case DATE:
                    if (field.val == null) {
                        statement.setNull(i, Types.DATE);
                    } else {
                        if (field.val instanceof LocalDate) {
                            statement.setObject(i, field.val);
                        } else {
                            statement.setDate(i, (Date) field.val);
                        }
                    }
                    break;
                case UUID:
                    statement.setObject(i, field.val);
                    break;
                case JSON:
                case JSONB:
                    if (dbProduct == DbProduct.POSTGRES) {
                        statement.setObject(i, field.val, Types.OTHER);
                    } else {
                        statement.setObject(i, field.val);
                    }
                    break;
                default:
                    throw new IOException("The data tuple from JdbcResolver is corrupted");
            }
        }
    }
}