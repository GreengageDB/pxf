package org.greenplum.pxf.service.rest;

import lombok.RequiredArgsConstructor;
import org.greenplum.pxf.api.model.ProtocolVersion;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.RequestParser;
import org.greenplum.pxf.service.controller.PxfErrorReporter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.util.function.ThrowingFunction;

@RequiredArgsConstructor
public class PxfRequestHandler extends PxfErrorReporter {

    private final RequestParser<MultiValueMap<String, String>> parser;

    public <V> ResponseEntity<V> processRequest(final MultiValueMap<String, String> headers,
                                                   final RequestContext.RequestType requestType,
                                                   final ProtocolVersion protocolVersion,
                                                   final ThrowingFunction<RequestContext, V> responseBuilder) {
        // use the request processing algorithm as a lambda for the invoking and error handling logic
        V response = this.invokeWithErrorHandling(
                () -> {
                    RequestContext context = parser.parseRequest(headers, requestType);
                    context.setProtocolVersion(protocolVersion);
                    return responseBuilder.apply(context);
                }
        );
        // return the response entity, if it is StreamingResponseBody, then the response will be streamed asynchronously
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

}
