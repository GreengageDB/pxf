package org.greenplum.pxf.plugins.s3;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.EndEvent;
import software.amazon.awssdk.services.s3.model.RecordsEvent;
import software.amazon.awssdk.services.s3.model.SelectObjectContentEventStream;
import software.amazon.awssdk.services.s3.model.StatsEvent;

import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Holds the async publisher of {@link SelectObjectContentEventStream}s returned from
 * a SelectObjectContent call together with the future of the call.
 */
@Slf4j
public class S3SelectEventStream {

    private final CompletableFuture<Void> selectOperationFuture;
    private final SdkPublisher<SelectObjectContentEventStream> publisher;

    /**
     * Create the event stream.
     *
     * @param selectOperationFuture SelectObjectContent future
     * @param publisher             SelectObjectContentEventStream publisher to wrap
     */
    public S3SelectEventStream(CompletableFuture<Void> selectOperationFuture,
                               SdkPublisher<SelectObjectContentEventStream> publisher) {
        this.selectOperationFuture = selectOperationFuture;
        this.publisher = publisher;
    }

    /**
     * Retrieve an input stream to the subset of the S3 object that matched the select query.
     * This is equivalent to loading the content of all RecordsEvents into an InputStream.
     * This will lazily-load the content from S3, minimising the amount of memory used.
     * {@link AbortableInputStream#abort()} stops the request, after that the stream
     * can be closed without reading the remaining records.
     *
     * @param onEndEvent callback on the end event
     * @return the input stream
     */
    public AbortableInputStream toRecordsInputStream(Consumer<EndEvent> onEndEvent) {
        SdkPublisher<InputStream> recordInputStreams = publisher
                .filter(e -> {
                    if (e instanceof RecordsEvent) {
                        return true;
                    } else if (e instanceof EndEvent) {
                        onEndEvent.accept((EndEvent) e);
                    } else if (e instanceof StatsEvent stats && stats.details() != null && log.isDebugEnabled()) {
                        log.debug("Received Stats, Bytes Scanned: {}. Bytes Processed: {}",
                                stats.details().bytesScanned(), stats.details().bytesProcessed());
                    }
                    return false;
                })
                .map(event -> ((RecordsEvent) event).payload().asInputStream());

        // Subscribe to the async publisher using an enumeration that will
        // buffer a single chunk (RecordsEvent's payload) at a time and block until it is consumed.
        BackpressureStreamIterator streamIterator = new BackpressureStreamIterator(recordInputStreams);
        return AbortableInputStream.create(
                new SequenceInputStream(streamIterator),
                () -> {
                    cancel();
                    streamIterator.cancel();
                });
    }

    public void cancel() {
        selectOperationFuture.cancel(true);
    }
}
