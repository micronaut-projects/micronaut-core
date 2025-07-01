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
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.PublisherAsBlocking;
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
import reactor.core.publisher.Sinks;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicLong;
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
        this(sharedBuffer, false, sharedBuffer.rootUpstream);
    }

    private StreamingNettyByteBody(SharedBuffer sharedBuffer, boolean forceDelaySubscribe, BufferConsumer.Upstream upstream) {
        this.sharedBuffer = sharedBuffer;
        this.forceDelaySubscribe = forceDelaySubscribe;
        this.upstream = upstream;
    }

    public BufferConsumer.Upstream primary(BufferConsumer primary) {
        touch();
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        this.upstream = null;
        sharedBuffer.subscribe(primary, upstream, forceDelaySubscribe);
        return upstream;
    }

    @Override
    public @NonNull CloseableByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
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
    public @NonNull StreamingNettyByteBody allowDiscard() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        upstream.allowDiscard();
        return this;
    }

    @Override
    protected Flux<ByteBuf> toByteBufPublisher() {
        AtomicLong unconsumed = new AtomicLong(0);
        Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer();
        BufferConsumer.Upstream upstream = primary(new BufferConsumer() {
            @Override
            public void add(ByteBuf buf) {
                long newLength = unconsumed.addAndGet(buf.readableBytes());
                if (newLength > sharedBuffer.limits.maxBufferSize()) {
                    sink.tryEmitError(new BufferLengthExceededException(sharedBuffer.limits.maxBufferSize(), newLength));
                    buf.release();
                } else {
                    if (sink.tryEmitNext(buf) != Sinks.EmitResult.OK) {
                        buf.release();
                    }
                }
            }

            @Override
            public void complete() {
                sink.tryEmitComplete();
            }

            @Override
            public void error(Throwable e) {
                sink.tryEmitError(e);
            }
        });
        return sink.asFlux()
            .doOnSubscribe(s -> upstream.start())
            .doOnNext(bb -> {
                unconsumed.addAndGet(-bb.readableBytes());
                upstream.onBytesConsumed(bb.readableBytes());
            })
            .doOnDiscard(ByteBuf.class, ReferenceCounted::release)
            .doOnCancel(() -> {
                upstream.allowDiscard();
                upstream.disregardBackpressure();
            });
    }

    @Override
    public @NonNull OptionalLong expectedLength() {
        long l = sharedBuffer.expectedLength;
        return l < 0 ? OptionalLong.empty() : OptionalLong.of(l);
    }

    @Override
    public @NonNull InputStream toInputStream() {
        PublisherAsBlocking<ByteBuf> blocking = new PublisherAsBlocking<>();
        toByteBufPublisher().subscribe(blocking);
        return new PublisherAsStream(blocking);
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableAvailableByteBody> bufferFlow() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        this.upstream = null;
        upstream.start();
        upstream.onBytesConsumed(Long.MAX_VALUE);
        return sharedBuffer.subscribeFull(upstream, forceDelaySubscribe).map(AvailableNettyByteBody::new);
    }

    @Override
    public void close() {
        touch();
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            return;
        }
        this.upstream = null;
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
    public static final class SharedBuffer implements BufferConsumer {
        private static final Supplier<ResourceLeakDetector<SharedBuffer>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(SharedBuffer.class));

        @Nullable
        private final ResourceLeakTracker<SharedBuffer> tracker = LEAK_DETECTOR.get().track(this);

        private final EventLoop eventLoop;
        private final BodySizeLimits limits;
        /**
         * Upstream of all subscribers. This is only used to cancel incoming data if the max
         * request size is exceeded.
         */
        private final Upstream rootUpstream;
        /**
         * Buffered data. This is forwarded to new subscribers.
         */
        private CompositeByteBuf buffer;
        /**
         * Whether the input is complete.
         */
        private boolean complete;
        /**
         * Any stream error.
         */
        private Throwable error;
        /**
         * Number of reserved subscriber spots. A new subscription MUST be preceded by a
         * reservation, and every reservation MUST have a subscription.
         */
        private int reserved = 1;
        /**
         * Active subscribers.
         */
        private List<@NonNull BufferConsumer> subscribers;
        /**
         * Active subscribers that need the fully buffered body.
         */
        private List<@NonNull DelayedExecutionFlow<ByteBuf>> fullSubscribers;
        /**
         * This flag is only used in tests, to verify that the BufferConsumer methods arent called
         * in a reentrant fashion.
         */
        private boolean working = false;
        /**
         * {@code true} during {@link #add(ByteBuf)} to avoid reentrant subscribe or reserve calls.
         * Field must only be accessed on the event loop.
         */
        private boolean adding = false;
        /**
         * Number of bytes received so far.
         */
        private long lengthSoFar = 0;
        /**
         * The expected length of the whole body. This is -1 if we're uncertain, otherwise it must
         * be accurate. This can come from a content-length header, but it's also set once the full
         * body has been received.
         */
        private volatile long expectedLength = -1;

        public SharedBuffer(EventLoop loop, BodySizeLimits limits, Upstream rootUpstream) {
            this.eventLoop = loop;
            this.limits = limits;
            this.rootUpstream = rootUpstream;
        }

        public void setExpectedLengthFrom(HttpHeaders headers) {
            String s = headers.get(HttpHeaderNames.CONTENT_LENGTH);
            if (s == null) {
                return;
            }
            long parsed;
            try {
                parsed = Long.parseLong(s);
            } catch (NumberFormatException e) {
                return;
            }
            if (parsed < 0) {
                return;
            }
            if (parsed > limits.maxBodySize()) {
                error(new ContentLengthExceededException(limits.maxBodySize(), parsed));
            }
            setExpectedLength(parsed);
        }

        public void setExpectedLength(long length) {
            if (length < 0) {
                throw new IllegalArgumentException("Should be > 0");
            }
            this.expectedLength = length;
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

        private void reserve0() {
            if (reserved == 0) {
                throw new IllegalStateException("Cannot go from streaming state back to buffering state");
            }
            reserved++;
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

        private void subscribe0(@Nullable BufferConsumer subscriber, Upstream specificUpstream) {
            assert !working;

            if (reserved == 0) {
                throw new IllegalStateException("Need to reserve a spot first");
            }

            working = true;
            boolean last = --reserved == 0;
            if (subscriber != null) {
                if (subscribers == null) {
                    subscribers = new ArrayList<>(1);
                }
                subscribers.add(subscriber);
                if (buffer != null) {
                    if (last) {
                        subscriber.add(buffer.slice());
                        buffer = null;
                    } else {
                        subscriber.add(buffer.retainedSlice());
                    }
                }
                if (error != null) {
                    subscriber.error(error);
                } else if (lengthSoFar > limits.maxBufferSize()) {
                    subscriber.error(new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar));
                    specificUpstream.allowDiscard();
                }
                if (complete) {
                    subscriber.complete();
                }
            } else {
                if (buffer != null && last) {
                    buffer.release();
                    buffer = null;
                }
            }
            if (tracker != null) {
                if (last) {
                    tracker.close(this);
                } else {
                    tracker.record();
                }
            }
            working = false;
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
            if (!forceDelay && eventLoop.inEventLoop() && !adding) {
                return subscribeFull0(asyncFlow, specificUpstream, true);
            } else {
                eventLoop.execute(() -> {
                    ExecutionFlow<ByteBuf> res = subscribeFull0(asyncFlow, specificUpstream, false);
                    assert res == asyncFlow;
                });
                return asyncFlow;
            }
        }

        /**
         * On-loop version of {@link #subscribeFull}. The returned flow will complete when the
         * input is buffered. The returned flow will always be identical to the {@code targetFlow}
         * parameter IF {@code canReturnImmediate} is false. If {@code canReturnImmediate} is true,
         * this method will SOMETIMES return an immediate ExecutionFlow instead as an optimization.
         *
         * @param targetFlow The delayed flow to use if {@code canReturnImmediate} is false and/or
         *                   we have to wait for the result
         * @param canReturnImmediate Whether we can return an immediate ExecutionFlow instead of
         *                  {@code targetFlow}, when appropriate
         */
        private ExecutionFlow<ByteBuf> subscribeFull0(DelayedExecutionFlow<ByteBuf> targetFlow, Upstream specificUpstream, boolean canReturnImmediate) {
            assert !working;

            if (reserved <= 0) {
                throw new IllegalStateException("Need to reserve a spot first. This should not happen, StreamingNettyByteBody should guard against it");
            }

            ExecutionFlow<ByteBuf> ret = targetFlow;

            working = true;
            boolean last = --reserved == 0;
            Throwable error = this.error;
            if (error == null && lengthSoFar > limits.maxBufferSize()) {
                error = new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar);
                specificUpstream.allowDiscard();
            }
            if (error != null) {
                if (canReturnImmediate) {
                    ret = ExecutionFlow.error(error);
                } else {
                    targetFlow.completeExceptionally(error);
                }
            } else if (complete) {
                ByteBuf buf;
                if (buffer == null) {
                    buf = Unpooled.EMPTY_BUFFER;
                } else if (last) {
                    buf = buffer;
                    buffer = null;
                } else {
                    buf = buffer.retainedSlice();
                }
                if (canReturnImmediate) {
                    ret = ExecutionFlow.just(buf);
                } else {
                    targetFlow.complete(buf);
                }
            } else {
                if (fullSubscribers == null) {
                    fullSubscribers = new ArrayList<>(1);
                }
                fullSubscribers.add(targetFlow);
            }
            if (tracker != null) {
                if (last) {
                    tracker.close(this);
                } else {
                    tracker.record();
                }
            }
            working = false;

            return ret;
        }

        @Override
        public void add(ByteBuf buf) {
            assert !working;

            buf.touch();

            // calculate the new total length
            long newLength = lengthSoFar + buf.readableBytes();
            long expectedLength = this.expectedLength;
            if (expectedLength != -1 && newLength > expectedLength) {
                throw new IllegalStateException("Received more bytes than specified by Content-Length");
            }
            lengthSoFar = newLength;

            // drop messages if we're done with all subscribers
            if (complete || error != null) {
                buf.release();
                return;
            }
            adding = true;
            if (newLength > limits.maxBodySize()) {
                // for maxBodySize, all subscribers get the error
                buf.release();
                error(new ContentLengthExceededException(limits.maxBodySize(), newLength));
                rootUpstream.allowDiscard();
                adding = false;
                return;
            }

            working = true;
            if (subscribers != null) {
                for (BufferConsumer subscriber : subscribers) {
                    subscriber.add(buf.retainedSlice());
                }
            }
            if (reserved > 0 || fullSubscribers != null) {
                if (newLength > limits.maxBufferSize()) {
                    // new subscribers will recognize that the limit has been exceeded. Streaming
                    // subscribers can proceed normally. Need to notify buffering subscribers
                    buf.release();
                    if (buffer != null) {
                        buffer.release();
                        buffer = null;
                    }
                    if (fullSubscribers != null) {
                        Exception e = new BufferLengthExceededException(limits.maxBufferSize(), lengthSoFar);
                        for (DelayedExecutionFlow<ByteBuf> fullSubscriber : fullSubscribers) {
                            fullSubscriber.completeExceptionally(e);
                        }
                    }
                } else {
                    if (buffer == null) {
                        buffer = buf.alloc().compositeBuffer();
                    }
                    buffer.addComponent(true, buf);
                }
            } else {
                buf.release();
            }
            adding = false;
            working = false;
        }

        @Override
        public void complete() {
            if (expectedLength > lengthSoFar) {
                throw new IllegalStateException("Received fewer bytes than specified by Content-Length");
            }
            complete = true;
            expectedLength = lengthSoFar;
            if (subscribers != null) {
                for (BufferConsumer subscriber : subscribers) {
                    subscriber.complete();
                }
            }
            if (fullSubscribers != null) {
                boolean release;
                ByteBuf buf;
                if (buffer == null) {
                    buf = Unpooled.EMPTY_BUFFER;
                    release = false;
                } else {
                    buf = buffer;
                    if (reserved > 0) {
                        release = false;
                    } else {
                        this.buffer = null;
                        release = true;
                    }
                }
                for (DelayedExecutionFlow<ByteBuf> fullSubscriber : fullSubscribers) {
                    fullSubscriber.complete(buf.retainedSlice());
                }
                if (release) {
                    buf.release();
                }
            }
        }

        @Override
        public void error(Throwable e) {
            error = e;
            if (buffer != null) {
                buffer.release();
                buffer = null;
            }
            if (subscribers != null) {
                for (BufferConsumer subscriber : subscribers) {
                    subscriber.error(e);
                }
            }
            if (fullSubscribers != null) {
                for (DelayedExecutionFlow<ByteBuf> fullSubscriber : fullSubscribers) {
                    fullSubscriber.completeExceptionally(e);
                }
            }
        }
    }
}
