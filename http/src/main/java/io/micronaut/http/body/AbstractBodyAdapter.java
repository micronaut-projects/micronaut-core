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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BufferConsumer;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * Base implementation for an adapter that transforms a {@link Publisher} of buffers to a
 * {@link ByteBody}.
 *
 * @param <B> The input buffer type
 * @param <S> The output {@link BaseSharedBuffer} the buffers are forwarded to
 * @since 4.8.0
 * @author Jonas Konrad
 */
@Internal
public abstract class AbstractBodyAdapter<B, S extends BaseSharedBuffer<?, ?>> implements BufferConsumer.Upstream, Subscriber<B> {
    protected S sharedBuffer;

    protected volatile Subscription subscription;
    protected final AtomicLong demand = new AtomicLong(1);

    private final Publisher<B> source;
    @Nullable
    private final Runnable onDiscard;
    private volatile boolean cancelled;

    public AbstractBodyAdapter(@NonNull Publisher<B> source, @Nullable Runnable onDiscard) {
        this.source = source;
        this.onDiscard = onDiscard;
    }

    @Override
    public final void start() {
        source.subscribe(this);
    }

    @Override
    public final void onBytesConsumed(long bytesConsumed) {
        if (bytesConsumed < 0) {
            throw new IllegalArgumentException("Negative bytes consumed");
        }

        // clamping add
        LongUnaryOperator add = l -> l + bytesConsumed < l ? Long.MAX_VALUE : l + bytesConsumed;
        long oldDemand = this.demand.getAndUpdate(add);
        long newDemand = add.applyAsLong(oldDemand);
        if (oldDemand <= 0 && newDemand > 0) {
            subscription.request(1);
        }
    }

    @Override
    public final void allowDiscard() {
        cancelled = true;
        if (subscription != null) {
            subscription.cancel();
        }
        if (onDiscard != null) {
            onDiscard.run();
        }
    }

    @Override
    public final void disregardBackpressure() {
        this.demand.set(Long.MAX_VALUE);
        if (subscription != null) {
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public final void onSubscribe(Subscription s) {
        this.subscription = s;
        if (cancelled) {
            s.cancel();
        } else {
            s.request(1);
        }
    }

    @Override
    public void onError(Throwable t) {
        sharedBuffer.error(t);
    }

    @Override
    public void onComplete() {
        sharedBuffer.complete();
    }

}
