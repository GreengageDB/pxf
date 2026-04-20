package org.greenplum.pxf.service.controller;

import com.google.common.io.CountingOutputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.greenplum.pxf.api.io.Writable;
import org.greenplum.pxf.api.model.ConfigurationFactory;
import org.greenplum.pxf.api.model.Fragment;
import org.greenplum.pxf.api.model.PluginConf;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.api.utilities.Utilities;
import org.greenplum.pxf.service.FragmenterService;
import org.greenplum.pxf.service.MetricsReporter;
import org.greenplum.pxf.service.bridge.Bridge;
import org.greenplum.pxf.service.bridge.BridgeFactory;
import org.greenplum.pxf.service.security.SecurityService;
import org.greenplum.pxf.service.utilities.ThrowingSupplier;
import org.springframework.stereotype.Service;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Implementation of the ReadService.
 */
@Service
@Slf4j
public class ReadServiceImpl extends BaseServiceImpl<OperationStats> implements ReadService {
    private final Map<RequestIdentifier, Bridge> readExecutionMap = new ConcurrentHashMap<>();

    private final FragmenterService fragmenterService;

    /**
     * Creates a new instance.
     *
     * @param configurationFactory configuration factory
     * @param bridgeFactory        bridge factory
     * @param securityService      security service
     * @param fragmenterService    fragmenter service
     * @param metricsReporter      metrics reporter service
     */
    public ReadServiceImpl(ConfigurationFactory configurationFactory,
                           BridgeFactory bridgeFactory,
                           SecurityService securityService,
                           FragmenterService fragmenterService,
                           MetricsReporter metricsReporter) {
        super("Read", configurationFactory, bridgeFactory, securityService, metricsReporter);
        this.fragmenterService = fragmenterService;
    }

    @Override
    public void readData(RequestContext context, OutputStream outputStream) {
        // wrapping the invocation of processData(..) with the error reporting logic
        // since any exception thrown from it must be logged, as this method is called asynchronously
        // and is the last opportunity to log the exception while having MDC logging context defined
        invokeWithErrorHandling(() -> processData(context, OperationStats.Operation.READ, () -> writeStream(context, outputStream)));
    }

    @Override
    public boolean cancelRead(RequestContext context) {
        RequestIdentifier requestIdentifier = new RequestIdentifier(context);
        Bridge bridge = readExecutionMap.remove(requestIdentifier);
        return cancelExecution(requestIdentifier, bridge);
    }

    @Override
    public void cancelReadExecutions(String profile, String server) {
        Predicate<RequestIdentifier> identifierFilter = getIdentifierFilter(profile, server);
        readExecutionMap.forEach((requestIdentifier, bridge) -> {
            if (identifierFilter.test(requestIdentifier)) {
                cancelExecution(requestIdentifier, bridge);
            }
        });
    }

    private Predicate<RequestIdentifier> getIdentifierFilter(String profile, String server) {
        return key -> (StringUtils.isBlank(profile) || key.getProfile().equals(profile))
                && (StringUtils.isBlank(server) || key.getServer().equals(server));
    }

    /**
     * Calls Fragmenter service to get a list of fragments for the resource, then reads records for each fragment
     * and writes them to the output stream. Maintains the statistics about the progress of the query and reports
     * it to the caller even if the operation failed or aborted.
     *
     * @param context      request context
     * @param outputStream output stream
     * @return operation statistics
     */
    private OperationResult writeStream(RequestContext context, OutputStream outputStream) {
        boolean restoreOriginalValues;

        String originalProfile = context.getProfile();
        String originalAccessor = context.getAccessor();
        String originalResolver = context.getResolver();
        String originalProfileScheme = context.getProfileScheme();

        OperationStats queryStats = new OperationStats(OperationStats.Operation.READ, metricsReporter, context);
        OperationResult queryResult = new OperationResult();

        // dataStream (and outputStream as the result) will close automatically at the end of the try block
        CountingOutputStream countingOutputStream = new CountingOutputStream(outputStream);
        String sourceName = null;
        try {
            List<Fragment> fragments = metricsReporter.reportTimer(MetricsReporter.PxfMetric.FRAGMENTER_CALL, context,
                    () -> fragmenterService.getFragmentsForSegment(context) );
            for (int i = 0; i < fragments.size(); i++) {
                Fragment fragment = fragments.get(i);
                sourceName = fragment.getSourceName();
                String profile = fragment.getProfile();
                restoreOriginalValues = false;
                if (StringUtils.isNotBlank(profile) &&
                        !StringUtils.equalsIgnoreCase(profile, context.getProfile())) {
                    restoreOriginalValues = true;
                    log.debug("Fragment {} of resource {} will be using profile: {}",
                            fragment.getIndex(), fragment.getSourceName(), profile);
                    updateProfile(context, profile);
                }
                context.setDataSource(fragment.getSourceName());
                context.setFragmentIndex(fragment.getIndex());
                context.setFragmentMetadata(fragment.getMetadata());
                metricsReporter.longTaskTimer(MetricsReporter.PxfMetric.FRAGMENTS_SENT, context, null, (ThrowingSupplier<Void, Exception>) () -> {
                    processFragment(countingOutputStream, context, queryStats);
                    return null;
                });

                // In cases where we have hundreds of thousands of fragments,
                // we want to release the fragment reference as soon as we are
                // done processing the fragment. This allows the GC to reclaim
                // any memory, under memory stress situations, if needed.
                fragments.set(i, null);

                if (restoreOriginalValues) {
                    // Restore the original values so that the next
                    // fragment will use the default profile settings
                    context.setProfile(originalProfile);
                    context.setAccessor(originalAccessor);
                    context.setResolver(originalResolver);
                    context.setProfileScheme(originalProfileScheme);
                }
            }
        } catch (Exception e) {
            // the exception is not re-thrown but passed to the caller in the queryResult so that
            // the caller has a chance to inspect / report query stats before re-throwing the exception
            queryResult.setException(e);
            queryResult.setSourceName(sourceName);
        } finally {
            queryResult.setStats(queryStats);
        }

        return queryResult;
    }

    private void registerExecution(RequestContext context, Bridge readBridge) {
        RequestIdentifier requestIdentifier = new RequestIdentifier(context);
        readExecutionMap.put(requestIdentifier, readBridge);
    }

    private void removeExecution(RequestContext context) {
        RequestIdentifier requestIdentifier = new RequestIdentifier(context);
        readExecutionMap.remove(requestIdentifier);
    }

    private boolean cancelExecution(RequestIdentifier requestIdentifier, Bridge bridge) {
        if (bridge == null) {
            log.debug("Couldn't cancel read request, request {} not found", requestIdentifier);
            return false;
        }
        try {
            log.debug("Cancelling read request {}", requestIdentifier);
            bridge.cancelIteration();
        } catch (Exception e) {
            log.warn("Ignoring error encountered during bridge.cancelIteration()", e);
            return false;
        }
        return true;
    }

    /**
     * Processes a single fragment identified in the RequestContext and updates query statistics.
     *
     * @param countingOutputStream output stream to write data to
     * @param context              request context
     * @param queryStats           query statistics
     * @throws Exception if operation fails
     */
    private void processFragment(CountingOutputStream countingOutputStream,
                                 RequestContext context,
                                 OperationStats queryStats) throws Exception {
        Writable record;
        DataOutputStream dos = new DataOutputStream(countingOutputStream);

        OperationStats fragmentStats = new OperationStats(OperationStats.Operation.READ, metricsReporter, context);
        long previousStreamByteCount = countingOutputStream.getCount();
        Instant startTime = Instant.now();
        Bridge bridge = null;
        try {
            bridge = getBridge(context);
            registerExecution(context, bridge);
            boolean hasData = metricsReporter.reportTimer(MetricsReporter.PxfMetric.BRIDGE_BEGIN, context, bridge::beginIteration);
            if (!hasData) {
                log.debug("Skipping streaming fragment {} of resource {}",
                        context.getFragmentIndex(), context.getDataSource());
            } else {
                log.debug("Starting streaming fragment {} of resource {}",
                        context.getFragmentIndex(), context.getDataSource());
                while ((record = bridge.getNext()) != null) {
                    record.write(dos);
                    // fragment's current byte count is relative to the previous stream's byte count
                    fragmentStats.reportCompletedRecord(countingOutputStream.getCount() - previousStreamByteCount);
                }
            }
        } finally {
            if (bridge != null) {
                try {
                    bridge.endIteration();
                } catch (Exception e) {
                    log.warn("Ignoring error encountered during bridge.endIteration()", e);
                }
            }
            removeExecution(context);
            Duration duration = Duration.between(startTime, Instant.now());

            // fragment's current byte count is relative to the previous stream's byte count
            // in the case where we fail to report a record due to an exception,
            // report the number of bytes that we were able to write before failure
            fragmentStats.setByteCount(countingOutputStream.getCount() - previousStreamByteCount);
            fragmentStats.flushStats();

            // update query stats even if there was an exception so that they can be properly reported by the
            // error reporter
            queryStats.update(fragmentStats);

            log.debug("Finished processing fragment {} of resource {} in {} ms, wrote {} records and {} bytes.",
                    context.getFragmentIndex(), context.getDataSource(), duration.toMillis(), fragmentStats.getRecordCount(), fragmentStats.getByteCount());
        }
    }

    private void updateProfile(RequestContext context, String profile) {
        context.setProfile(profile);
        PluginConf pluginConf = context.getPluginConf();

        Map<String, String> pluginMap = pluginConf.getPlugins(profile);
        if (pluginMap != null) {
            context.setAccessor(pluginMap.get("ACCESSOR"));
            context.setResolver(pluginMap.get("RESOLVER"));
        }

        String handlerClassName = pluginConf.getHandler(profile);
        if (handlerClassName != null) {
            Utilities.updatePlugins(context, handlerClassName);
        }

        String profileProtocol = pluginConf.getProtocol(profile);
        if (profileProtocol != null) {
            context.setProfileScheme(profileProtocol);
        }
    }
}
