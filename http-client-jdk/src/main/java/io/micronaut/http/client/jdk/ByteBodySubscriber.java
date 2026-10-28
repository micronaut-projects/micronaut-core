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
import io.micronaut.core.async.publisher.DelayedSubscriber;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReactiveByteBufferByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.ResponseClosedException;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.EOFException;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * {@link HttpResponse.BodySubscriber} implementation that pushes data into a
 * {@link ReactiveByteBufferByteBody.SharedBuffer}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class ByteBodySubscriber implements HttpResponse.BodySubscriber<CloseableByteBody> {
    /**
     * The pieces are heap buffers that hold no resource, made on the threads of the JDK client:
     * they are not leak tracked.
     */
    private static final ByteBodyFactory BODY_FACTORY = new ByteBodyFactory(ByteArrayBufferFactory.INSTANCE, ReadBufferFactory.getUntrackedJdkFactory()) {
    };

    private final DelayedSubscriber<ReadBuffer> defer = new DelayedSubscriber<>();
    private final CloseableByteBody mapped;
    @Nullable
    private final Consumer<@Nullable Throwable> onEnd;
    private final AtomicBoolean ended = new AtomicBoolean();
    private final boolean clientFailures;
    /**
     * The longest wait for the next piece of the body while the consumer has demand, or
     * {@code null} for no limit. The request timeout of the JDK client ends when the headers
     * arrive, so a stalled body is only caught here.
     */
    private final @Nullable Duration readTimeout;
    private Flow.@Nullable Subscription upstream;
    private long outstanding;
    private boolean done;
    private @Nullable Disposable timeoutTask;

    public ByteBodySubscriber(BodySizeLimits limits) {
        this(limits, false, null);
    }

    /**
     * @param limits         The body size limits
     * @param clientFailures Whether an I/O failure of the body is passed on as an
     *                       {@link HttpClientException}, as the failures of the bodies of the
     *                       streams of the Netty client, instead of as it is
     * @param onEnd          Called once when the body ends: with {@code null} when it is
     *                       complete, or when its consumer let the rest go, and with the failure
     *                       of the JDK client when it failed
     */
    public ByteBodySubscriber(BodySizeLimits limits, boolean clientFailures, @Nullable Consumer<@Nullable Throwable> onEnd) {
        this(limits, clientFailures, onEnd, null);
    }

    /**
     * @param limits         The body size limits
     * @param clientFailures Whether an I/O failure of the body is passed on as an
     *                       {@link HttpClientException}, as the failures of the bodies of the
     *                       streams of the Netty client, instead of as it is
     * @param onEnd          Called once when the body ends: with {@code null} when it is
     *                       complete, or when its consumer let the rest go, and with the failure
     *                       of the JDK client when it failed
     * @param readTimeout    The longest wait for the next piece of the body while the consumer
     *                       has demand, or {@code null} for no limit
     */
    ByteBodySubscriber(BodySizeLimits limits, boolean clientFailures, @Nullable Consumer<@Nullable Throwable> onEnd, @Nullable Duration readTimeout) {
        this.mapped = BODY_FACTORY.adapt(defer, limits, null, null);
        this.clientFailures = clientFailures;
        this.onEnd = onEnd;
        this.readTimeout = readTimeout == null || readTimeout.isNegative() || readTimeout.isZero() ? null : readTimeout;
    }

    private void end(@Nullable Throwable failure) {
        if (onEnd != null && ended.compareAndSet(false, true)) {
            onEnd.accept(failure);
        }
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
                // the consumer let the rest of the body go
                end(null);
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
        if (idle) {
            restartTimeout();
        }
    }

    private synchronized void received() {
        if (readTimeout == null || done) {
            return;
        }
        if (outstanding != Long.MAX_VALUE) {
            outstanding--;
        }
        if (outstanding > 0) {
            restartTimeout();
        } else {
            cancelTimeout();
        }
    }

    private void restartTimeout() {
        cancelTimeout();
        Duration timeout = readTimeout;
        if (timeout != null) {
            timeoutTask = Schedulers.parallel().schedule(this::timedOut, timeout.toNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private void cancelTimeout() {
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

    private void timedOut() {
        Flow.Subscription s;
        synchronized (this) {
            if (done || outstanding == 0) {
                return;
            }
            finish();
            s = upstream;
        }
        if (s != null) {
            s.cancel();
        }
        // reported to the load balancer as a timeout
        end(new HttpTimeoutException("Read Timeout while reading the response body"));
        defer.onError(new ReadTimeoutException(true));
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        synchronized (this) {
            if (done) {
                return;
            }
        }
        received();
        if (item.isEmpty()) {
            defer.onNext(BODY_FACTORY.readBufferFactory().createEmpty());
        } else if (item.size() == 1) {
            defer.onNext(BODY_FACTORY.readBufferFactory().adapt(item.get(0)));
        } else {
            defer.onNext(BODY_FACTORY.readBufferFactory().adapt(concat(item)));
        }
    }

    /**
     * Copy the pieces into one array. The pieces of the JDK client are read-only heap buffers that
     * expose no array, so composing them as {@link ReadBuffer}s would copy each piece twice: once
     * out of the read-only buffer, and once more into the composite.
     *
     * @param item The pieces
     * @return The bytes of all pieces
     */
    private static byte[] concat(List<ByteBuffer> item) {
        int length = 0;
        for (ByteBuffer byteBuffer : item) {
            length = Math.addExact(length, byteBuffer.remaining());
        }
        byte[] bytes = new byte[length];
        int offset = 0;
        for (ByteBuffer byteBuffer : item) {
            int remaining = byteBuffer.remaining();
            byteBuffer.get(bytes, offset, remaining);
            offset += remaining;
        }
        return bytes;
    }

    @Override
    public void onError(Throwable throwable) {
        if (!finish()) {
            return;
        }
        if (isTruncatedBody(throwable)) {
            throwable = new ResponseClosedException("Connection closed before the response body was received completely", true);
        }
        end(throwable);
        if (clientFailures) {
            throwable = clientFailure(throwable);
        }
        defer.onError(throwable);
    }

    /**
     * @param failure A failure of the body
     * @return The failure as a client exception
     */
    private static Throwable clientFailure(Throwable failure) {
        if (failure instanceof HttpTimeoutException) {
            return new ReadTimeoutException(true);
        }
        if (failure instanceof IOException) {
            return new HttpClientException("Error occurred reading HTTP response: " + failure.getMessage(), failure);
        }
        return failure;
    }

    /**
     * Whether the JDK client reports a connection closed before the body was complete: an EOF, or
     * its messages for a fixed-length or a chunked body that ended early.
     */
    static boolean isTruncatedBody(Throwable throwable) {
        if (throwable instanceof EOFException) {
            return true;
        }
        if (throwable instanceof IOException) {
            String message = throwable.getMessage();
            return message != null && (message.startsWith("fixed content-length:") || message.startsWith("chunked transfer encoding"));
        }
        return false;
    }

    @Override
    public void onComplete() {
        if (!finish()) {
            return;
        }
        end(null);
        defer.onComplete();
    }
}
