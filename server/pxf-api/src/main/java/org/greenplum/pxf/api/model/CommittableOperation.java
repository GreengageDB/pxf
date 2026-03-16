package org.greenplum.pxf.api.model;

import java.util.List;

public interface CommittableOperation {

    /**
     * Commit data handled on all pxf nodes.
     *
     * @throws Exception if closing the resource failed
     */
    void commit(List<byte[]> fullMetadata) throws Exception;

}
