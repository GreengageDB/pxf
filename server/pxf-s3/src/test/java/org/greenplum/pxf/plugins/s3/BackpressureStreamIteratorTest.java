package org.greenplum.pxf.plugins.s3;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BackpressureStreamIteratorTest {

    @Test
    public void startsWithEmptyStreamWithoutWaitingForPublisher() {
        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(silentPublisher());
        assertStartsWithEmptyStream(streamIterator);
    }

    @Test
    public void containsElement() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(Stream.of("foo").map(BackpressureStreamIteratorTest::stream));

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);

        assertStartsWithEmptyStream(streamIterator);
        assertTrue(streamIterator.hasMoreElements());
        assertEquals("foo", read(streamIterator.nextElement()));
        assertFalse(streamIterator.hasMoreElements());
    }

    @Test
    public void throwsExceptionOnFirstElement() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                Stream.of(0, 1)
                        .map(i -> {
                            throw SdkException.create("error!", null);
                        }),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        assertThrows(SdkException.class, streamIterator::hasMoreElements);
    }

    @Test
    public void throwsNonSdkException() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                Stream.of(0, 1)
                        .map(i -> {
                            throw new RuntimeException("error!", null);
                        }),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        SdkException exception = assertThrows(SdkException.class, streamIterator::hasMoreElements);
        assertEquals(RuntimeException.class, exception.getCause().getClass());
    }

    @Test
    public void throwsError() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                Stream.of(0, 1)
                        .map(i -> {
                            throw new Error("error!", null);
                        }),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        assertThrows(Error.class, streamIterator::hasMoreElements);
    }

    @Test
    public void throwsExceptionOnSecondElement() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                Stream.of(0, 1)
                        .peek(i -> {
                            if (i == 1) {
                                throw SdkException.create("error!", null);
                            }
                        })
                        .map(i -> stream(String.valueOf(i))),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        assertTrue(streamIterator.hasMoreElements());
        assertEquals("0", read(streamIterator.nextElement()));
        assertThrows(SdkException.class, streamIterator::hasMoreElements);
    }

    @Test
    public void noMoreElementsAfterThrow() {
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                Stream.of(0, 1)
                        .map(i -> {
                            throw SdkException.create("error!", null);
                        }),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        assertThrows(SdkException.class, streamIterator::hasMoreElements);
        assertFalse(streamIterator.hasMoreElements());
    }

    @Test
    public void buffersOnSameThread() {
        verifyBuffering(10, Runnable::run);
    }

    @Test
    public void publisherOnDifferentThread() {
        verifyBuffering(5, Executors.newSingleThreadExecutor());
    }

    @Test
    public void cancelStopsStreamIteratorAndSubscription() {
        AtomicInteger emitted = new AtomicInteger();
        StreamPublisher<InputStream> publisher = new StreamPublisher<>(
                IntStream.range(0, 1000)
                        .peek(i -> emitted.incrementAndGet())
                        .mapToObj(i -> stream(String.valueOf(i))),
                Executors.newSingleThreadExecutor());

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertStartsWithEmptyStream(streamIterator);
        assertTrue(streamIterator.hasMoreElements());
        assertEquals("0", read(streamIterator.nextElement()));

        streamIterator.cancel();

        assertFalse(streamIterator.hasMoreElements());
        assertTrue(publisher.isDone());
        assertTrue(emitted.get() <= 2, "emitted " + emitted.get());
    }

    @Test
    public void cancelDoesNotWaitForPublisher() {
        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(silentPublisher());
        assertTrue(streamIterator.hasMoreElements());
        streamIterator.cancel();
        assertFalse(streamIterator.hasMoreElements());
    }

    /**
     * Checks that only one element is requested ahead of the pulled ones.
     * StreamPublisher takes the elements from the stream inside request(),
     * so the emitted count is exact without waiting for the executor.
     */
    private static void verifyBuffering(int length, Executor executor) {
        AtomicInteger emitted = new AtomicInteger();
        SdkPublisher<InputStream> publisher = new StreamPublisher<>(
                IntStream.range(0, length)
                        .peek(i -> emitted.incrementAndGet())
                        .mapToObj(i -> stream(String.valueOf(i))),
                executor);

        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(publisher);
        assertEquals(0, emitted.get());
        assertStartsWithEmptyStream(streamIterator);

        int pulled = 0;
        while (true) {
            assertEquals(Math.min(length, pulled + 1), emitted.get());
            if (!streamIterator.hasMoreElements()) {
                break;
            }
            assertEquals(String.valueOf(pulled), read(streamIterator.nextElement()));
            pulled++;
        }
        assertEquals(length, pulled);
    }

    /**
     * SequenceInputStream takes the first element in its constructor,
     * so the streamIterator starts with an empty stream.
     */
    private static void assertStartsWithEmptyStream(BackpressureStreamIterator streamIterator) {
        assertTrue(streamIterator.hasMoreElements());
        assertEquals("", read(streamIterator.nextElement()));
    }

    /**
     * Publisher that never sends anything.
     */
    private static SdkPublisher<InputStream> silentPublisher() {
        return subscriber -> subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
            }

            @Override
            public void cancel() {
            }
        });
    }

    private static InputStream stream(String data) {
        return new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
    }

    private static String read(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
