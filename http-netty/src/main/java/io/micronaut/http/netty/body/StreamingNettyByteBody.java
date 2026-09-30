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
import io.micronaut.core.convert.ConversionService;
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
import io.micronaut.http.netty.NettyHttpHeaders;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.ResourceLeakDetector;
import io.netty.util.ResourceLeakDetectorFactory;
import io.netty.util.ResourceLeakTracker;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    boolean isCompatible(EventLoop eventLoop) {
        return sharedBuffer.eventLoop == eventLoop;
    }

    @Override
    public BufferConsumer.Upstream primary(@Nullable BufferConsumer primary) {
        touch();
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        recordPrimaryOp();
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
    public CloseableByteBody split(SplitBackpressureMode backpressureMode) {
        touch();
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        UpstreamBalancer.UpstreamPair pair = UpstreamBalancer.balancer(upstream, backpressureMode);
        this.upstream = pair.left();
        boolean forceDelaySubscribe = this.sharedBuffer.reserve();
        return new StreamingNettyByteBody(sharedBuffer, forceDelaySubscribe, pair.right());
    }

    @Override
    public ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        recordPrimaryOp();
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
        recordClosed();
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
        /**
         * Number of {@link #reserve()} calls queued on the event loop. While there are any, a
         * subscribe is queued behind them, even one from another body of this buffer: a body
         * closed on the event loop must not take the last reservation before a split made off
         * the event loop has reserved its own.
         */
        private final AtomicInteger pendingReservations = new AtomicInteger();
        /**
         * Whether a subscriber is being added. The bytes forwarded to a new subscriber can make
         * it ask for more, and a source that has bytes queued delivers them right away, back into
         * this buffer: such input is queued on the event loop behind the subscribe.
         */
        private boolean subscribing = false;
        /**
         * Number of inputs queued on the event loop, see {@link #subscribing}. The inputs that
         * follow are queued behind them to keep their order.
         */
        private int deferredInputs = 0;

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

        /**
         * Complete this buffer with the trailing headers of a
         * {@link io.netty.handler.codec.http.LastHttpContent}.
         *
         * @param trailingHeaders The trailing headers, possibly empty
         * @since 5.3.0
         */
        public void completeWithTrailers(HttpHeaders trailingHeaders) {
            if (trailingHeaders.isEmpty()) {
                complete();
            } else {
                complete(new NettyHttpHeaders(trailingHeaders, ConversionService.SHARED));
            }
        }

        @Override
        protected void submitDeferred(Runnable task) {
            eventLoop.execute(task);
        }

        /**
         * Queue an input that arrives while a subscriber is added, or behind the inputs queued
         * before it. A queued input runs in its turn, so it is given to this buffer directly. It
         * counts as queued until it has run: an input it triggers is queued behind it.
         *
         * @param input The input
         * @return {@code true} if the input is queued
         */
        private boolean deferInput(Runnable input) {
            if (!subscribing && deferredInputs == 0) {
                return false;
            }
            deferredInputs++;
            eventLoop.execute(() -> {
                try {
                    input.run();
                } finally {
                    deferredInputs--;
                }
            });
            return true;
        }

        /**
         * Run a subscribe on the event loop once no {@link #reserve()} is queued from off the
         * event loop. A reservation is counted before it is queued, so it can be queued behind
         * this task: the task then moves behind it.
         *
         * @param task The subscribe
         */
        private void executeAfterReservations(Runnable task) {
            eventLoop.execute(() -> {
                if (pendingReservations.get() > 0) {
                    executeAfterReservations(task);
                } else {
                    task.run();
                }
            });
        }

        @Override
        public void add(ReadBuffer rb) {
            if (!deferInput(() -> super.add(rb))) {
                super.add(rb);
            }
        }

        @Override
        public void addAndComplete(ReadBuffer rb) {
            if (!deferInput(() -> super.addAndComplete(rb))) {
                super.addAndComplete(rb);
            }
        }

        @Override
        public void complete() {
            if (!deferInput(super::complete)) {
                super.complete();
            }
        }

        @Override
        public void complete(io.micronaut.http.HttpHeaders trailers) {
            if (!deferInput(() -> super.complete(trailers))) {
                super.complete(trailers);
            }
        }

        @Override
        public void error(Throwable e) {
            if (!deferInput(() -> super.error(e))) {
                super.error(e);
            }
        }

        private void subscribeOnLoop(@Nullable BufferConsumer subscriber, Upstream specificUpstream) {
            // a subscriber can subscribe another split reentrantly: the inputs stay deferred
            // until the outer subscribe is done
            boolean outer = subscribing;
            subscribing = true;
            try {
                subscribe0(subscriber, specificUpstream);
            } finally {
                subscribing = outer;
            }
        }

        boolean reserve() {
            if (eventLoop.inEventLoop() && !adding) {
                reserve0();
                return false;
            } else {
                pendingReservations.incrementAndGet();
                eventLoop.execute(() -> {
                    try {
                        reserve0();
                    } finally {
                        pendingReservations.decrementAndGet();
                    }
                });
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
            if (!forceDelay && pendingReservations.get() == 0 && eventLoop.inEventLoop() && !adding) {
                subscribeOnLoop(subscriber, specificUpstream);
            } else {
                executeAfterReservations(() -> subscribeOnLoop(subscriber, specificUpstream));
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
            if (!forceDelay && pendingReservations.get() == 0 && eventLoop.inEventLoop() && !adding) {
                return subscribeFull0(asyncFlow, specificUpstream, true);
            } else {
                executeAfterReservations(() -> {
                    ExecutionFlow<ReadBuffer> res = subscribeFull0(asyncFlow, specificUpstream, false);
                    assert res == asyncFlow;
                });
                return asyncFlow;
            }
        }

        @Override
        protected @Nullable List<ReadBuffer> addGuarded(ReadBuffer rb, boolean completeAfter) {
            if (!eventLoop.inEventLoop()) {
                throw new IllegalStateException("Must only be called on event loop");
            }
            adding = true;
            try {
                return super.addGuarded(rb, completeAfter);
            } finally {
                adding = false;
            }
        }
    }
}
