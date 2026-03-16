package org.greenplum.pxf.api.model;

import org.apache.commons.lang.NotImplementedException;

import java.util.List;

public interface ProtocolVersionV1Aware extends CommittableOperation {

    /**
     * Commit data handled on all pxf nodes.
     *
     * @throws Exception if closing the resource failed
     */
    default void commit(List<byte[]> fullMetadata) throws Exception {
        throw new NotImplementedException("Commit operation is not implemented");
    }

    /**
     * Closes the resource for write and return metadata.
     *
     * @throws Exception if closing the resource failed
     */
    default byte[] closeForWriteAndReturnMetadata() throws Exception {
        throw new NotImplementedException("Return metadata from write is not implemented");
    }

    /**
     * Closes the resource.
     *
     * @throws Exception if closing the resource failed
     */
    default byte[] closeForReadAndReturnMetadata() throws Exception {
        throw new NotImplementedException("Return metadata from read is not implemented");
    }


}
