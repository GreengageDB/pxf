package org.greenplum.pxf.service;

import io.micrometer.core.instrument.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.utilities.ThrowingSupplier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Service responsible for submitting metrics to MeterRegistry.
 */
@Component
@Slf4j
public class MetricsReporter {

    private static final String UNKNOWN_VALUE = "unknown";
    private static final Tags SUCCESS_TAG = Tags.of("outcome", "success");
    private static final Tags ERROR_TAG = Tags.of("outcome", "error");

    private final MeterRegistry registry;
    private final Environment env;

    /**
     * Creates a new instance.
     *
     * @param registry meter registry to submit metrics to
     * @param env      environment which serves as the source of property values
     */
    public MetricsReporter(MeterRegistry registry, Environment env) {
        this.registry = registry;
        this.env = env;
    }

    /**
     * Reports timer metric with a given name and duration to the registry. Applies custom tags before reporting.
     *
     * @param metric   metric to apply
     * @param duration duration measured by the metric
     * @param context  request context
     */
    public void reportTimer(PxfMetric metric, Duration duration, RequestContext context) {
        reportTimer(metric, duration, context, null);
    }

    /**
     * Reports timer metric, duration and outcome of the timed operation to the registry.
     * Applies custom tags before reporting and adds an outcome tag.
     *
     * @param metric   metric to apply
     * @param duration duration measured by the metric
     * @param context  request context
     * @param success  true if timed operation was successful, false otherwise
     */
    public void reportTimer(PxfMetric metric, Duration duration, RequestContext context, boolean success) {
        reportTimer(metric, duration, context, success ? SUCCESS_TAG : ERROR_TAG);
    }

    /**
     * Reports long task timer metric.
     *
     * @param metric    metric to apply
     * @param context   request context
     * @param supplier  code to run
     */
    public <T> T longTaskTimer(PxfMetric metric, RequestContext context, Tags extraTags, ThrowingSupplier<T, Exception> supplier) {
        if(!isMetricAvailable(metric)) {
            return supplier.getWithoutException();
        }
        Tags tags = (extraTags == null) ? getTags(context) : getTags(context).and(extraTags);
        try {
            LongTaskTimer timer = registry.more().longTaskTimer(metric.metricName, tags);
            return timer.record(supplier::getWithoutException);
        } catch (Exception e) {
            log.warn("Unable to report timer {} {}.", metric.metricName, tags);
            throw e;
        }
    }

    /**
     * Reports timer metric with a given name, duration and additional tags to the registry.
     * Applies custom tags before reporting and adds a success outcome tag.
     *
     * @param metric - metric
     * @param duration - duration
     * @param context - request context
     * @param extraTags - tags
     */
    private void reportTimer(PxfMetric metric, Duration duration, RequestContext context, Tags extraTags) {
        String metricName = metric.getMetricName();
        long durationMs = duration.toMillis();
        if(!isMetricAvailable(metric)) {
            log.trace("Skipping reporting metric {} with duration={}ms", metricName, durationMs);
            return;
        }
        Tags tags = (extraTags == null) ? getTags(context) : getTags(context).and(extraTags);
        try {
            Timer timer = Timer.builder(metricName).tags(tags).register(registry);
            timer.record(duration);
            if (log.isTraceEnabled()) {
                log.trace("Reported timer {}{} with duration={}ms", metricName, tags, durationMs);
            }
        } catch (Exception e) {
            log.warn(String.format("Unable to report timer %s%s with duration=%dms.", metricName, tags, durationMs), e);
        }
    }

    private boolean isMetricAvailable(PxfMetric metric) {
        return env.getProperty(metric.getEnabledPropertyName(), Boolean.class, Boolean.FALSE);
    }

    public <T> T reportTimer(MetricsReporter.PxfMetric metric, RequestContext context, ThrowingSupplier<T, Exception> supplier) throws Exception {
        Instant start = Instant.now();
        T result;
        boolean success = false;
        try {
            result = supplier.get();
            success = true;
        } finally {
            reportTimer(metric, Duration.between(start, Instant.now()), context, success);
        }
        return result;
    }


    /**
     * Reports counter metric with a given name and the increment to the registry.
     * Reports with any tags given by the context and adds a success outcome tag.
     *
     * @param metric - the metric
     * @param increment - how much to increment
     * @param context - the request context
     */
    public void reportCounter(PxfMetric metric, long increment, RequestContext context) {
        String metricName = metric.getMetricName();
        if (!env.getProperty(metric.getEnabledPropertyName(), Boolean.class, Boolean.FALSE)) {
            log.trace("Skipping reporting metric {} with increment={}", metricName, increment);
            return;
        }
        Tags tags = getTags(context);
        try {
            double incrementCount = Long.valueOf(increment).doubleValue();
            Counter counter = Counter.builder(metricName).tags(tags).register(registry);
            counter.increment(incrementCount);
            if (log.isTraceEnabled()) {
                log.trace("Reported counter {}{} with increment={}", metricName, tags, increment);
            }
        } catch (Exception e) {
            log.warn(String.format("Unable to report counter %s%s with increment=%d.", metricName, tags, increment), e);
        }
    }

    /**
     * Pulls the value for reporting frequency for the given metric from the environment.
     * If no value found, the default reporting frequency is 1000.
     */
    public long getReportFrequency() {
        long reportFrequency = env.getProperty("pxf.metrics.report-frequency", Long.class, 1000L);
        if (reportFrequency < 0) {
            log.warn("Tried to set reporting frequency to {}. We do not support negative numbers, setting to 0.", reportFrequency);
            reportFrequency = 0;
        }
        return reportFrequency;
    }

    /**
     * Produces a set of custom tags with values from the provided request context.
     *
     * @param context request context
     * @return collection of custom tags
     */
    private Tags getTags(RequestContext context) {
        return Tags.empty()
                .and("user", StringUtils.defaultIfBlank(context.getUser(), UNKNOWN_VALUE))
                .and("segment", String.valueOf(context.getSegmentId()))
                .and("profile", StringUtils.defaultIfBlank(context.getProfile(), UNKNOWN_VALUE))
                .and("server", StringUtils.defaultIfBlank(context.getServerName(), "default"))
                .and("datasource", StringUtils.defaultIfBlank(context.getDataSource(), UNKNOWN_VALUE));
    }

    /**
     * Enum that has information about all custom metrics for PXF.
     */
    @Getter
    public enum PxfMetric {
        FRAGMENTS_SENT("pxf.fragments.sent", "pxf.metrics.fragments.enabled"),
        RECORDS_SENT("pxf.records.sent", "pxf.metrics.records.enabled"),
        RECORDS_RECEIVED("pxf.records.received", "pxf.metrics.records.enabled"),
        BYTES_SENT("pxf.bytes.sent", "pxf.metrics.bytes.enabled"),
        BYTES_RECEIVED("pxf.bytes.received", "pxf.metrics.bytes.enabled"),
        OPERATION("pxf.operation", "pxf.metrics.operations.enabled"),
        FRAGMENTER_CALL("pxf.fragmenter.call", "pxf.metrics.fragmenter.enabled"),
        BRIDGE_BEGIN("pxf.bridge.begin", "pxf.metrics.bridge.enabled");

        private final String metricName;
        private final String enabledPropertyName;

        /**
         * Creates a new instance.
         *
         * @param metricName          name of the metric as will be seen by the registry
         * @param enabledPropertyName property name that manages whether reporting of this metric is turned on / off
         */
        PxfMetric(String metricName, String enabledPropertyName) {
            this.metricName = metricName;
            this.enabledPropertyName = enabledPropertyName;
        }

    }
}
