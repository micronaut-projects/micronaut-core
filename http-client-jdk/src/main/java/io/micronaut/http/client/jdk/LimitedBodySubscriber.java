/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.http.client.exceptions.ContentLengthExceededException;
import org.jspecify.annotations.Nullable;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Body subscriber that fails and cancels the body once it exceeds the maximum content length,
 * or once no piece of the body arrives within the read timeout.
 *
 * @param <T> The body type
 * @since 5.2.21
 */
@Internal
final class LimitedBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
    private final HttpResponse.BodySubscriber<T> delegate;
    private final long maxContentLength;
    private final @Nullable Duration readTimeout;
    private final AtomicBoolean failed = new AtomicBoolean();
    private long received;
    private volatile Flow.@Nullable Subscription subscription;
    private volatile @Nullable Disposable timeoutTask;

    /**
     * @param delegate         The subscriber that reads the body
     * @param maxContentLength The maximum number of bytes of the body
     * @param readTimeout      The longest wait for the next piece of the body, or {@code null}
     *                         for none. The request timeout of the JDK client ends when the
     *                         headers arrive, so without it a stalled body is waited for forever
     */
    LimitedBodySubscriber(HttpResponse.BodySubscriber<T> delegate, long maxContentLength, @Nullable Duration readTimeout) {
        this.delegate = delegate;
        this.maxContentLength = maxContentLength;
        this.readTimeout = readTimeout == null || readTimeout.isNegative() || readTimeout.isZero() ? null : readTimeout;
    }

    @Override
    public CompletionStage<T> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        restartTimeout();
        delegate.onSubscribe(subscription);
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        if (failed.get()) {
            return;
        }
        for (ByteBuffer buffer : item) {
            received += buffer.remaining();
        }
        if (received > maxContentLength) {
            fail(new ContentLengthExceededException(maxContentLength, received));
            return;
        }
        restartTimeout();
        delegate.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        if (failed.compareAndSet(false, true)) {
            cancelTimeout();
            delegate.onError(throwable);
        }
    }

    @Override
    public void onComplete() {
        // settled once: a read timeout that fires as the body ends is not followed by a completion
        if (failed.compareAndSet(false, true)) {
            cancelTimeout();
            delegate.onComplete();
        }
    }

    private void fail(Throwable failure) {
        if (failed.compareAndSet(false, true)) {
            cancelTimeout();
            // the body fails with this failure first: cancelling makes the JDK client close the
            // connection, which it would otherwise report as a truncated body
            delegate.onError(failure);
            Flow.Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
        }
    }

    private void restartTimeout() {
        Duration timeout = readTimeout;
        if (timeout == null) {
            return;
        }
        cancelTimeout();
        timeoutTask = Schedulers.parallel().schedule(this::timedOut, timeout.toNanos(), TimeUnit.NANOSECONDS);
    }

    private void cancelTimeout() {
        Disposable task = timeoutTask;
        if (task != null) {
            task.dispose();
            timeoutTask = null;
        }
    }

    private void timedOut() {
        // mapped to a read timeout of the body, and reported to the load balancer as a timeout
        fail(new HttpTimeoutException("Read Timeout while reading the response body"));
    }
}
