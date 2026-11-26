package io.arenadata.pxf.plugins.iceberg.filtering;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Converts values produced by {@code IcebergConverter#convertFromGreengageToIceberg(Object)}
 * into objects accepted by {@code org.apache.iceberg.expressions.Literals#from(Object)}.
 * <p>
 * Iceberg expression literals support only a limited set of Java types
 * (Boolean, Integer, Long, Float, Double, CharSequence, UUID, byte[], ByteBuffer, BigDecimal).
 * Temporal java.time values must be converted to the Iceberg internal representation
 * that is used in manifests and partition data, otherwise {@code Expressions.*}
 * throw an IllegalArgumentException and the whole WHERE clause is dropped from pushdown:
 * <ul>
 *     <li>{@link LocalDate} - epoch day as Integer for Iceberg date</li>
 *     <li>{@link LocalTime} - microseconds from midnight as Long for Iceberg time</li>
 *     <li>{@link LocalDateTime} - microseconds from epoch (as if UTC) as Long for Iceberg timestamp without zone</li>
 *     <li>{@link OffsetDateTime} - microseconds from epoch as Long for Iceberg timestamp with zone</li>
 * </ul>
 */
final class IcebergLiteralConverter {

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;

    private IcebergLiteralConverter() {
    }

    /**
     * @param value value converted by {@code IcebergConverters}
     * @return the same value for all Iceberg literal compatible types,
     *         or the Iceberg internal representation for temporal values
     */
    static Object convert(Object value) {
        if (value instanceof LocalDate date) {
            return Math.toIntExact(date.toEpochDay());
        }
        if (value instanceof LocalTime time) {
            return time.toNanoOfDay() / NANOS_PER_MICRO;
        }
        if (value instanceof LocalDateTime timestamp) {
            return toMicrosFromEpoch(timestamp.toInstant(ZoneOffset.UTC));
        }
        if (value instanceof OffsetDateTime timestamp) {
            return toMicrosFromEpoch(timestamp.toInstant());
        }
        return value;
    }

    private static long toMicrosFromEpoch(Instant instant) {
        return Math.addExact(
                Math.multiplyExact(instant.getEpochSecond(), MICROS_PER_SECOND),
                instant.getNano() / NANOS_PER_MICRO
        );
    }
}
