package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.body.CloseableImmediateInboundByteBody;
import io.micronaut.http.body.CloseableInboundByteBody;
import io.micronaut.http.netty.PublisherAsBlocking;
import io.micronaut.http.netty.PublisherAsStream;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.util.ReferenceCounted;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalLong;

public final class StreamingInboundByteBody extends NettyInboundByteBody implements BufferConsumer, CloseableInboundByteBody {
    private Upstream upstream;
    private List<@Nullable ByteBuf> buffered;
    private BufferConsumer primary;
    private List<StreamingInboundByteBody> split;

    public StreamingInboundByteBody(Upstream upstream) {
        this.upstream = upstream;
    }

    public Upstream primary(BufferConsumer primary) {
        synchronized (this) {
            if (this.primary != null) {
                failClaim();
            }

            this.primary = primary;
            if (this.buffered != null) {
                // todo: move out of sync somehow
                for (ByteBuf b : buffered) {
                    if (b == null) {
                        primary.complete();
                    } else {
                        primary.add(b);
                    }
                }
                this.buffered = null;
            }
        }
        return this.upstream;
    }

    @Override
    public @NonNull CloseableInboundByteBody split(@NonNull SplitBackpressureMode backpressureMode) {
        synchronized (this) {
            if (this.primary != null) {
                failClaim();
            }

            UpstreamBalancer.UpstreamPair pair = switch (backpressureMode) {
                case SLOWEST -> UpstreamBalancer.slowest(upstream);
                case FASTEST -> UpstreamBalancer.fastest(upstream);
                case ORIGINAL -> UpstreamBalancer.first(upstream);
                case NEW -> UpstreamBalancer.first(upstream).flip();
            };

            upstream = pair.left();
            StreamingInboundByteBody node = new StreamingInboundByteBody(pair.right());
            if (this.buffered != null) {
                node.buffered = new ArrayList<>(this.buffered.size());
                for (ByteBuf buf : this.buffered) {
                    node.buffered.add(buf == null ? null : buf.retainedSlice());
                }
            }
            // cannot use COWArrayList here
            List<StreamingInboundByteBody> oldSplit = split;
            if (oldSplit == null) {
                oldSplit = Collections.emptyList();
            }
            List<StreamingInboundByteBody> newSplit = new ArrayList<>(oldSplit.size() + 1);
            newSplit.addAll(oldSplit);
            newSplit.add(node);
            split = newSplit;
            return node;
        }
    }

    @Override
    public void add(@Nullable ByteBuf buf) {
        BufferConsumer primary;
        List<StreamingInboundByteBody> split;
        synchronized (this) {
            primary = this.primary;
            split = this.split;
            if (primary == null) {
                if (this.buffered == null) {
                    this.buffered = new ArrayList<>();
                }
                // todo: limit buffer
                this.buffered.add(buf);
            }
        }
        if (split != null) {
            for (StreamingInboundByteBody s : split) {
                if (buf == null) {
                    s.complete();
                } else {
                    s.add(buf.retainedSlice());
                }
            }
        }
        if (primary != null) {
            if (buf == null) {
                primary.complete();
            } else {
                primary.add(buf);
            }
        }
    }

    @Override
    public void complete() {
        add(null);
    }

    @Override
    protected Flux<ByteBuf> toByteBufPublisher() {
        Sinks.Many<ByteBuf> sink = Sinks.many().unicast().onBackpressureBuffer();
        Upstream upstream = primary(new BufferConsumer() {
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
        });
        return sink.asFlux()
            .doOnNext(bb -> upstream.onBytesConsumed(bb.readableBytes()))
            .doOnDiscard(ByteBuf.class, ReferenceCounted::release);
    }

    @Override
    public @NonNull OptionalLong expectedLength() {
        return OptionalLong.empty(); // TODO
    }

    @Override
    public @NonNull InputStream toInputStream() {
        PublisherAsBlocking<ByteBuf> blocking = new PublisherAsBlocking<>();
        toByteBufPublisher().subscribe(blocking);
        return new PublisherAsStream(blocking);
    }

    @Override
    public @NonNull ExecutionFlow<? extends CloseableImmediateInboundByteBody> buffer() {
        DelayedExecutionFlow<CloseableImmediateInboundByteBody> flow = DelayedExecutionFlow.create();
        primary(new BufferConsumer() {
            private CompositeByteBuf combined;

            @Override
            public void add(ByteBuf buf) {
                if (combined == null) {
                    combined = buf.alloc().compositeBuffer();
                }
                combined.addComponent(true, buf);
            }

            @Override
            public void complete() {
                flow.complete(new ImmediateNettyInboundByteBody(combined));
            }
        }).onBytesConsumed(Long.MAX_VALUE);
        return flow;
    }

    @Override
    public void close() {
        synchronized (this) {
            if (this.primary != null) {
                return;
            }
            primary(new BufferConsumer() {
                @Override
                public void add(ByteBuf buf) {
                    buf.release();
                }

                @Override
                public void complete() {
                }
            }).allowDiscard();
        }
    }
}
