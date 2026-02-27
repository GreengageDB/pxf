package org.greenplum.pxf.service.bridge;

import org.greenplum.pxf.api.model.*;
import org.greenplum.pxf.api.utilities.Utilities;
import org.greenplum.pxf.service.serde.RecordReaderFactory;
import org.greenplum.pxf.service.utilities.BasePluginFactory;
import org.greenplum.pxf.service.utilities.GSSFailureHandler;
import org.springframework.stereotype.Component;

@Component
public class SimpleBridgeFactory implements BridgeFactory {

    private final BasePluginFactory pluginFactory;
    private final RecordReaderFactory recordReaderFactory;
    private final GSSFailureHandler failureHandler;

    public SimpleBridgeFactory(BasePluginFactory pluginFactory, RecordReaderFactory recordReaderFactory, GSSFailureHandler failureHandler) {
        this.pluginFactory = pluginFactory;
        this.recordReaderFactory = recordReaderFactory;
        this.failureHandler = failureHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bridge getBridge(RequestContext context) {

        Bridge bridge;
        if (context.getRequestType() == RequestContext.RequestType.WRITE_BRIDGE) {
            bridge = getWriteBridge(context);
        } else if (context.getRequestType() != RequestContext.RequestType.READ_BRIDGE) {
            throw new UnsupportedOperationException("Current Operation is not supported");
        } else if (context.getStatsSampleRatio() > 0) {
            bridge = new ReadSamplingBridge(pluginFactory, context, failureHandler);
        } else if (Utilities.aggregateOptimizationsSupported(context)) {
            bridge = new AggBridge(pluginFactory, context, failureHandler);
        } else if (useReadVectorization(context)) {
            bridge = new ReadVectorizedBridge(pluginFactory, context, failureHandler);
        } else {
            bridge = new ReadBridge(pluginFactory, context, failureHandler);
        }
        return bridge;
    }

    private Bridge getWriteBridge(RequestContext context) {
        if (useWriteVectorization(context)) {
            return new WriteVectorizedBridge(pluginFactory, recordReaderFactory, context, failureHandler);
        }
        var bridge = new WriteBridge(pluginFactory, recordReaderFactory, context, failureHandler);
        if(ProtocolVersion.V1.equals(context.getProtocolVersion())) {
            var accessor = bridge.accessor;
            if(!(accessor instanceof ProtocolVersionV1Aware)) {
                throw new IllegalStateException("Protocol version v1 is not supported by accessor with type " + accessor.getClass().getName());
            }
            return new WriteBridgeV1(bridge, (ProtocolVersionV1Aware) accessor);
        }
        return bridge;
    }

    /**
     * Determines whether to use vectorization when reading data from an external system
     *
     * @param requestContext input protocol data
     * @return true if vectorization during reading is applicable in a current context
     */
    private boolean useReadVectorization(RequestContext requestContext) {
        String resolverName = requestContext.getResolver();
        return Utilities.implementsInterface(resolverName, ReadVectorizedResolver.class);
    }

    /**
     * Determines whether to use vectorization when writing data to an external system
     *
     * @param requestContext input protocol data
     * @return true if vectorization during writing is applicable in a current context
     */
    private boolean useWriteVectorization(RequestContext requestContext) {
        String resolverName = requestContext.getResolver();
        return Utilities.implementsInterface(resolverName, WriteVectorizedResolver.class);
    }

}
