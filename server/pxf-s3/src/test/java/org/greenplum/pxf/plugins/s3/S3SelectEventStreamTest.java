package org.greenplum.pxf.plugins.s3;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.SelectObjectContentEventStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class S3SelectEventStreamTest {

    private static Executor createExecutor(String threading) {
        if (threading.equals("main")) {
            return Runnable::run;
        } else if (threading.equals("background")) {
            return Executors.newSingleThreadExecutor();
        } else {
            throw new IllegalArgumentException("Unknown: " + threading);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void emptyRecordsInputStream(String threading) throws IOException {
        S3SelectEventStream eventStream = createEventStream(threading, Stream.of(
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromByteArray(new byte[0]))
                        .build()));

        try (AbortableInputStream inputStream = eventStream.toRecordsInputStream(e -> {
        })) {
            assertEquals(-1, inputStream.read());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void multipleRecords(String threading) throws IOException {
        S3SelectEventStream eventStream = createEventStream(threading, Stream.of(
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromUtf8String("foo"))
                        .build(),
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromUtf8String("bar"))
                        .build()));

        try (AbortableInputStream inputStream = eventStream.toRecordsInputStream(e -> {
        })) {
            String result = readAll(inputStream);
            assertEquals("foobar", result);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void skipsOtherEvents(String threading) throws IOException {
        S3SelectEventStream eventStream = createEventStream(threading, Stream.of(
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromUtf8String("foo"))
                        .build(),
                SelectObjectContentEventStream.progressBuilder()
                        .build(),
                SelectObjectContentEventStream.statsBuilder()
                        .build(),
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromUtf8String("bar"))
                        .build(),
                SelectObjectContentEventStream.endBuilder()
                        .build()));

        try (AbortableInputStream inputStream = eventStream.toRecordsInputStream(e -> {
        })) {
            String result = readAll(inputStream);
            assertEquals("foobar", result);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void callsOnEndEvent(String threading) throws IOException {
        S3SelectEventStream eventStream = createEventStream(threading, Stream.of(
                SelectObjectContentEventStream.recordsBuilder()
                        .payload(SdkBytes.fromUtf8String("foo"))
                        .build(),
                SelectObjectContentEventStream.endBuilder()
                        .build()));

        AtomicBoolean endEvent = new AtomicBoolean(false);
        try (AbortableInputStream inputStream =
                     eventStream.toRecordsInputStream(e -> endEvent.set(true))) {
            String result = readAll(inputStream);
            assertEquals("foo", result);
        }

        assertTrue(endEvent.get());
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void handlesErrors(String threading) throws IOException {
        S3SelectEventStream eventStream = createEventStream(threading, Stream.of(
                        SelectObjectContentEventStream.recordsBuilder()
                                .payload(SdkBytes.fromUtf8String("foo"))
                                .build(),
                        SelectObjectContentEventStream.recordsBuilder()
                                .payload(SdkBytes.fromUtf8String("bar"))
                                .build())
                .map(e -> {
                    throw SdkException.create("error!", null);
                }));

        try (AbortableInputStream inputStream = eventStream.toRecordsInputStream(e -> {
        })) {
            assertThrows(SdkException.class, () -> readAll(inputStream));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "background"})
    public void abortThenCloseDoesNotReadRemainingRecords(String threading) throws IOException {
        AtomicInteger emitted = new AtomicInteger();
        CompletableFuture<Void> future = new CompletableFuture<>();
        S3SelectEventStream eventStream = createEventStream(threading, future,
                IntStream.range(0, 1000)
                        .peek(i -> emitted.incrementAndGet())
                        .mapToObj(i -> SelectObjectContentEventStream.recordsBuilder()
                                .payload(SdkBytes.fromUtf8String(i + "\n"))
                                .build()));

        AtomicBoolean endEvent = new AtomicBoolean(false);
        AbortableInputStream inputStream = eventStream.toRecordsInputStream(e -> endEvent.set(true));
        assertEquals('0', inputStream.read());

        inputStream.abort();
        inputStream.close();

        assertTrue(future.isCancelled());
        assertFalse(endEvent.get());
        assertTrue(emitted.get() <= 3, "emitted " + emitted.get());
    }

    private S3SelectEventStream createEventStream(String threading,
                                                  Stream<SelectObjectContentEventStream> stream) {
        return createEventStream(threading, CompletableFuture.completedFuture(null), stream);
    }

    private S3SelectEventStream createEventStream(String threading,
                                                  CompletableFuture<Void> future,
                                                  Stream<SelectObjectContentEventStream> stream) {
        SdkPublisher<SelectObjectContentEventStream> sdkPublisher = new StreamPublisher<>(stream, createExecutor(threading));
        return new S3SelectEventStream(future, sdkPublisher);
    }

    private static String readAll(InputStream inputStream) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8096];
            int read;
            while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            return outputStream.toString();
        }
    }
}
