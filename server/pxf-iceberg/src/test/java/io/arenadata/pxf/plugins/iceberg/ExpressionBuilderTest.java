package io.arenadata.pxf.plugins.iceberg;

import io.arenadata.pxf.plugins.iceberg.filtering.ExpressionBuilder;
import org.apache.iceberg.expressions.Expression;
import org.apache.iceberg.expressions.Expressions;
import org.apache.iceberg.types.Types;
import org.greenplum.pxf.api.utilities.ColumnDescriptor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.apache.iceberg.expressions.Expressions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ExpressionBuilderTest {

    private static Logger log = LoggerFactory.getLogger(ExpressionBuilderTest.class);

    private ExpressionBuilder builder = ExpressionBuilder.create(
        List.of(
            new ColumnDescriptor("id", 23, 0, "int4", null, true),
            new ColumnDescriptor("first_name", 25, 1, "text", null, true),
            new ColumnDescriptor("last_name", 25, 2, "text", null, true),
            new ColumnDescriptor("age", 23, 3, "int4", null, true)
        ), Map.of(
            "id", Types.IntegerType.get(),
            "first_name", Types.StringType.get(),
            "last_name", Types.StringType.get(),
            "age", Types.IntegerType.get()
        ));

    private ExpressionBuilder temporalBuilder = ExpressionBuilder.create(
        List.of(
            new ColumnDescriptor("last_transaction_timestamp", 1184, 0, "timestamptz", null, true),
            new ColumnDescriptor("created_at", 1114, 1, "timestamp", null, true),
            new ColumnDescriptor("order_date", 1082, 2, "date", null, true),
            new ColumnDescriptor("order_time", 1083, 3, "time", null, true)
        ), Map.of(
            "last_transaction_timestamp", Types.TimestampType.withZone(),
            "created_at", Types.TimestampType.withoutZone(),
            "order_date", Types.DateType.get(),
            "order_time", Types.TimeType.get()
        ));

    @ParameterizedTest
    @MethodSource("getTestData")
    public void checkOperatorSupport(String filterString, Expression expectedExpression) {
        log.info("Checking {} for {}", filterString, expectedExpression);
        var expression = builder.build(filterString);
        assertEquals(expression.toString(), expectedExpression.toString());
    }

    @ParameterizedTest
    @MethodSource("getTemporalTestData")
    public void checkTemporalFilterPushdown(String filterString, Expression expectedExpression) {
        log.info("Checking temporal filter {} for {}", filterString, expectedExpression);
        var expression = temporalBuilder.build(filterString);
        assertEquals(expectedExpression.toString(), expression.toString());
    }

    static List<Object[]> getTestData() {
        return List.of(
            new Object[] {"a0c23s1d1o2", greaterThan("id", 1) },
            new Object[] {"a0c23s1d2o2a1c25s6dfirst3o5l0", and(greaterThan("id", 2), equal("first_name", "first3")) },
            new Object[] {"a1o8", Expressions.isNull("first_name") },
            new Object[] {"a1o8l2", Expressions.not(Expressions.isNull("first_name")) },
            new Object[] {"a0m1007s1d1s1d2o10l2", Expressions.not(Expressions.in("id", List.of(1, 2))) },
            new Object[] {"a0m1007s1d1s1d2o10a3c23s2d26o2l0", and(in("id", List.of(1, 2)), greaterThan("age", 26)) }
        );
    }

    static List<Object[]> getTemporalTestData() {
        // '2026-11-10 00:00:00+03:00' == 2026-11-09T21:00:00Z, Iceberg timestamptz is micros from epoch
        long timestamptzMicros = Instant.parse("2026-11-09T21:00:00Z").toEpochMilli() * 1000L;
        // '2026-11-10 12:34:56.789' is stored by Iceberg timestamp as micros from epoch as if UTC
        long timestampMicros = Instant.parse("2026-11-10T12:34:56.789Z").toEpochMilli() * 1000L;
        return List.of(
            // WHERE last_transaction_timestamp >= '2026-11-10 00:00:00+03'
            new Object[] {"a0c1184s25d2026-11-10 00:00:00+03:00o4",
                greaterThanOrEqual("last_transaction_timestamp", timestamptzMicros)},
            // WHERE created_at = '2026-11-10 12:34:56.789'
            new Object[] {"a1c1114s23d2026-11-10 12:34:56.789o5",
                equal("created_at", timestampMicros)},
            // WHERE order_date < '2026-11-10', Iceberg date is days from epoch
            new Object[] {"a2c1082s10d2026-11-10o1",
                lessThan("order_date", (int) LocalDate.of(2026, 11, 10).toEpochDay())},
            // WHERE order_time IN ('00:00:01', '12:34:56.5'), Iceberg time is micros from midnight
            // 1183 = _time (time array) as GP sends IN lists with the array oid
            new Object[] {"a3m1183s8d00:00:01s10d12:34:56.5o10",
                in("order_time", List.of(1_000_000L, 45_296_500_000L))},
            // WHERE last_transaction_timestamp NOT IN ('2026-11-10 00:00:00+03', '2026-11-11 00:00:00+03')
            new Object[] {"a0m1185s25d2026-11-10 00:00:00+03:00s25d2026-11-11 00:00:00+03:00o10l2",
                not(in("last_transaction_timestamp", List.of(
                        timestamptzMicros,
                        timestamptzMicros + 86_400_000_000L)))}
        );
    }

}
