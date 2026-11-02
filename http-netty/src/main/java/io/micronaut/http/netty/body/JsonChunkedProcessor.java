/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.micronaut.http.exceptions.BufferLengthExceededException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Adapted from JsonContentProcessor. This class takes input data and splits it up according to the
 * {@link #counter} configuration.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
final class JsonChunkedProcessor {
    private static final int SPLITTING = 1;
    private static final int RELEASED = 1 << 1;

    final JsonCounter counter = new JsonCounter();
    private ByteBuf singleBuffer;
    private CompositeByteBuf compositeBuffer;
    /**
     * {@link #SPLITTING} and {@link #RELEASED} flags. concatMap splits one chunk at a time, but
     * a cancel may arrive from another thread while a chunk is being split. Instead of locking,
     * the release of the buffering state is left to the splitting thread in that case, when it
     * is done with the chunk.
     */
    private final AtomicInteger state = new AtomicInteger();
    /**
     * The nodes of the chunk that is being emitted. concatMap emits one chunk at a time, so the
     * nodes of earlier chunks have all been claimed.
     */
    private List<Node> emitting = List.of();
    /**
     * The maximum size of a single node.
     */
    private long maxElementSize = Long.MAX_VALUE;

    /**
     * Split the input into one buffer per JSON node. The input buffers are released by this
     * method. Any node the downstream does not receive, because it cancelled or because the
     * stream failed, is released as well.
     *
     * @param input The input buffers
     * @return The split buffers
     */
    public Flux<ByteBuffer<?>> process(Flux<ByteBuf> input) {
        return Flux.deferContextual(ctx -> {
            maxElementSize = JsonStreamElementLimit.get(ctx);
            return processWithLimit(input);
        });
    }

    private Flux<ByteBuffer<?>> processWithLimit(Flux<ByteBuf> input) {
        return Flux.concat(
                input.map(Node::new).concatMap(node -> {
                    ByteBuf b = node.claim();
                    if (b == null) {
                        return Flux.empty();
                    }
                    // split one node per request, so that the nodes of a chunk are not all
                    // retained ahead of demand
                    return Flux.generate(() -> new Chunk(b), this::splitNext, Chunk::release);
                }, 1),
                Flux.defer(() -> {
                    if (!enter()) {
                        return Flux.empty();
                    }
                    try {
                        return Flux.fromIterable(track(complete()));
                    } catch (Throwable e) {
                        return Flux.error(e);
                    } finally {
                        exit();
                    }
                })
            )
            // releases an input chunk that arrives after cancel. The nodes of the chunk that is
            // being emitted are released by releaseBuffers, fromIterable does not reliably discard
            // them when the cancel comes from another thread
            .doOnDiscard(Node.class, Node::release)
            .doFinally(s -> releaseBuffers())
            .handle((node, sink) -> {
                // a cancel from another thread may discard a node while it is being emitted,
                // whichever side claims it first owns it
                ByteBuf b = node.claim();
                if (b != null) {
                    sink.next(NettyByteBufferFactory.DEFAULT.wrap(b));
                }
            });
    }

    /**
     * Split a single buffer that holds the complete input. Unlike {@link #process}, this does
     * not release the input.
     *
     * @param input The complete input
     * @return The split buffers, which the caller must release
     * @throws IOException If the input is not valid JSON
     */
    List<ByteBuf> processFully(ByteBuf input) throws IOException {
        List<ByteBuf> out = new ArrayList<>();
        try {
            out.addAll(split(input.retainedDuplicate()));
            out.addAll(complete());
            return out;
        } catch (Throwable t) {
            releaseAll(out);
            throw t;
        } finally {
            releaseBuffers();
        }
    }

    /**
     * Read a node with the given reader. A reader releases the buffer on success only, but a
     * node of a chunked read has no caller that could release it on failure, so this does.
     *
     * @param node   The node to read
     * @param reader The reader
     * @param <T>    The read type
     * @return The read value
     */
    static <T> T readOrRelease(ByteBuffer<?> node, Function<ByteBuffer<?>, T> reader) {
        try {
            return reader.apply(node);
        } catch (Throwable t) {
            if (node instanceof ReferenceCounted rc) {
                rc.release();
            }
            throw t;
        }
    }

    /**
     * Unwrap a netty buffer. The input is released if it is not a netty buffer.
     *
     * @param input The input buffer
     * @return The netty buffer
     */
    static ByteBuf toByteBuf(ByteBuffer<?> input) {
        if (!(input.asNativeBuffer() instanceof ByteBuf buf)) {
            if (input instanceof ReferenceCounted rc) {
                rc.release();
            }
            throw new IllegalArgumentException("Only netty buffers are supported");
        }
        return buf;
    }

    /**
     * The generator of {@link Flux#generate(java.util.concurrent.Callable, java.util.function.BiFunction, java.util.function.Consumer)}
     * for the nodes of one input chunk.
     *
     * @param chunk The generator state: the chunk that is being split
     * @param sink  The sink that receives at most one node
     * @return The state for the next round, always the same chunk. {@code Flux.generate} requires
     * the state to be returned, it is how this method fits its {@code BiFunction}
     */
    private Chunk splitNext(Chunk chunk, SynchronousSink<Node> sink) {
        if (!enter()) {
            sink.complete();
            return chunk;
        }
        try {
            ByteBuf next = chunk.next();
            if (next == null) {
                sink.complete();
            } else {
                sink.next(track(next));
            }
        } catch (Throwable e) {
            sink.error(e);
        } finally {
            exit();
        }
        return chunk;
    }

    private List<ByteBuf> split(ByteBuf content) throws IOException {
        List<ByteBuf> out = new ArrayList<>();
        Chunk chunk = new Chunk(content);
        try {
            ByteBuf next;
            while ((next = chunk.next()) != null) {
                out.add(next);
            }
            return out;
        } catch (Throwable t) {
            releaseAll(out);
            throw t;
        } finally {
            chunk.release();
        }
    }

    private List<ByteBuf> complete() {
        ByteBuf last = takeBuffered();
        return last == null ? List.of() : List.of(last);
    }

    /**
     * Mark the start of a split.
     *
     * @return {@code false} if the stream is already released
     */
    private boolean enter() {
        int s;
        do {
            s = state.get();
            if ((s & RELEASED) != 0) {
                return false;
            }
        } while (!state.compareAndSet(s, s | SPLITTING));
        return true;
    }

    /**
     * Mark the end of a split, and release the buffering state if the stream was released in
     * the meantime.
     */
    private void exit() {
        if ((state.getAndUpdate(s -> s & ~SPLITTING) & RELEASED) != 0) {
            releaseState();
        }
    }

    private void releaseBuffers() {
        int old = state.getAndUpdate(s -> s | RELEASED);
        // while a split is running, exit() releases the state
        if ((old & (RELEASED | SPLITTING)) == 0) {
            releaseState();
        }
    }

    private void releaseState() {
        for (Node node : emitting) {
            node.release();
        }
        emitting = List.of();
        ByteBuf buffered = takeBuffered();
        if (buffered != null) {
            buffered.release();
        }
    }

    private Node track(ByteBuf buf) {
        // if the stream is released during the split, exit() releases this node
        Node node = new Node(buf);
        emitting = List.of(node);
        return node;
    }

    private List<Node> track(List<ByteBuf> bufs) {
        emitting = Node.wrapAll(bufs);
        return emitting;
    }

    private static void releaseAll(List<ByteBuf> buffers) {
        for (ByteBuf buffer : buffers) {
            buffer.release();
        }
    }

    private void checkElementSize(long size) throws BufferLengthExceededException {
        if (size > maxElementSize) {
            throw new BufferLengthExceededException(maxElementSize, size);
        }
    }

    private void buffer(ByteBuf buffer) {
        if (this.singleBuffer == null && this.compositeBuffer == null) {
            this.singleBuffer = buffer;
        } else {
            if (this.compositeBuffer == null) {
                // no component limit: a consolidating composite recopies the whole node every few pieces
                this.compositeBuffer = buffer.alloc().compositeBuffer(Integer.MAX_VALUE);
                this.compositeBuffer.addComponent(true, this.singleBuffer);
                this.singleBuffer = null;
            }
            this.compositeBuffer.addComponent(true, buffer);
        }
    }

    @Nullable
    private ByteBuf takeBuffered() {
        ByteBuf buffered = compositeBuffer == null ? singleBuffer : compositeBuffer;
        compositeBuffer = null;
        singleBuffer = null;
        return buffered;
    }

    /**
     * An input chunk that is split one node at a time.
     */
    private final class Chunk {
        private final ByteBuf content;
        private final long initialPosition;
        private final long bias;
        private boolean done;
        private boolean released;

        Chunk(ByteBuf content) {
            this.content = content;
            this.initialPosition = counter.position();
            this.bias = initialPosition - content.readerIndex();
        }

        /**
         * @return The next complete node of this chunk, or {@code null} once the chunk is
         * consumed. The incomplete node at the end of the chunk is buffered.
         */
        @Nullable
        ByteBuf next() throws IOException {
            while (content.isReadable()) {
                counter.feed(content);
                JsonCounter.BufferRegion bufferRegion = counter.pollFlushedRegion();
                if (bufferRegion != null) {
                    checkElementSize(bufferRegion.end() - bufferRegion.start());
                    long start = Math.max(initialPosition, bufferRegion.start());
                    buffer(content.retainedSlice(
                        Math.toIntExact(start - bias),
                        Math.toIntExact(bufferRegion.end() - start)
                    ));
                    return Objects.requireNonNull(takeBuffered());
                }
            }
            if (!done) {
                done = true;
                if (counter.isBuffering()) {
                    checkElementSize(counter.position() - counter.bufferStart());
                    int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
                    buffer(content.retainedSlice(currentBufferStart, content.writerIndex() - currentBufferStart));
                }
            }
            return null;
        }

        void release() {
            if (!released) {
                released = true;
                content.release();
            }
        }
    }

    /**
     * Holder for a buffer in flight, so that exactly one of the discard hook and the consumer
     * releases it, even when they race.
     */
    private static final class Node extends AtomicReference<ByteBuf> {
        Node(ByteBuf buf) {
            super(buf);
        }

        static List<Node> wrapAll(List<ByteBuf> bufs) {
            List<Node> nodes = new ArrayList<>(bufs.size());
            for (ByteBuf buf : bufs) {
                nodes.add(new Node(buf));
            }
            return nodes;
        }

        @Nullable
        ByteBuf claim() {
            return getAndSet(null);
        }

        void release() {
            ByteBuf buf = claim();
            if (buf != null) {
                buf.release();
            }
        }
    }
}
