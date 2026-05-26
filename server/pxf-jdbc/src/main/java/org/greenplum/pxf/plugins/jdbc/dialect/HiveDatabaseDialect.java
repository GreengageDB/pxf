package org.greenplum.pxf.plugins.jdbc.dialect;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hive.common.type.TimestampTZ;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

import static org.greenplum.pxf.api.GreenplumDateTime.DATETIME_FORMATTER;
import static org.greenplum.pxf.api.GreenplumDateTime.DATE_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.OFFSET_DATE_TIME_FORMATTER;
import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.OFFSET_DATE_TIME_WITH_TIME_ZONE_FORMATTER;

@Component
@Slf4j
public class HiveDatabaseDialect implements DatabaseDialect {
    /**
     * Used to format String to OffsetDateTime.
     * Examples: "2024-11-13 21:01:02.95+3" -> "2024-11-13 21:01:02.95+03:00"; "2015-10-11 15:00:00.9+05" -> "2015-10-11 15:00:00.9+05:00";
     * "2015-10-11 15:00:00.9-03:30" -> "2015-10-11 15:00:00.9-03:30"; "2018-04-03 18:10:23.956789+00" -> "2018-04-03 18:10:23.956789+00:00"
     */
    public static final DateTimeFormatter HIVE_OFFSET_DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd' 'HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 7, true)
                    .appendOffset("+HH:MM", "+00:00")
                    .toFormatter(Locale.ROOT);

    @Override
    public String getName() {
        return "APACHE HIVE";
    }

    @Override
    public String wrapTimestampWithTZ(String val) {
        String valStr = OffsetDateTime.parse(val, OFFSET_DATE_TIME_WITH_TIME_ZONE_FORMATTER)
                .format(HIVE_OFFSET_DATE_TIME_FORMATTER);
        return "TIMESTAMPLOCALTZ'" + valStr + "'";
    }

    @Override
    public Object getDateValue(ResultSet result, String colName, boolean isDateWideRange) throws SQLException {
        Date date = result.getDate(colName);
        return date != null ? date.toLocalDate().format(DATE_FORMATTER) : null;
    }

    @Override
    public Object getTimestampValue(ResultSet result, String colName, boolean isDateWideRange) throws SQLException {
        Timestamp timestamp = result.getTimestamp(colName);
        return timestamp != null ? timestamp.toLocalDateTime().format(DATETIME_FORMATTER) : null;
    }

    @Override
    public Object getTimestampTZValue(ResultSet result, String colName) throws SQLException {
        TimestampTZ timestampTZ = (TimestampTZ) result.getObject(colName);
        if (timestampTZ == null) {
            return null;
        }
        OffsetDateTime offsetDateTime = timestampTZ
                .getZonedDateTime()
                .toOffsetDateTime();
        return offsetDateTime.format(OFFSET_DATE_TIME_FORMATTER);
    }

    @Override
    public void setTimestampTZValue(PreparedStatement statement, int index, OffsetDateTime offsetDateTime) throws SQLException {
        String tstz = offsetDateTime.format(HIVE_OFFSET_DATE_TIME_FORMATTER);
        statement.setString(index, tstz);
    }
}
