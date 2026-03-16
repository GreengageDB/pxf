package org.greenplum.pxf.service.controller;

import com.google.common.io.CountingInputStream;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.greenplum.pxf.api.model.CommittableOperation;
import org.greenplum.pxf.api.model.ConfigurationFactory;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.MetricsReporter;
import org.greenplum.pxf.service.bridge.Bridge;
import org.greenplum.pxf.service.bridge.BridgeFactory;
import org.greenplum.pxf.service.security.SecurityService;
import org.springframework.stereotype.Service;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Implementation of the WriteService.
 */
@Service
@Slf4j
public class WriteServiceImpl extends BaseServiceImpl<OperationResult> implements WriteService {

    private final Map<RequestIdentifier, Bridge> writeExecutionMap = new ConcurrentHashMap<>();

    /**
     * Creates a new instance.
     *
     * @param configurationFactory configuration factory
     * @param bridgeFactory        bridge factory
     * @param securityService      security service
     */
    public WriteServiceImpl(ConfigurationFactory configurationFactory,
                            BridgeFactory bridgeFactory,
                            SecurityService securityService,
                            MetricsReporter metricsReporter) {
        super("Write", configurationFactory, bridgeFactory, securityService, metricsReporter);
    }

    @Override
    public OperationResult writeData(RequestContext context, InputStream inputStream) throws Exception {
        return processData(context, OperationStats.Operation.WRITE, () -> readStream(context, inputStream));
    }

    @Override
    public void commitData(RequestContext context, List<byte[]> fullMetadata) throws Exception {
        Bridge bridge = getBridge(context);
        if(bridge instanceof CommittableOperation committable) {
            committable.commit(fullMetadata);
            return;
        }
        throw new IllegalStateException("Commit operation is not supported by protocol " + context.getProtocolVersion());
    }

    @Override
    public boolean cancelWrite(RequestContext context) {
        RequestIdentifier requestIdentifier = new RequestIdentifier(context);
        Bridge bridge = writeExecutionMap.remove(requestIdentifier);
        return cancelExecution(requestIdentifier, bridge);
    }

    @Override
    public void cancelWriteExecutions(String profile, String server) {
        Predicate<RequestIdentifier> identifierFilter = getIdentifierFilter(profile, server);
        writeExecutionMap.forEach((requestIdentifier, bridge) -> {
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
     * Reads the input stream, iteratively submits data from the stream to created bridge.
     *
     * @param context     request context
     * @param inputStream input stream
     * @return operation statistics
     */
    private OperationResult readStream(RequestContext context, InputStream inputStream) {
        Bridge bridge = getBridge(context);

        OperationStats operationStats = new OperationStats(OperationStats.Operation.WRITE, metricsReporter, context);
        OperationResult operationResult = new OperationResult();

        RequestIdentifier requestIdentifier = new RequestIdentifier(context);
        writeExecutionMap.put(requestIdentifier, bridge);

        // dataStream (and inputStream as the result) will close automatically at the end of the try block
        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
        try (DataInputStream dataStream = new DataInputStream(countingInputStream)) {
            // open the output file, returns true or throws an error
            metricsReporter.reportTimer(MetricsReporter.PxfMetric.BRIDGE_BEGIN, context, bridge::beginIteration);
            while (bridge.setNext(dataStream)) {
                operationStats.reportCompletedRecord(countingInputStream.getCount());
            }
        } catch (Exception e) {
            operationResult.setException(e);
        } finally {
            try {
                operationResult.setMetadata(bridge.endIteration());
            } catch (Exception e) {
                if (operationResult.getException() == null) {
                    operationResult.setException(e);
                }
            }
            writeExecutionMap.remove(requestIdentifier);
            // in the case where we fail to report a record due to an exception,
            // report the number of bytes that we were able to read before failure
            operationStats.setByteCount(countingInputStream.getCount());
            operationStats.flushStats();
            operationResult.setStats(operationStats);
        }

        return operationResult;
    }

    private boolean cancelExecution(RequestIdentifier requestIdentifier, Bridge bridge) {
        if (bridge == null) {
            log.debug("Couldn't cancel write request, request {} not found", requestIdentifier);
            return false;
        }
        try {
            log.debug("Cancelling write request {}", requestIdentifier);
            bridge.cancelIteration();
        } catch (Exception e) {
            log.warn("Ignoring error encountered during bridge.cancelIteration()", e);
            return false;
        }
        return true;
    }
}
