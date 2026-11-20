package org.greenplum.pxf.plugins.s3;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.hadoop.fs.s3a.S3AUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.SelectObjectContentEventStream;
import software.amazon.awssdk.services.s3.model.SelectObjectContentRequest;
import software.amazon.awssdk.services.s3.model.SelectObjectContentResponse;
import software.amazon.awssdk.services.s3.model.SelectObjectContentResponseHandler;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Helper for SelectObjectContent queries against an S3 Bucket.
 */
@Slf4j
public class S3SelectObjectContentHelper {

    public S3SelectObjectContentHelper() {
    }

    /**
     * Execute an S3 Select operation and wait until the event stream starts.
     *
     * @param s3Client the async client
     * @param request  the select request
     * @param path     the object path, used in error messages
     * @return the publisher of the select events
     * @throws IOException failure, translated by S3A like for other S3 operations
     */
    public S3SelectEventStream select(S3AsyncClient s3Client,
                                      SelectObjectContentRequest request,
                                      String path) throws IOException {
        try {
            Handler handler = new Handler();
            CompletableFuture<Void> selectOperationFuture = s3Client.selectObjectContent(request, handler);
            return handler.eventPublisher(selectOperationFuture).join();
        } catch (Throwable e) {
            Throwable throwable = e;
            if (throwable instanceof CompletionException) {
                throwable = throwable.getCause();
            }
            if (throwable instanceof SdkException sdkException) {
                throw translateException(path, sdkException);
            }
            throw new IOException(throwable);
        }
    }

    /**
     * Translates an exception to the select operation like S3A does.
     * S3 sends errors of the event stream as messages with the code and the
     * message in the ":error-code" and ":error-message" headers. SDK v2 does
     * not parse them for S3 (REST-XML protocol), so they are added here.
     *
     * @param path the object path, used in the message
     * @param e    the exception
     * @return the translated exception
     */
    public IOException translateException(String path, SdkException e) {
        if (e instanceof AwsServiceException serviceException) {
            AwsErrorDetails details = serviceException.awsErrorDetails();
            SdkHttpResponse response = details == null ? null : details.sdkHttpResponse();
            Optional<String> code = response == null ? Optional.empty() : response.firstMatchingHeader(":error-code");
            if (code.isPresent() && details.errorCode() == null) {
                String message = response.firstMatchingHeader(":error-message").orElse("");
                return new IOException(String.format("S3 returned an error: %s (%s)", message, code.get()), e);
            }
        }
        try {
            return S3AUtils.translateException("S3 Select", path, e);
        } catch (RuntimeException translationError) {
            Throwable rootCause = ExceptionUtils.getRootCause(e);
            String message = rootCause == e ? e.toString() : e + ": " + rootCause;
            return new IOException("S3 Select on " + path + ": " + message, e);
        }
    }

    private static class Handler implements SelectObjectContentResponseHandler {
        private final CompletableFuture<SdkPublisher<SelectObjectContentEventStream>> publisherFuture = new CompletableFuture<>();

        CompletableFuture<S3SelectEventStream> eventPublisher(CompletableFuture<Void> selectOperationFuture) {
            return publisherFuture.thenApply(publisher ->
                    new S3SelectEventStream(selectOperationFuture, publisher));
        }

        @Override
        public void responseReceived(SelectObjectContentResponse selectObjectContentResponse) {
        }

        @Override
        public void onEventStream(SdkPublisher<SelectObjectContentEventStream> publisher) {
            publisherFuture.complete(publisher);
        }

        @Override
        public void exceptionOccurred(Throwable error) {
            publisherFuture.completeExceptionally(error);
        }

        @Override
        public void complete() {
        }
    }
}
