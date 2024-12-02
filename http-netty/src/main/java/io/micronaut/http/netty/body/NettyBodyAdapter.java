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
package io.micronaut.http.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.body.AvailableByteBody;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.netty.EventLoopFlow;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaders;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/**
 * Adapter from generic streaming {@link ByteBody} to {@link StreamingNettyByteBody}.
 *
 * @author Jonas Konrad
 * @since 4.6.0
 */
@Internal
public final class NettyBodyAdapter implements BufferConsumer.Upstream, Subscriber<ByteBuf> {
    private final EventLoopFlow eventLoopFlow;
    private final Publisher<ByteBuf> source;
    @Nullable
    private final Runnable onDiscard;

    private volatile boolean cancelled;
    private volatile Subscription subscription;
    private StreamingNettyByteBody.SharedBuffer sharedBuffer;
    private final AtomicLong demand = new AtomicLong(1);

    private NettyBodyAdapter(EventLoop eventLoop, Publisher<ByteBuf> source, @Nullable Runnable onDiscard) {
        this.eventLoopFlow = new EventLoopFlow(eventLoop);
        this.source = source;
        this.onDiscard = onDiscard;
    }

    /**
     * Transform the given body to a {@link NettyByteBody}.
     *
     * @param body The generic body
     * @param eventLoop The event loop for task serialization
     * @return The adapted body
     */
    @NonNull
    public static NettyByteBody adapt(@NonNull ByteBody body, @NonNull EventLoop eventLoop) {
        if (body instanceof NettyByteBody nbb) {
            return nbb;
        }
        if (body instanceof AvailableByteBody available) {
            return new AvailableNettyByteBody(Unpooled.wrappedBuffer(available.toByteArray()));
        }
        NettyBodyAdapter adapter = new NettyBodyAdapter(eventLoop, NettyByteBody.toByteBufs(body), null);
        adapter.sharedBuffer = new StreamingNettyByteBody.SharedBuffer(eventLoop, BodySizeLimits.UNLIMITED, adapter);
        body.expectedLength().ifPresent(adapter.sharedBuffer::setExpectedLength);
        return new StreamingNettyByteBody(adapter.sharedBuffer);
    }

    public static StreamingNettyByteBody adapt(Publisher<ByteBuf> publisher, EventLoop eventLoop) {
        return adapt(publisher, eventLoop, null, null);
    }

    public static StreamingNettyByteBody adapt(Publisher<ByteBuf> publisher, EventLoop eventLoop, @Nullable HttpHeaders headersForLength, @Nullable Runnable onDiscard) {
        NettyBodyAdapter adapter = new NettyBodyAdapter(eventLoop, publisher, onDiscard);
        adapter.sharedBuffer = new StreamingNettyByteBody.SharedBuffer(eventLoop, BodySizeLimits.UNLIMITED, adapter);
        if (headersForLength != null) {
            adapter.sharedBuffer.setExpectedLengthFrom(headersForLength);
        }
        return new StreamingNettyByteBody(adapter.sharedBuffer);
    }

    @Override
    public void start() {
        source.subscribe(this);
    }

    @Override
    public void onBytesConsumed(long bytesConsumed) {
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
    public void allowDiscard() {
        cancelled = true;
        if (subscription != null) {
            subscription.cancel();
        }
        if (onDiscard != null) {
            onDiscard.run();
        }
    }

    @Override
    public void disregardBackpressure() {
        this.demand.set(Long.MAX_VALUE);
        if (subscription != null) {
            subscription.request(Long.MAX_VALUE);
        }
    }

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
        if (cancelled) {
            s.cancel();
        } else {
            s.request(1);
        }
    }

    @Override
    public void onNext(ByteBuf bytes) {
        if (eventLoopFlow.executeNow(() -> onNext0(bytes))) {
            onNext0(bytes);
        }
    }

    private void onNext0(ByteBuf bytes) {
        long newDemand = demand.addAndGet(-bytes.readableBytes());
        sharedBuffer.add(bytes);
        if (newDemand > 0) {
            subscription.request(1);
        }
    }

    @Override
    public void onError(Throwable t) {
        if (eventLoopFlow.executeNow(() -> sharedBuffer.error(t))) {
            sharedBuffer.error(t);
        }
    }

    @Override
    public void onComplete() {
        if (eventLoopFlow.executeNow(() -> sharedBuffer.complete())) {
            sharedBuffer.complete();
        }
    }

}
