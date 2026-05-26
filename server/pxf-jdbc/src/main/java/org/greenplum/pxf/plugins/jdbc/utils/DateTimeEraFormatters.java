package org.greenplum.pxf.plugins.jdbc.utils;

import lombok.experimental.UtilityClass;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

import static java.time.format.DateTimeFormatter.ISO_LOCAL_TIME;
import static java.time.format.DateTimeFormatter.ISO_OFFSET_TIME;

@UtilityClass
public class DateTimeEraFormatters {

    /**
     * Signifies the ERA
     * Examples: " AD"; " BC"
     */
    public final static DateTimeFormatter ERA_FORMATTER = new DateTimeFormatterBuilder()
            .appendLiteral(" ")
            .appendText(ChronoField.ERA, TextStyle.SHORT)
            .toFormatter();

    /**
     * Signifies the Time Zone
     * +H:mm:ss - hour, with minute if non-zero or with minute and second if non-zero, with colon
     * Examples: "-5", "+03", "-03:30", "+04:15"
     */
    public final static DateTimeFormatter TIME_ZONE_FORMATTER = new DateTimeFormatterBuilder()
            .appendOffset("+H:mm","+00:00")
            .toFormatter();

    /**
     * Used to parse String to LocalDateTime.
     * Examples: "1980-08-10 17:10:20" -> 1980-08-10T17:10:20; "123456-10-19 11:12:13" -> +123456-10-19T11:12:13;
     * "1234-10-19 10:11:15.456 BC" -> -1233-10-19T10:11:15.456
     */
    public static final DateTimeFormatter LOCAL_DATE_TIME_PARSE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 1, 9, SignStyle.NORMAL).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 1, 2, SignStyle.NORMAL).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 1, 2, SignStyle.NORMAL).appendLiteral(" ")
            .append(ISO_LOCAL_TIME)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to parse String without delimiters to LocalDateTime.
     * Examples: "19800810T171020" -> 1980-08-10T17:10:20; "1234561019T111213" -> +123456-10-19T11:12:13;
     * "12341019T101115 BC" -> -1233-10-19T10:11:15
     */
    public static final DateTimeFormatter LOCAL_DATE_TIME_WITHOUT_DELIMITERS_PARSE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 1, 9, SignStyle.NORMAL)
            .appendValue(ChronoField.MONTH_OF_YEAR, 2)
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendLiteral('T')
            .appendValue(ChronoField.HOUR_OF_DAY, 2)
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to parse String to LocalDate.
     * Examples: "1977-12-11" -> 1977-12-11; "456789-12-11" -> +456789-12-11; "0010-12-11 BC" -> -0009-12-11
     */
    public static final DateTimeFormatter LOCAL_DATE_PARSE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 1, 9, SignStyle.NORMAL).appendLiteral('-')
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to format OffsetDateTime to String.
     * Examples: 1956-02-01T07:15:16Z -> "1956-02-01 07:15:16Z AD"; +12345-02-01T10:15:16Z -> "12345-02-01 10:15:16Z AD";
     * -1999-02-01T04:15:16Z -> "2000-02-01 04:15:16Z BC"
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 4, 9, SignStyle.NORMAL).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral(" ")
            .append(ISO_OFFSET_TIME)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to format String to OffsetDateTime.
     * Examples: "2024-11-13 21:01:02.95+3" -> "2024-11-13T21:01:02.95+03:00"; "2015-10-11 15:00:00.9+05" -> "2015-10-11T15:00:00.9+05:00";
     * "2015-10-11 15:00:00.9-03:30" -> "2015-10-11T15:00:00.9-03:30"; "2018-04-03 18:10:23.956789+00" -> "2018-04-03T18:10:23.956789Z"
     */
    public static final DateTimeFormatter OFFSET_DATE_TIME_WITH_TIME_ZONE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 4, 9, SignStyle.NORMAL).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral(" ")
            .append(ISO_LOCAL_TIME)
            .append(TIME_ZONE_FORMATTER)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to format LocalDateTime to String.
     * Examples: 2018-10-19T10:11 -> "2018-10-19 10:11:00 AD"; +123456-10-19T11:12:13 -> "123456-10-19 11:12:13 AD";
     * -1233-10-19T10:11:15.456 -> "1234-10-19 10:11:15.456 BC"
     */
    public static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 4, 9, SignStyle.NORMAL).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2).appendLiteral(" ")
            .append(ISO_LOCAL_TIME)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);
    /**
     * Used to format LocalDate to String.
     * Examples: 2023-01-10 -> "2023-01-10 AD"; +12345-02-01 -> "12345-02-01 AD"; -0009-12-11 -> "0010-12-11 BC"
     */
    public static final DateTimeFormatter LOCAL_DATE_FORMATTER = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.YEAR_OF_ERA, 4, 9, SignStyle.NEVER).appendLiteral("-")
            .appendValue(ChronoField.MONTH_OF_YEAR, 2).appendLiteral('-')
            .appendValue(ChronoField.DAY_OF_MONTH, 2)
            .appendOptional(ERA_FORMATTER)
            .toFormatter()
            .withLocale(Locale.ROOT);

    /**
     * Convert a string to LocalDate class with formatter
     *
     * @param rawVal the LocalDate in a string format
     * @return LocalDate
     */
    public static LocalDate getLocalDate(String rawVal) {
        try {
            return LocalDate.parse(rawVal, LOCAL_DATE_PARSE_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert date '" + rawVal + "' to LocalDate class: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a string to LocalDateTime class with formatter
     *
     * @param rawVal the LocalDateTime in a string format
     * @return LocalDateTime
     */
    public static LocalDateTime getLocalDateTime(String rawVal) {
        try {
            return LocalDateTime.parse(rawVal, LOCAL_DATE_TIME_PARSE_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert timestamp '" + rawVal + "' to the LocalDateTime class: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a string to LocalDateTime class with formatter
     *
     * @param rawVal the LocalDateTime in a string format without delimiters yyyyMMddTHHmmss
     * @return LocalDateTime
     */
    public static LocalDateTime getLocalDateTimeFromStringWithoutDelimiters(String rawVal) {
        try {
            return LocalDateTime.parse(rawVal, LOCAL_DATE_TIME_WITHOUT_DELIMITERS_PARSE_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert timestamp '" + rawVal + "' to the LocalDateTime class: " + e.getMessage(), e);
        }
    }

    /**
     * Convert a string to OffsetDateTime class with formatter
     *
     * @param rawVal the OffsetDateTime in a string format
     * @return OffsetDateTime
     */
    public static OffsetDateTime getOffsetDateTime(String rawVal) {
        try {
            return OffsetDateTime.parse(rawVal, OFFSET_DATE_TIME_WITH_TIME_ZONE_FORMATTER);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to convert value '" + rawVal + "' to the OffsetDateTime class: " + e.getMessage(), e);
        }
    }
}
