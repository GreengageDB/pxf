package org.greenplum.pxf.service.rest;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.greenplum.pxf.api.model.ProtocolVersion;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.RequestParser;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

/**
 * Base class for PXF REST resources that provides unified error logging and wrapping.
 * All exceptions will be first logged with the proper MDC context and then wrapped into a PxfRuntimeException
 * so that the ErrorHandler can process them and not re-throw to the container
 * where they would've been logged again, but without the MDC context.
 *
 * @param <T> type of ResponseEntity that a resource will produce.
 */
@Slf4j
public abstract class PxfBaseResource<T> {

    protected final RequestContext.RequestType requestType;
    protected final RequestParser<MultiValueMap<String, String>> parser;
    private final PxfRequestHandler requestHandler;

    /**
     * Creates a new instance of the resource.
     *
     * @param requestType type of PXF request
     * @param parser      request parser
     */
    protected PxfBaseResource(RequestContext.RequestType requestType, RequestParser<MultiValueMap<String, String>> parser) {
        this.requestType = requestType;
        this.parser = parser;
        this.requestHandler = new PxfRequestHandler(parser);
    }

    /**
     * Parses the incoming httpServletRequest and produces a response, wrapping and logging an error, if any.
     *
     * @param headers            http servlet request headers
     * @param httpServletRequest http servlet request
     * @return response entity to give to container
     */
    protected ResponseEntity<T> processRequest(final MultiValueMap<String, String> headers,
                                               final HttpServletRequest httpServletRequest) {
        return requestHandler.processRequest(headers, requestType, ProtocolVersion.V0, context ->
                produceResponse(context, httpServletRequest)
        );
    }

    /**
     * Produces response of type T by processing a given request.
     *
     * @param context PXF request context
     * @param request HTTP servlet request
     * @return the response that can be placed in the ResponseEntity and given to the container
     * @throws Exception if operation fails
     */
    protected abstract T produceResponse(RequestContext context, HttpServletRequest request) throws Exception;

}
