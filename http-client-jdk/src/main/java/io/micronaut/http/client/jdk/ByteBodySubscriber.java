/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.client.jdk;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.DelayedSubscriber;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReactiveByteBufferByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * {@link HttpResponse.BodySubscriber} implementation that pushes data into a
 * {@link ReactiveByteBufferByteBody.SharedBuffer}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class ByteBodySubscriber implements HttpResponse.BodySubscriber<CloseableByteBody> {
    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    private final DelayedSubscriber<ReadBuffer> defer = new DelayedSubscriber<>();
    private final CloseableByteBody mapped;
    /**
     * The longest wait for the next piece of the body while the consumer has demand, or
     * {@code null} for no limit. The request timeout of the JDK client ends when the headers
     * arrive, so a stalled body is only caught here.
     */
    private final @Nullable Duration readTimeout;
    private Flow.@Nullable Subscription upstream;
    private long outstanding;
    private boolean done;
    /**
     * Whether a piece of the body is being delivered. The timeout neither signals nor runs meanwhile:
     * it is armed again once the consumer returns.
     */
    private boolean emitting;
    private @Nullable Disposable timeoutTask;
    /**
     * Incremented whenever the timeout is cancelled or armed, so a timer that already started
     * before it was cancelled does not act.
     */
    private long timeoutGeneration;

    public ByteBodySubscriber(BodySizeLimits limits) {
        this(limits, null);
    }

    ByteBodySubscriber(BodySizeLimits limits, @Nullable Duration readTimeout) {
        this.mapped = BODY_FACTORY.adapt(defer, limits, null, null);
        this.readTimeout = readTimeout == null || readTimeout.isNegative() || readTimeout.isZero() ? null : readTimeout;
    }

    @Override
    public CompletionStage<CloseableByteBody> getBody() {
        return CompletableFuture.completedFuture(mapped);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        synchronized (this) {
            upstream = subscription;
        }
        defer.onSubscribe(new Subscription() {
            @Override
            public void request(long n) {
                demand(n);
                subscription.request(n);
            }

            @Override
            public void cancel() {
                finish();
                subscription.cancel();
            }
        });
    }

    private synchronized void demand(long n) {
        if (readTimeout == null || done || n <= 0) {
            return;
        }
        boolean idle = outstanding == 0;
        outstanding = outstanding + n < 0 ? Long.MAX_VALUE : outstanding + n;
        if (idle && !emitting) {
            restartTimeout();
        }
    }

    /**
     * Starts delivering a piece of the body.
     *
     * @return Whether it is delivered, {@code false} once the body is done
     */
    private synchronized boolean startEmitting() {
        if (done) {
            return false;
        }
        emitting = true;
        if (readTimeout != null) {
            if (outstanding != Long.MAX_VALUE && outstanding > 0) {
                outstanding--;
            }
            cancelTimeout();
        }
        return true;
    }

    /**
     * The consumer handled a piece of the body: wait for the next one, if it has demand.
     */
    private synchronized void emitted() {
        emitting = false;
        if (readTimeout != null && !done && outstanding > 0) {
            restartTimeout();
        }
    }

    private void restartTimeout() {
        cancelTimeout();
        Duration timeout = readTimeout;
        if (timeout != null) {
            long generation = ++timeoutGeneration;
            timeoutTask = Schedulers.parallel().schedule(() -> timedOut(generation), timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private void cancelTimeout() {
        timeoutGeneration++;
        Disposable task = timeoutTask;
        if (task != null) {
            task.dispose();
            timeoutTask = null;
        }
    }

    private synchronized boolean finish() {
        if (done) {
            return false;
        }
        done = true;
        cancelTimeout();
        return true;
    }

    private void timedOut(long generation) {
        Flow.Subscription s;
        synchronized (this) {
            if (generation != timeoutGeneration || done || outstanding == 0 || emitting) {
                // a piece of the body arrived, the timeout is armed again once it is handled
                return;
            }
            finish();
            s = upstream;
        }
        if (s != null) {
            s.cancel();
        }
        defer.onError(ReadTimeoutException.TIMEOUT_EXCEPTION);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (!startEmitting()) {
            return;
        }
        try {
            if (item.isEmpty()) {
                defer.onNext(BODY_FACTORY.readBufferFactory().createEmpty());
            } else if (item.size() == 1) {
                defer.onNext(BODY_FACTORY.readBufferFactory().adapt(item.get(0)));
            } else {
                List<ReadBuffer> buffers = new ArrayList<>(item.size());
                for (ByteBuffer byteBuffer : item) {
                    buffers.add(BODY_FACTORY.readBufferFactory().adapt(byteBuffer));
                }
                defer.onNext(BODY_FACTORY.readBufferFactory().compose(buffers));
            }
        } finally {
            emitted();
        }
    }

    @Override
    public void onError(Throwable throwable) {
        if (finish()) {
            defer.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        if (finish()) {
            defer.onComplete();
        }
    }
}
