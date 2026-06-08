package org.greenplum.pxf.service.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.model.ProtocolVersion;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.RequestParser;
import org.greenplum.pxf.service.controller.OperationResult;
import org.greenplum.pxf.service.controller.WriteService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * PXF REST endpoint for protocol v1 requests.
 */
@RestController
@RequestMapping("/pxf/v1")
@Slf4j
public class PxfProtocolV1Resource {

    private final WriteService writeService;
    private final PxfRequestHandler requestHandler;

    /**
     * Creates a new instance of the resource with Request parser and write service implementation.
     *
     * @param parser       http request parser
     * @param writeService write service implementation
     */
    public PxfProtocolV1Resource(RequestParser<MultiValueMap<String, String>> parser,
                                 WriteService writeService) {
        this.writeService = writeService;
        this.requestHandler = new PxfRequestHandler(parser);
    }

    @PostMapping(value = "/write", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> stream(@RequestHeader MultiValueMap<String, String> headers,
                                         HttpServletRequest request) {
        return requestHandler.processRequest(headers, RequestContext.RequestType.WRITE_BRIDGE, ProtocolVersion.V1, context -> {
            OperationResult result = writeService.writeData(context, request.getInputStream());
            return result.getMetadata();
        });
    }

    @PostMapping(value = "/commit", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<Void> commit(@RequestHeader MultiValueMap<String, String> headers,
                                       @RequestBody byte[] fullMetadata) {
        return requestHandler.processRequest(headers, RequestContext.RequestType.WRITE_BRIDGE, ProtocolVersion.V1, context -> {
            writeService.commitData(context, deserializeToByteArrayList(fullMetadata));
            return null;
        });
    }

    private List<byte[]> deserializeToByteArrayList(byte[] raw) {
        List<byte[]> metadata = new ArrayList<>();

        ByteBuffer buffer = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);

        while (buffer.hasRemaining()) {
            int length = buffer.getInt();
            byte[] chunk = new byte[length];
            buffer.get(chunk);
            metadata.add(chunk);
        }

        return metadata;
    }

}
