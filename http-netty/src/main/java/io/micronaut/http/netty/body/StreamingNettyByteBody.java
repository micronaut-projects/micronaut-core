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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.body.CloseableAvailableByteBody;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.body.stream.BaseSharedBuffer;
import io.micronaut.http.body.stream.BaseStreamingByteBody;
import io.micronaut.http.body.stream.BodySizeLimits;
import io.micronaut.http.body.stream.BufferConsumer;
import io.micronaut.http.body.stream.UpstreamBalancer;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;

import java.util.function.Supplier;

/**
 * Netty implementation for streaming ByteBody.
 *
 * @since 4.5.0
 * @author Jonas Konrad
 */
@Internal
public final class StreamingNettyByteBody extends BaseStreamingByteBody<StreamingNettyByteBody.SharedBuffer> implements CloseableByteBody {
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

    public StreamingNettyByteBody(SharedBuffer sharedBuffer) {
        this(sharedBuffer, false, sharedBuffer.getRootUpstream());
    }

    private StreamingNettyByteBody(SharedBuffer sharedBuffer, boolean forceDelaySubscribe, BufferConsumer.Upstream upstream) {
        super(sharedBuffer, upstream);
        this.forceDelaySubscribe = forceDelaySubscribe;
    }

    @Override
    public BufferConsumer.Upstream primary(BufferConsumer primary) {
        touch();
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
    protected BaseStreamingByteBody<SharedBuffer> derive(BufferConsumer.Upstream upstream) {
        return new StreamingNettyByteBody(sharedBuffer, forceDelaySubscribe, upstream);
    }

    @Override
    public @NonNull CloseableByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        touch();
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
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            BaseSharedBuffer.failClaim();
        }
        this.upstream = null;
        BaseSharedBuffer.logClaim();
        upstream.start();
        upstream.onBytesConsumed(Long.MAX_VALUE);
        return sharedBuffer.subscribeFull(upstream, forceDelaySubscribe).map(sharedBuffer.byteBodyFactory::adapt);
    }

    @Override
    public void close() {
        touch();
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

    @Override
    public void touch() {
        ResourceLeakTracker<SharedBuffer> tracker = sharedBuffer.tracker;
        if (tracker != null) {
            tracker.record();
        }
    }

    /**
     * This class buffers input data and distributes it to multiple {@link StreamingNettyByteBody}
     * instances.
     * <p>Thread safety: The {@link BufferConsumer} methods <i>must</i> only be called from one
     * thread, the {@link #eventLoop} thread. The other methods (subscribe, reserve) can be
     * called from any thread.
     */
    @Internal
    public static final class SharedBuffer extends BaseSharedBuffer {
        private static final Supplier<ResourceLeakDetector<SharedBuffer>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(SharedBuffer.class));

        @Nullable
        private final ResourceLeakTracker<SharedBuffer> tracker = LEAK_DETECTOR.get().track(this);

        private final EventLoop eventLoop;
        private final NettyByteBodyFactory byteBodyFactory;
        private boolean adding = false;

        public SharedBuffer(EventLoop loop, NettyByteBodyFactory byteBodyFactory, BodySizeLimits limits, Upstream rootUpstream) {
            super(byteBodyFactory.readBufferFactory(), limits, rootUpstream);
            this.eventLoop = loop;
            this.byteBodyFactory = byteBodyFactory;
        }

        public EventLoop eventLoop() {
            return eventLoop;
        }

        public void setExpectedLengthFrom(HttpHeaders headers) {
            setExpectedLengthFrom(headers.get(HttpHeaderNames.CONTENT_LENGTH));
        }

        boolean reserve() {
            if (eventLoop.inEventLoop() && !adding) {
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
        void subscribe(@Nullable BufferConsumer subscriber, Upstream specificUpstream, boolean forceDelay) {
            if (!forceDelay && eventLoop.inEventLoop() && !adding) {
                subscribe0(subscriber, specificUpstream);
            } else {
                eventLoop.execute(() -> subscribe0(subscriber, specificUpstream));
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

        /**
         * Optimized version of {@link #subscribe} for subscribers that want to buffer the full
         * body.
         *
         * @param specificUpstream The upstream for the subscriber. This is used to call allowDiscard if there was an error
         * @param forceDelay       Whether to require an {@link EventLoop#execute} call to ensure serialization with previous {@link #reserve()} call
         * @return A flow that will complete when all data has arrived, with a buffer containing that data
         */
        ExecutionFlow<ReadBuffer> subscribeFull(Upstream specificUpstream, boolean forceDelay) {
            DelayedExecutionFlow<ReadBuffer> asyncFlow = DelayedExecutionFlow.create();
            if (!forceDelay && eventLoop.inEventLoop() && !adding) {
                return subscribeFull0(asyncFlow, specificUpstream, true);
            } else {
                eventLoop.execute(() -> {
                    ExecutionFlow<ReadBuffer> res = subscribeFull0(asyncFlow, specificUpstream, false);
                    assert res == asyncFlow;
                });
                return asyncFlow;
            }
        }

        @Override
        public void add(ReadBuffer rb) {
            assert eventLoop.inEventLoop();
            adding = true;
            try {
                super.add(rb);
            } finally {
                adding = false;
            }
        }
    }
}
