package org.greenplum.pxf.service.controller;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.hadoop.conf.Configuration;
import org.greenplum.pxf.api.error.PxfRuntimeException;
import org.greenplum.pxf.api.io.Writable;
import org.greenplum.pxf.api.model.ConfigurationFactory;
import org.greenplum.pxf.api.model.Fragment;
import org.greenplum.pxf.api.model.RequestContext;
import org.greenplum.pxf.service.FragmenterService;
import org.greenplum.pxf.service.MetricsReporter;
import org.greenplum.pxf.service.bridge.Bridge;
import org.greenplum.pxf.service.bridge.BridgeFactory;
import org.greenplum.pxf.service.security.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.springframework.core.env.StandardEnvironment;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.PrivilegedAction;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class ReadServiceImplTest {

    @Mock
    private ConfigurationFactory mockConfigurationFactory;
    @Mock
    private BridgeFactory mockBridgeFactory;
    @Mock
    private SecurityService mockSecurityService;
    @Mock
    private FragmenterService mockFragmenterService;
    @Mock
    private OutputStream mockOutputStream;
    @Mock
    private Configuration mockConfiguration;
    @Mock
    private List<Fragment> mockFragmentList;
    @Mock
    private Fragment mockFragment1, mockFragment2;
    @Mock
    private Bridge mockBridge1, mockBridge2;
    @Mock
    private Writable mockRecord1, mockRecord2, mockRecord3;
    @Mock
    private RequestContext mockContext;
    @Spy
    private MetricsReporter metricReporter = new MetricsReporter(new SimpleMeterRegistry(), new StandardEnvironment());

    private ReadServiceImpl readService;

    @BeforeEach
    public void setup() throws Exception {
        when(mockConfigurationFactory.initConfiguration(any(), any(), any(), any())).thenReturn(mockConfiguration);
        when(mockFragmenterService.getFragmentsForSegment(mockContext)).thenReturn(mockFragmentList);
        when(mockSecurityService.doAs(same(mockContext), any())).thenAnswer(invocation -> {
            PrivilegedAction<OperationResult> action = invocation.getArgument(1);
            return action.run();
        });

        readService = new ReadServiceImpl(mockConfigurationFactory, mockBridgeFactory, mockSecurityService, mockFragmenterService, metricReporter);
    }

    @Test
    public void testReadDataOneFragOneRecord() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsReportBatch() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        // batch flush fires during iteration (count=2, 2%2==0) — counters appear before BRIDGE_BEGIN
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsRemainder() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(5L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        // no batch flush (2 records < frequency 5) — BRIDGE_BEGIN fires in inner finally before outer-finally flush
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragMultiRecordsRemainderAfterBatch() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, mockRecord3, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));
        doAnswer(writeTestData("Boo!")).when(mockRecord3).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        // batch flush fires at count=2 — counters appear before "Boo!" write
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 11, mockContext);
        inOrder.verify(mockOutputStream).write("Boo!".getBytes(StandardCharsets.UTF_8), 0, 4);
        // BRIDGE_BEGIN fires in inner finally (after all records) — before outer-finally flush for remainder
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 4, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataOneFragRecordsException() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(5L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenThrow(new Exception());
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        assertThrows(PxfRuntimeException.class, () -> readService.readData(mockContext, mockOutputStream));
        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataMultiFragmentMultiRecord() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(2L);
        when(mockFragmentList.size()).thenReturn(2);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1, mockBridge2);

        // 1st frag
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        // 2nd frag
        when(mockFragmentList.get(1)).thenReturn(mockFragment2);
        when(mockBridge2.beginIteration()).thenReturn(true);
        when(mockBridge2.getNext()).thenReturn(mockRecord2, mockRecord3, null);
        doAnswer(writeTestData("world!")).when(mockRecord2).write(any(DataOutputStream.class));
        doAnswer(writeTestData("Boo!")).when(mockRecord3).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        // Fragment 1: 1 record, no batch flush — BRIDGE_BEGIN fires before outer-finally flush
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        // Fragment 2: 2 records, batch flush at count=2 — counters appear before BRIDGE_BEGIN
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("world!".getBytes(StandardCharsets.UTF_8), 0, 6);
        inOrder.verify(mockOutputStream).write("Boo!".getBytes(StandardCharsets.UTF_8), 0, 4);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 2, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 10, mockContext);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataZeroReportFrequency() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(0L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1).thenReturn(null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockOutputStream, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadDataBeginIterationFalse() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(false);

        readService.readData(mockContext, mockOutputStream);

        InOrder inOrder = inOrder(mockBridge1, metricReporter);
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.OPERATION), same(mockContext), any(Tags.class), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(metricReporter).longTaskTimer(same(MetricsReporter.PxfMetric.FRAGMENTS_SENT), same(mockContext), any(), any());
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.BRIDGE_BEGIN), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockBridge1).endIteration();
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testReadWithCancel() throws Exception {
        when(metricReporter.getReportFrequency()).thenReturn(1L);
        when(mockFragmentList.size()).thenReturn(1);
        when(mockFragmentList.get(0)).thenReturn(mockFragment1);
        when(mockBridgeFactory.getBridge(mockContext)).thenReturn(mockBridge1);
        when(mockBridge1.beginIteration()).thenReturn(true);
        when(mockBridge1.getNext()).thenReturn(mockRecord1, mockRecord2, mockRecord3, null);
        doAnswer(writeTestData("hello")).when(mockRecord1).write(any(DataOutputStream.class));
        doAnswer(s -> {
            Thread.sleep(10000);
            return null;
        }).when(mockRecord2).write(any(DataOutputStream.class));

        Thread readThread = new Thread(() -> readService.readData(mockContext, mockOutputStream));
        readThread.setName("readThread");
        readThread.start();
        Thread.sleep(200);
        readService.cancelRead(mockContext);


        InOrder inOrder = inOrder(mockOutputStream, metricReporter, mockBridge1);
        inOrder.verify(metricReporter).reportTimer(same(MetricsReporter.PxfMetric.FRAGMENTER_CALL), any(Duration.class), same(mockContext), eq(true));
        inOrder.verify(mockOutputStream).write("hello".getBytes(StandardCharsets.UTF_8), 0, 5);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.RECORDS_SENT, 1, mockContext);
        inOrder.verify(metricReporter).reportCounter(MetricsReporter.PxfMetric.BYTES_SENT, 5, mockContext);
        inOrder.verify(mockBridge1).cancelIteration();
        inOrder.verifyNoMoreInteractions();
    }

    // helper for writing mock record to a mock output stream
    // mockOutputStream -> CountingOutputStream -> DataOutputStream
    // in order for the us to see the side-effect of CountingOutputStream,
    // we need to actually call the `write` method of DataOutputStream.
    private Answer writeTestData(String testData) {
        return invocation -> {
            DataOutputStream dos = invocation.getArgument(0);
            dos.write(testData.getBytes(StandardCharsets.UTF_8));
            return null;
        };
    }
}
