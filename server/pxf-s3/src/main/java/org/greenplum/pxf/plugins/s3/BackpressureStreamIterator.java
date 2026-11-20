package org.greenplum.pxf.plugins.s3;

import lombok.Builder;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Implements the {@link Enumeration} interface by subscribing to a
 * {@link SdkPublisher} of input streams. The enumeration buffers a single
 * element and only requests the next one from the publisher when the
 * previous one is consumed. Calls to {@link #hasMoreElements()} and
 * {@link #nextElement()} may block while waiting for new elements.
 * <p>
 * The enumeration is made for {@link java.io.SequenceInputStream}, which takes
 * the first element in its constructor. So the enumeration starts with an empty
 * stream, that is returned without waiting for the publisher.
 */
public class BackpressureStreamIterator implements Enumeration<InputStream> {

    private static final InputStream EMPTY_STREAM = new ByteArrayInputStream(new byte[0]);
    private static final StreamEventData FINISHED_STREAM_DATA = StreamEventData.builder().build();

    private final CompletableFuture<Subscription> subscription = new CompletableFuture<>();
    private final BlockingQueue<StreamEventData> queue;
    private StreamEventData currentStreamEventData;

    public BackpressureStreamIterator(SdkPublisher<InputStream> publisher) {
        this.queue = new LinkedBlockingQueue<>();
        this.currentStreamEventData = StreamEventData.builder().stream(EMPTY_STREAM).build();
        publisher.subscribe(new StreamEventSubscriber());
    }

    @Override
    public boolean hasMoreElements() {
        if (currentStreamEventData == null) {
            try {
                currentStreamEventData = queue.take();
            } catch (InterruptedException ex) {
                currentStreamEventData = StreamEventData.builder().error(ex).build();
                subscription.thenAccept(Subscription::cancel);
                Thread.currentThread().interrupt();
            }
        }
        if (currentStreamEventData.error() != null) {
            Throwable error = currentStreamEventData.error();
            currentStreamEventData = FINISHED_STREAM_DATA;
            if (error instanceof Error fatal) {
                throw fatal;
            } else if (error instanceof SdkException sdkException) {
                throw sdkException;
            } else {
                throw SdkException.create("Unexpected error", error);
            }
        }
        return currentStreamEventData != FINISHED_STREAM_DATA;
    }

    @Override
    public InputStream nextElement() {
        if (!hasMoreElements()) {
            throw new NoSuchElementException();
        }
        InputStream inputStream = currentStreamEventData.stream();
        currentStreamEventData = null;
        subscription.thenAccept(s -> s.request(1));
        return inputStream;
    }

    /**
     * Stops the enumeration: cancels the subscription and drops the buffered
     * elements, so that {@link #hasMoreElements()} returns false without
     * waiting for the publisher. Must be called from the consuming thread.
     * <p>
     * {@link java.io.SequenceInputStream#close()} reads the enumeration till
     * its end, so without this call closing a partially read stream would
     * download all remaining records.
     */
    public void cancel() {
        currentStreamEventData = FINISHED_STREAM_DATA;
        queue.clear();
        subscription.thenAccept(Subscription::cancel);
    }

    private final class StreamEventSubscriber implements Subscriber<InputStream> {

        @Override
        public void onSubscribe(Subscription s) {
            subscription.complete(s);
        }

        @Override
        public void onNext(InputStream stream) {
            queue.add(StreamEventData.builder().stream(stream).build());
        }

        @Override
        public void onError(Throwable throwable) {
            queue.add(StreamEventData.builder().error(throwable).build());
        }

        @Override
        public void onComplete() {
            queue.add(FINISHED_STREAM_DATA);
        }
    }

    @Builder
    private record StreamEventData(InputStream stream, Throwable error) {
    }
}
