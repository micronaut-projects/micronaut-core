package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.body.CloseableImmediateInboundByteBody;
import io.micronaut.http.body.CloseableInboundByteBody;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.micronaut.http.netty.EventLoopFlow;
import io.micronaut.http.netty.PublisherAsBlocking;
import io.micronaut.http.netty.PublisherAsStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
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
import java.util.function.Supplier;

@Internal
public final class StreamingInboundByteBody extends NettyInboundByteBody implements CloseableInboundByteBody {
    private final SharedBuffer sharedBuffer;
    private BufferConsumer.Upstream upstream;

    public StreamingInboundByteBody(SharedBuffer sharedBuffer) {
        this(sharedBuffer, sharedBuffer.rootUpstream);
    }

    private StreamingInboundByteBody(SharedBuffer sharedBuffer, BufferConsumer.Upstream upstream) {
        this.sharedBuffer = sharedBuffer;
        this.upstream = upstream;
    }

    private BufferConsumer.Upstream primary(BufferConsumer primary) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        this.upstream = null;
        sharedBuffer.subscribe(primary, upstream);
        return upstream;
    }

    @Override
    public @NonNull CloseableInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            failClaim();
        }
        UpstreamBalancer.UpstreamPair pair = switch (backpressureMode) {
            case SLOWEST -> UpstreamBalancer.slowest(upstream);
            case FASTEST -> UpstreamBalancer.fastest(upstream);
            case ORIGINAL -> UpstreamBalancer.first(upstream);
            case NEW -> UpstreamBalancer.first(upstream).flip();
        };
        this.upstream = pair.left();
        this.sharedBuffer.reserve();
        return new StreamingInboundByteBody(sharedBuffer, upstream);
    }

    @Override
    protected Flux<ByteBuf> toByteBufPublisher() {
        Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer();
        BufferConsumer.Upstream upstream = primary(new BufferConsumer() {
            @Override
            public void add(ByteBuf buf) {
                if (sink.tryEmitNext(buf) != Sinks.EmitResult.OK) {
                    buf.release();
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
            .doOnNext(bb -> upstream.onBytesConsumed(bb.readableBytes()))
            .doOnDiscard(ByteBuf.class, ReferenceCounted::release);
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
    public @NonNull ExecutionFlow<? extends CloseableImmediateInboundByteBody> buffer() {
        // todo: optimize with sharedBuffer
        DelayedExecutionFlow<CloseableImmediateInboundByteBody> flow = DelayedExecutionFlow.create();
        BufferConsumer.Upstream u = primary(new BufferConsumer() {
            private CompositeByteBuf combined;
            private boolean done;

            @Override
            public void add(ByteBuf buf) {
                if (combined == null) {
                    combined = buf.alloc().compositeBuffer();
                }
                combined.addComponent(true, buf);
            }

            @Override
            public void complete() {
                if (!done) {
                    done = true;
                    flow.complete(combined == null ? ImmediateNettyInboundByteBody.empty() : new ImmediateNettyInboundByteBody(combined));
                }
            }

            @Override
            public void error(Throwable e) {
                if (!done) {
                    done = true;
                    if (combined != null) {
                        combined.release();
                        combined = null;
                    }
                    flow.completeExceptionally(e);
                }
            }
        });
        u.start();
        u.onBytesConsumed(Long.MAX_VALUE);
        return flow;
    }

    @Override
    public void close() {
        BufferConsumer.Upstream upstream = this.upstream;
        if (upstream == null) {
            return;
        }
        this.upstream = null;
        upstream.allowDiscard();
        sharedBuffer.subscribe(null, upstream);
    }

    public static final class SharedBuffer implements BufferConsumer {
        private static final Supplier<ResourceLeakDetector<SharedBuffer>> LEAK_DETECTOR = SupplierUtil.memoized(() ->
            ResourceLeakDetectorFactory.instance().newResourceLeakDetector(SharedBuffer.class));

        @Nullable
        private final ResourceLeakTracker<SharedBuffer> tracker = LEAK_DETECTOR.get().track(this);

        private final EventLoopFlow eventLoopFlow;
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
         * This flag is only used in tests, to verify that the BufferConsumer methods arent called
         * in a reentrant fashion.
         */
        private boolean working = false;
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
            this.eventLoopFlow = new EventLoopFlow(loop);
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

        void reserve() {
            if (eventLoopFlow.executeNow(this::reserve0)) {
                reserve0();
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
         */
        void subscribe(@Nullable BufferConsumer subscriber, Upstream specificUpstream) {
            if (eventLoopFlow.executeNow(() -> subscribe0(subscriber, specificUpstream))) {
                subscribe0(subscriber, specificUpstream);
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
                    subscribers = new ArrayList<>();
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

        @Override
        public void add(ByteBuf buf) {
            assert !working;

            buf.touch();

            // drop messages if we're done with all subscribers
            if (complete || error != null) {
                buf.release();
                return;
            }
            // calculate the new total length
            long newLength = (lengthSoFar += buf.readableBytes());
            if (newLength > limits.maxBodySize()) {
                // for maxBodySize, all subscribers get the error
                buf.release();
                error(new ContentLengthExceededException(limits.maxBodySize(), lengthSoFar));
                rootUpstream.allowDiscard();
                return;
            }

            working = true;
            if (subscribers != null) {
                for (BufferConsumer subscriber : subscribers) {
                    subscriber.add(buf.retainedSlice());
                }
            }
            if (reserved > 0) {
                if (newLength > limits.maxBufferSize()) {
                    // new subscribers will recognize that the limit has been exceeded. Streaming
                    // subscribers can proceed normally
                    buf.release();
                    if (buffer != null) {
                        buffer.release();
                        buffer = null;
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
            working = false;
        }

        @Override
        public void complete() {
            complete = true;
            expectedLength = lengthSoFar;
            if (subscribers != null) {
                for (BufferConsumer subscriber : subscribers) {
                    subscriber.complete();
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
        }
    }
}
