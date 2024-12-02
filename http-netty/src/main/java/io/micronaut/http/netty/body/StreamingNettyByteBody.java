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
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.PublisherAsBlocking;
import io.micronaut.http.body.stream.UpstreamBalancer;
import io.micronaut.http.netty.PublisherAsStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ReferenceCounted;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.List;
import java.util.OptionalLong;
import java.util.function.Supplier;

/**
 * Netty implementation for streaming ByteBody.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Internal
public final class StreamingNettyByteBody extends NettyByteBody implements CloseableByteBody {
    private final SharedBuffer sharedBuffer;
    /**
     * We have reserve, subscribe, and add calls in {@link SharedBuffer} that all modify the same
     * data structures. They can all happen concurrently and must be moved to the event loop. We
     * also need to ensure that a reserve and associated subscribe stay serialized
     * ({@link io.micronaut.http.netty.EventLoopFlow} semantics). But because of the potential
     * concurrency, we actually need stronger semantics than
     * {@link io.micronaut.http.netty.EventLoopFlow}.
     * <p>
     * The solution is to use the old {@link EventLoop#inEventLoop()} + {@link EventLoop#execute}
     * pattern. Serialization semantics for reserve to subscribe are guaranteed using this field:
     * If the reserve call is delayed, this field is {@code true}, and the subscribe call will also
     * be delayed. This approach is possible because we only need to serialize a single reserve
     * with a single subscribe.
     */
    private final boolean forceDelaySubscribe;
    private BufferConsumer.Upstream upstream;

    public StreamingNettyByteBody(SharedBuffer sharedBuffer) {
        this(sharedBuffer, false, sharedBuffer.getRootUpstream());
    }

    private StreamingNettyByteBody(SharedBuffer sharedBuffer, boolean forceDelaySubscribe, BufferConsumer.Upstream upstream) {
        this.sharedBuffer = sharedBuffer;
        this.forceDelaySubscribe = forceDelaySubscribe;
        this.upstream = upstream;
    }

    public BufferConsumer.Upstream primary(ByteBufConsumer primary) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        sharedBuffer.subscribe(primary, upstream, forceDelaySubscribe);
        return upstream;
    }

    @Override
    public @NonNull CloseableByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        UpstreamBalancer.UpstreamPair pair = UpstreamBalancer.balancer(upstream, backpressureMode);
        this.upstream = pair.left();
        boolean forceDelaySubscribe = this.sharedBuffer.reserve();
        return new StreamingNettyByteBody(sharedBuffer, forceDelaySubscribe, pair.right());
    }

    @Override
    public @NonNull StreamingNettyByteBody allowDiscard() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        upstream.allowDiscard();
        return this;
    }

    @Override
    protected Flux<ByteBuf> toByteBufPublisher() {
        AsFlux asFlux = new AsFlux(sharedBuffer);
        BufferConsumer.Upstream upstream = primary(asFlux);
        return asFlux.asFlux(upstream)
            .doOnDiscard(ByteBuf.class, ReferenceCounted::release);
    }

    @Override
    public @NonNull OptionalLong expectedLength() {
        return sharedBuffer.getExpectedLength();
    }

    @Override
    public @NonNull InputStream toInputStream() {
        PublisherAsBlocking<ByteBuf> blocking = new PublisherAsBlocking<>() {
            @Override
            protected void release(ByteBuf item) {
                item.release();
            }
        };
        toByteBufPublisher().subscribe(blocking);
        return new PublisherAsStream(blocking);
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        upstream.start();
        upstream.onBytesConsumed(Long.MAX_VALUE);
        return sharedBuffer.subscribeFull(upstream, forceDelaySubscribe).map(AvailableNettyByteBody::new);
    }

    @Override
    public @NonNull CloseableByteBody move() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        return new StreamingNettyByteBody(sharedBuffer, forceDelaySubscribe, upstream);
    }

    @Override
    public void close() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            return;
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        upstream.allowDiscard();
        upstream.disregardBackpressure();
        upstream.start();
        sharedBuffer.subscribe(null, upstream, forceDelaySubscribe);
    }

    private static final class AsFlux extends BaseSharedBuffer.AsFlux<ByteBuf> implements ByteBufConsumer {
        public AsFlux(BaseSharedBuffer<?, ?> sharedBuffer) {
            super(sharedBuffer);
        }

        @Override
        public void add(ByteBuf buf) {
            if (!add0(buf)) {
                buf.release();
            }
        }

        @Override
        protected int size(ByteBuf buf) {
            return buf.readableBytes();
        }
    }

    /**
     * This class buffers input data and distributes it to multiple {@link StreamingNettyByteBody}
     * instances.
     * <p>Thread safety: The {@link ByteBufConsumer} methods <i>must</i> only be called from one
     * thread, the {@link #eventLoop} thread. The other methods (subscribe, reserve) can be
     * called from any thread.
     */
    public static final class SharedBuffer extends BaseSharedBuffer<ByteBufConsumer, ByteBuf> implements ByteBufConsumer {
        private static final Supplier<ResourceLeakDetector<SharedBuffer>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(SharedBuffer.class));

        @Nullable
        private final ResourceLeakTracker<SharedBuffer> tracker = LEAK_DETECTOR.get().track(this);

        private final EventLoop eventLoop;
        /**
         * Buffered data. This is forwarded to new subscribers.
         */
        private CompositeByteBuf buffer;
        /**
         * Active subscribers that need the fully buffered body.
         */
        private List<@NonNull DelayedExecutionFlow<ByteBuf>> fullSubscribers;
        private ByteBuf addingBuffer;

        public SharedBuffer(EventLoop loop, BodySizeLimits limits, Upstream rootUpstream) {
            super(limits, rootUpstream);
            this.eventLoop = loop;
        }

        public void setExpectedLengthFrom(HttpHeaders headers) {
            setExpectedLengthFrom(headers.get(HttpHeaderNames.CONTENT_LENGTH));
        }

        boolean reserve() {
            if (eventLoop.inEventLoop() && addingBuffer == null) {
                reserve0();
                return false;
            } else {
                eventLoop.execute(this::reserve0);
                return true;
            }
        }

        @Override
        protected void reserve0() {
            super.reserve0();
            if (tracker != null) {
                tracker.record();
            }
        }

        /**
         * Add a subscriber. Must be preceded by a reservation.
         *
         * @param subscriber       The subscriber to add. Can be {@code null}, then the bytes will just be discarded
         * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
         * @param forceDelay       Whether to require an {@link EventLoop#execute} call to ensure serialization with previous {@link #reserve()} call
         */
        void subscribe(@Nullable ByteBufConsumer subscriber, Upstream specificUpstream, boolean forceDelay) {
            if (!forceDelay && eventLoop.inEventLoop() && addingBuffer == null) {
                subscribe0(subscriber, specificUpstream);
            } else {
                eventLoop.execute(() -> subscribe0(subscriber, specificUpstream));
            }
        }

        @Override
        protected void forwardInitialBuffer(@Nullable ByteBufConsumer subscriber, boolean last) {
            if (subscriber != null) {
                if (buffer != null) {
                    if (last) {
                        subscriber.add(buffer.slice());
                        buffer = null;
                    } else {
                        subscriber.add(buffer.retainedSlice());
                    }
                }
            } else {
                if (buffer != null && last) {
                    buffer.release();
                    buffer = null;
                }
            }
        }

        @Override
        protected void afterSubscribe(boolean last) {
            if (tracker != null) {
                if (last) {
                    tracker.close(this);
                } else {
                    tracker.record();
                }
            }
        }

        @Override
        protected ByteBuf subscribeFullResult(boolean last) {
            if (buffer == null) {
                return Unpooled.EMPTY_BUFFER;
            } else if (last) {
                ByteBuf buf = buffer;
                buffer = null;
                return buf;
            } else {
                return buffer.retainedSlice();
            }
        }

        /**
         * Optimized version of {@link #subscribe} for subscribers that want to buffer the full
         * body.
         *
         * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
         * @param forceDelay       Whether to require an {@link EventLoop#execute} call to ensure serialization with previous {@link #reserve()} call
         * @return A flow that will complete when all data has arrived, with a buffer containing that data
         */
        ExecutionFlow<ByteBuf> subscribeFull(Upstream specificUpstream, boolean forceDelay) {
            DelayedExecutionFlow<ByteBuf> asyncFlow = DelayedExecutionFlow.create();
            if (!forceDelay && eventLoop.inEventLoop() && addingBuffer == null) {
                return subscribeFull0(asyncFlow, specificUpstream, true);
            } else {
                eventLoop.execute(() -> {
                    ExecutionFlow<ByteBuf> res = subscribeFull0(asyncFlow, specificUpstream, false);
                    assert res == asyncFlow;
                });
                return asyncFlow;
            }
        }

        @Override
        public void add(ByteBuf buf) {
            addingBuffer = buf.touch();
            add(buf.readableBytes());
            addingBuffer = null;
        }

        @Override
        protected void addForward(List<ByteBufConsumer> consumers) {
            for (ByteBufConsumer consumer : consumers) {
                consumer.add(addingBuffer.retainedSlice());
            }
        }

        @Override
        protected void addBuffer() {
            if (buffer == null) {
                buffer = addingBuffer.alloc().compositeBuffer();
            }
            buffer.addComponent(true, addingBuffer);
        }

        @Override
        protected void addDoNotBuffer() {
            addingBuffer.release();
        }

        @Override
        protected void discardBuffer() {
            if (buffer != null) {
                buffer.release();
                buffer = null;
            }
        }
    }
}
