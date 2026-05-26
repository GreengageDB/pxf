package org.greenplum.pxf.plugins.jdbc.dialect;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;

import static org.greenplum.pxf.plugins.jdbc.utils.DateTimeEraFormatters.*;

@Component
public class SqlServerDatabaseDialect implements DatabaseDialect {
    /**
     * Used to format String to OffsetDateTime.
     * Examples: "2024-11-13 21:01:02.95+3" -> "2024-11-13T21:01:02.95+03:00"; "2015-10-11 15:00:00.9+05" -> "2015-10-11T15:00:00.9+05:00";
     * "2015-10-11 15:00:00.9-03:30" -> "2015-10-11T15:00:00.9-03:30"; "2018-04-03 18:10:23.956789+00" -> "2018-04-03T18:10:23.956789+00:00"
     * The method OffsetDateTime#toString() doesn't work well because hides seconds if seconds and nanoseconds are 0.
     */
    public static final DateTimeFormatter MSSQL_OFFSET_DATE_TIME_FORMATTER =
            new DateTimeFormatterBuilder()
                    .appendPattern("yyyy-MM-dd'T'HH:mm:ss")
                    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 7, true)
                    .appendOffset("+HH:MM", "+00:00")
                    .toFormatter(Locale.ROOT);

    @Override
    public String getName() {
        return "MICROSOFT";
    }

    @Override
    public String buildSessionQuery(String key, String value) {
        return String.format("SET %s %s", key, value);
    }

    @Override
    public String wrapTimestampWithTZ(String val) {
        try {
            String valStr = OffsetDateTime.parse(val, OFFSET_DATE_TIME_WITH_TIME_ZONE_FORMATTER)
                    .format(MSSQL_OFFSET_DATE_TIME_FORMATTER);
            return "CONVERT(DATETIMEOFFSET, '" + valStr + "')";
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format("The value '%s' cannot be converted to the Microsoft SQL Server 'DATETIMEOFFSET' type", val));
        }
    }
}
