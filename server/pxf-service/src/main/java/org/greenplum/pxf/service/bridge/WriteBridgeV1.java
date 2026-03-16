package org.greenplum.pxf.service.bridge;

import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.model.ProtocolVersionV1Aware;

import java.util.List;

@Slf4j
public class WriteBridgeV1 extends BridgeDelegate implements ProtocolVersionV1Aware {

    private final ProtocolVersionV1Aware protocolVersionV1Aware;

    public WriteBridgeV1(Bridge delegate, ProtocolVersionV1Aware protocolVersionV1Aware) {
        super(delegate);
        this.protocolVersionV1Aware = protocolVersionV1Aware;
    }

    @Override
    public byte[] endIteration() throws Exception {
        try {
            return closeForWriteAndReturnMetadata();
        } catch (Exception e) {
            log.error("Failed to close bridge resources: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public void commit(List<byte[]> fullMetadata) throws Exception {
        protocolVersionV1Aware.commit(fullMetadata);
    }

    @Override
    public byte[] closeForWriteAndReturnMetadata() throws Exception {
        return protocolVersionV1Aware.closeForWriteAndReturnMetadata();
    }
}
