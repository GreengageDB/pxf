package org.greenplum.pxf.service.bridge;

import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.model.ProtocolVersionV1Aware;

@Slf4j
public class WriteBridgeV1 extends BridgeDelegate {

    private final ProtocolVersionV1Aware protocolVersionV1Aware;

    public WriteBridgeV1(Bridge delegate, ProtocolVersionV1Aware protocolVersionV1Aware) {
        super(delegate);
        this.protocolVersionV1Aware = protocolVersionV1Aware;
    }

    @Override
    public byte[] endIteration() throws Exception {
        try {
            return protocolVersionV1Aware.closeForWriteAndReturnMetadata();
        } catch (Exception e) {
            log.error("Failed to close bridge resources: {}", e.getMessage());
            throw e;
        }
    }

}
