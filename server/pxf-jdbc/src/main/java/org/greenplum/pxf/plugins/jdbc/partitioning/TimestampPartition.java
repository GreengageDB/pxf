package org.greenplum.pxf.plugins.jdbc.partitioning;

import lombok.Getter;
import org.greenplum.pxf.plugins.jdbc.dialect.DatabaseDialect;

import java.time.LocalDateTime;
import java.util.Objects;

@Getter
class TimestampPartition extends BaseRangePartition {

    private final LocalDateTime start;
    private final LocalDateTime end;
    private final boolean isDateWideRange;

    /**
     * Construct a TimestampPartition covering a range of values from 'start' to 'end'
     *
     * @param column the partitioned column
     * @param start  null for right-bounded interval
     * @param end    null for left-bounded interval
     */
    public TimestampPartition(String column, LocalDateTime start, LocalDateTime end) {
        this(column, start, end, false);
    }

    /**
     * Construct a TimestampPartition covering a range of values from 'start' to 'end'
     *
     * @param column          the partitioned column
     * @param start           null for right-bounded interval
     * @param end             null for left-bounded interval
     * @param isDateWideRange flag which is used when the year might contain more than 4 digits
     */
    public TimestampPartition(String column, LocalDateTime start, LocalDateTime end, boolean isDateWideRange) {
        super(column);
        if (start == null && end == null) {
            throw new RuntimeException("Both boundaries cannot be null");
        }
        if (Objects.equals(start, end)) {
            throw new RuntimeException(String.format(
                    "Boundaries cannot be equal for partition of type '%s'", PartitionType.TIMESTAMP
            ));
        }
        this.start = start;
        this.end = end;
        this.isDateWideRange = isDateWideRange;
    }

    @Override
    public String toSqlConstraint(String quoteString, DatabaseDialect dialect) {
        if (dialect == null) {
            throw new RuntimeException(String.format(
                    "Partitioning by %s is not supported for this DB", PartitionType.TIMESTAMP
            ));
        }

        return generateRangeConstraint(
                getQuotedColumn(quoteString),
                convert(start, dialect),
                convert(end, dialect)
        );
    }

    private String convert(LocalDateTime value, DatabaseDialect dialect) {
        if (value == null) {
            return null;
        }
        return dialect.wrapTimestamp(value, isDateWideRange);
    }
}