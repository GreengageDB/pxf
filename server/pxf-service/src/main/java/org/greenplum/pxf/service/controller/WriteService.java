package org.greenplum.pxf.service.controller;

import org.greenplum.pxf.api.model.RequestContext;

import java.io.InputStream;
import java.util.List;

/**
 * Service that writes data to external systems.
 */
public interface WriteService {

    /**
     * Writes data to the external system specified by the RequestContext.
     * The data is first read from the provided InputStream.
     *
     * @param context     request context
     * @param inputStream input stream to read data from
     * @return text response to send back to the client
     * @throws Exception if any error happened during processing
     */
    OperationResult writeData(RequestContext context, InputStream inputStream) throws Exception;

    /**
     * Commit data written to the external system on previous step.
     *
     * @param context     request context
     * @param fullMetadata metadata received from all pxf nodes
     * @throws Exception if any error happened during processing
     */
    void commitData(RequestContext context, List<byte[]> fullMetadata) throws Exception;

    /**
     * Tries to cancel active write request to the external system specified by the RequestContext.
     * Returns true if write request was found and cancelled, false otherwise
     *
     * @param context request context
     * @return if write request cancellation succeeded
     */
    boolean cancelWrite(RequestContext context);

    /**
     * Tries to cancel active write requests to the external system with specified profile and server.
     *
     * @param profile the name of the profile defined in the external table. For example, jdbc, hdfs, s3, etc...
     * @param server the named server configuration that PXF uses to access the data. PXF uses the default server if not specified.
     */
    void cancelWriteExecutions(String profile, String server);
}
