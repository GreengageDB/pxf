package org.greenplum.pxf.service.rest;

import com.google.common.base.Charsets;
import org.greenplum.pxf.api.error.PxfRuntimeException;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.HttpHeaderDecoder;
import org.greenplum.pxf.service.RequestParser;
import org.greenplum.pxf.service.controller.OperationResult;
import org.greenplum.pxf.service.controller.OperationStats;
import org.greenplum.pxf.service.controller.ReadService;
import org.greenplum.pxf.service.controller.WriteService;
import org.greenplum.pxf.service.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PxfReadResource.class, PxfWriteResource.class, PxfLegacyResource.class})
@Import(SecurityConfig.class)
public class PxfResourceIT {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private RequestParser<MultiValueMap<String, String>> mockParser;

    @MockBean
    private WriteService mockWriteService;

    @MockBean
    private HttpHeaderDecoder mockHttpHeaderDecoder;

    @Mock
    private RequestContext mockContext;

    @Test
    public void testReadEndpoint() throws Exception {
        when(mockParser.parseRequest(any(), eq(RequestContext.RequestType.READ_BRIDGE))).thenReturn(mockContext);

        ResultActions result = mvc.perform(get("/pxf/read")).andExpect(status().isOk());
        Thread.sleep(200);
        result.andExpect(content().string("Hello from read!"));
    }

    @Test
    public void testWriteEndpoint() throws Exception {
        when(mockParser.parseRequest(any(), eq(RequestContext.RequestType.WRITE_BRIDGE))).thenReturn(mockContext);
        var stats = mock(OperationStats.class);
        when(stats.getRecordCount()).thenReturn(100L);
        var operationResult = new OperationResult();
        operationResult.setStats(stats);

        when(mockWriteService.writeData(same(mockContext), any())).thenReturn(operationResult);

        mvc.perform(post("/pxf/write"))
                .andExpect(status().isOk());
    }

    @Test
    public void testCancelReadEndpoint() throws Exception {
        when(mockParser.parseRequest(any(), eq(RequestContext.RequestType.READ_BRIDGE))).thenReturn(mockContext);

        mvc.perform(post("/pxf/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    public void testLegacyFragmenterEndpoint() throws Exception {
        ResultActions result = mvc.perform(
                get("/pxf/v15/Fragmenter/getFragments").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError());
        result.andExpect(r -> assertTrue(r.getResolvedException() instanceof PxfRuntimeException))
                .andExpect(r -> assertEquals("/Fragmenter/getFragments API (v15) is no longer supported by the server, upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        r.getResolvedException().getMessage()))
                .andExpect(r -> assertEquals("upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        ((PxfRuntimeException) r.getResolvedException()).getHint()));
    }

    @Test
    public void testLegacyBridgeEndpoint() throws Exception {
        when(mockParser.parseRequest(any(), eq(RequestContext.RequestType.READ_BRIDGE))).thenReturn(mockContext);

        ResultActions result = mvc.perform(get("/pxf/v15/Bridge")).andExpect(status().isInternalServerError());
        result.andExpect(r -> assertTrue(r.getResolvedException() instanceof PxfRuntimeException))
                .andExpect(r -> assertEquals("/Bridge API (v15) is no longer supported by the server, upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        r.getResolvedException().getMessage()))
                .andExpect(r -> assertEquals("upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        ((PxfRuntimeException) r.getResolvedException()).getHint()));
    }

    @Test
    public void testLegacyWritableEndpoint() throws Exception {
        when(mockParser.parseRequest(any(), eq(RequestContext.RequestType.WRITE_BRIDGE))).thenReturn(mockContext);
        OperationResult operationResult = mock(OperationResult.class);
        when(mockWriteService.writeData(same(mockContext), any())).thenReturn(operationResult);

        ResultActions result = mvc.perform(post("/pxf/v15/Writable/stream")).andExpect(status().isInternalServerError());
        result.andExpect(r -> assertTrue(r.getResolvedException() instanceof PxfRuntimeException))
                .andExpect(r -> assertEquals("/Writable/stream API (v15) is no longer supported by the server, upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        r.getResolvedException().getMessage()))
                .andExpect(r -> assertEquals("upgrade PXF extension (run 'pxf [cluster] register' and then 'ALTER EXTENSION pxf UPDATE')",
                        ((PxfRuntimeException) r.getResolvedException()).getHint()));
    }

    @TestConfiguration
    static class PxfResourceTestConfiguration {
        @Bean
        ReadService createReadService() {
            return new ReadService() {
                @Override
                public void readData(RequestContext context, OutputStream out) {
                    try {
                        out.write("Hello from read!".getBytes(Charsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public boolean cancelRead(RequestContext context) {
                    return true;
                }

                @Override
                public void cancelReadExecutions(String profile,String server) {
                }
            };
        }
    }
}
