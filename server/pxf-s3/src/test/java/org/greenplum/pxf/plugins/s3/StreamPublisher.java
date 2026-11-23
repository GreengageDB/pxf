package org.greenplum.pxf.plugins.s3;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import software.amazon.awssdk.core.async.SdkPublisher;

import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

final class StreamPublisher<T> implements SdkPublisher<T> {
    private final Executor executor;
    private final Iterator<T> iterator;
    private volatile boolean done = false;

    StreamPublisher(Stream<T> data, Executor executor) {
        this.iterator = data.iterator();
        this.executor = executor;
    }

    StreamPublisher(Stream<T> data) {
        this(data, Runnable::run);
    }

    boolean isDone() {
        return done;
    }

    @Override
    public void subscribe(Subscriber<? super T> subscriber) {
        subscriber.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                if (done) {
                    return;
                }

                if (n < 1) {
                    done = true;
                    executor.execute(() -> subscriber.onError(new IllegalArgumentException()));
                    return;
                }

                for (long i = 0; i < n; i++) {
                    final T value;
                    try {
                        synchronized (iterator) {
                            value = iterator.hasNext() ? iterator.next() : null;
                        }
                    } catch (Throwable e) {
                        executor.execute(() -> subscriber.onError(e));
                        break;
                    }

                    if (value == null) {
                        done = true;
                        executor.execute(subscriber::onComplete);
                        break;
                    } else {
                        executor.execute(() -> subscriber.onNext(value));
                    }
                }
            }

            @Override
            public void cancel() {
                done = true;
            }
        });
    }
}
