package org.greenplum.pxf.plugins.jdbc.dialect;

import lombok.NonNull;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;
import static org.greenplum.pxf.api.GreenplumDateTime.DATETIME_FORMATTER;
import static org.greenplum.pxf.api.GreenplumDateTime.DATE_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.*;

public interface DatabaseDialect {

    String getName();

    /**
     * Wraps a given date value the way required by target database
     *
     * @param val {@link java.sql.Date} object to wrap
     * @return a string with a properly wrapped date object
     */
    default String wrapDate(String val) {
        return "'" + val + "'";
    }

    /**
     * Wraps a given date value the way required by target database
     *
     * @param val             {@link java.time.LocalDate} object to wrap
     * @param isDateWideRange flag which is used when the year might contain more than 4 digits
     * @return a string with a properly wrapped date object
     */
    default String wrapDate(@NonNull LocalDate val, boolean isDateWideRange) {
        return wrapDate(isDateWideRange ? val.format(ISO_LOCAL_DATE) : val.toString());
    }

    /**
     * Wraps a given date value to the date with time.
     * It might be used in some special cases.
     *
     * @param val {@link java.sql.Date} object to wrap
     * @return a string with a properly wrapped date object
     */
    default String wrapDateWithTime(String val) {
        return wrapTimestamp(val);
    }

    /**
     * Wraps a given timestamp value the way required by target database
     *
     * @param val {@link java.sql.Timestamp} object to wrap
     * @return a string with a properly wrapped timestamp object
     */
    default String wrapTimestamp(String val) {
        return "'" + val + "'";
    }

    /**
     * Wraps a given timestamp value the way required by target database
     *
     * @param val             {@link java.sql.Timestamp} object to wrap
     * @param isDateWideRange flag which is used when the year might contain more than 4 digits
     * @return a string with a properly wrapped timestamp object
     */
    default String wrapTimestamp(@NonNull LocalDateTime val, boolean isDateWideRange) {
        return wrapTimestamp(isDateWideRange ? val.format(ISO_LOCAL_DATE_TIME) :
                val.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    /**
     * Build a query to set session-level variables for target database
     *
     * @param key   variable name (key)
     * @param value variable value
     * @return a string with template SET query
     */
    default String buildSessionQuery(String key, String value) {
        return String.format("SET %s = %s", key, value);
    }

    /**
     * Wraps a given timestamp with time zone value the way required by target database
     *
     * @param val {@link java.sql.Types.TIME_WITH_TIMEZONE} object to wrap
     * @return a string with a properly wrapped timestamp object
     */
    default String wrapTimestampWithTZ(String val) {
        throw new UnsupportedOperationException("The database doesn't support pushdown of the `TIMESTAMP WITH TIME ZONE` data type");
    }

    default Object getDateValue(ResultSet result, String colName, boolean isDateWideRange) throws SQLException {
        if (isDateWideRange) {
            LocalDate localDate = result.getObject(colName, LocalDate.class);
            return localDate != null ? localDate.format(LOCAL_DATE_FORMATTER) : null;
        }
        Date date = result.getDate(colName);
        return date != null ? date.toLocalDate().format(DATE_FORMATTER) : null;
    }

    default Object getTimestampValue(ResultSet result, String colName, boolean isDateWideRange) throws SQLException {
        if (isDateWideRange) {
            LocalDateTime localDateTime = result.getObject(colName, LocalDateTime.class);
            return localDateTime != null ? localDateTime.format(LOCAL_DATE_TIME_FORMATTER) : null;
        }
        Timestamp timestamp = result.getTimestamp(colName);
        return timestamp != null ? timestamp.toLocalDateTime().format(DATETIME_FORMATTER) : null;
    }

    default Object getTimestampTZValue(ResultSet result, String colName) throws SQLException {
        OffsetDateTime offsetDateTime = result.getObject(colName, OffsetDateTime.class);
        return offsetDateTime != null ? offsetDateTime.format(OFFSET_DATE_TIME_FORMATTER) : null;
    }

    default void setTimestampTZValue(PreparedStatement statement, int index, OffsetDateTime offsetDateTime) throws SQLException {
        statement.setObject(index, offsetDateTime);
    }

    default Object getUUIDValue(ResultSet result, String colName) throws SQLException {
        return result.getObject(colName, java.util.UUID.class);
    }

    default void setJsonObject(PreparedStatement statement, int index, Object value) throws SQLException {
        statement.setObject(index, value);
    }
}
