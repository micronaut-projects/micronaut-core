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
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.ReactiveByteBufferByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * {@link HttpResponse.BodySubscriber} implementation that pushes data into a
 * {@link ReactiveByteBufferByteBody.SharedBuffer}.
 *
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
final class ByteBodySubscriber implements HttpResponse.BodySubscriber<CloseableByteBody>, BufferConsumer.Upstream {
    private final ReactiveByteBufferByteBody.SharedBuffer sharedBuffer;
    private final CloseableByteBody root;
    private final AtomicLong demand = new AtomicLong(0);
    private Flow.Subscription subscription;
    private boolean cancelled;
    private volatile boolean disregardBackpressure;

    public ByteBodySubscriber(BodySizeLimits limits) {
        sharedBuffer = new ReactiveByteBufferByteBody.SharedBuffer(limits, this);
        root = new ReactiveByteBufferByteBody(sharedBuffer);
    }

    @Override
    public CompletionStage<CloseableByteBody> getBody() {
        return CompletableFuture.completedFuture(root);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        boolean initialDemand;
        boolean cancelled;
        synchronized (this) {
            this.subscription = subscription;
            cancelled = this.cancelled;
            initialDemand = demand.get() > 0;
        }
        if (cancelled) {
            subscription.cancel();
        } else if (initialDemand) {
            subscription.request(disregardBackpressure ? Long.MAX_VALUE : 1);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> item) {
        for (ByteBuffer buffer : item) {
            int n = buffer.remaining();
            demand.addAndGet(-n);
            sharedBuffer.add(buffer);
        }
        if (demand.get() > 0) {
            subscription.request(1);
        }
    }

    @Override
    public void onError(Throwable throwable) {
        sharedBuffer.error(throwable);
    }

    @Override
    public void onComplete() {
        sharedBuffer.complete();
    }

    @Override
    public void start() {
        Flow.Subscription initialDemand;
        synchronized (this) {
            initialDemand = subscription;
            demand.set(1);
        }
        if (initialDemand != null) {
            initialDemand.request(1);
        }
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
        long prev = demand.getAndAdd(bytesConsumed);
        if (prev <= 0 && prev + bytesConsumed > 0) {
            subscription.request(1);
        }
    }

    @Override
    public void allowDiscard() {
        Flow.Subscription subscription;
        synchronized (this) {
            cancelled = true;
            subscription = this.subscription;
        }
        if (subscription != null) {
            subscription.cancel();
        }
    }

    @Override
    public void disregardBackpressure() {
        disregardBackpressure = true;
        if (subscription != null) {
            subscription.request(Long.MAX_VALUE);
        }
    }
}
