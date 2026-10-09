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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReferenceCounted;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

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
    @Nullable
    private ByteBuf singleBuffer;
    @Nullable
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
     * Split the input into one buffer per JSON node. The input buffers are released by this
     * method. Any node the downstream does not receive, because it cancelled or because the
     * stream failed, is released as well.
     *
     * @param input The input buffers
     * @return The split buffers
     */
    public Flux<ByteBuffer<?>> process(Flux<ByteBuf> input) {
        return Flux.concat(
                input.map(Node::new).concatMap(node -> {
                    ByteBuf b = node.claim();
                    if (b == null) {
                        return Flux.empty();
                    }
                    if (!enter()) {
                        b.release();
                        return Flux.empty();
                    }
                    try {
                        return Flux.fromIterable(track(split(b)));
                    } catch (Throwable e) {
                        return Flux.error(e);
                    } finally {
                        exit();
                    }
                }),
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

    private List<ByteBuf> split(ByteBuf content) throws IOException {
        List<ByteBuf> out = new ArrayList<>();
        try {
            countLoop(out, content);
            return out;
        } catch (Throwable t) {
            releaseAll(out);
            throw t;
        } finally {
            content.release();
        }
    }

    private List<ByteBuf> complete() throws IOException {
        counter.noMoreInput();
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

    private List<Node> track(List<ByteBuf> bufs) {
        // if the stream is released during the split, exit() releases these nodes
        emitting = Node.wrapAll(bufs);
        return emitting;
    }

    private static void releaseAll(List<ByteBuf> buffers) {
        for (ByteBuf buffer : buffers) {
            buffer.release();
        }
    }

    private void countLoop(List<ByteBuf> out, ByteBuf content) throws IOException {
        long initialPosition = counter.position();
        long bias = initialPosition - content.readerIndex();
        while (content.isReadable()) {
            counter.feed(content);
            JsonCounter.BufferRegion bufferRegion = counter.pollFlushedRegion();
            if (bufferRegion != null) {
                long start = Math.max(initialPosition, bufferRegion.start());
                buffer(content.retainedSlice(
                    Math.toIntExact(start - bias),
                    Math.toIntExact(bufferRegion.end() - start)
                ));
                out.add(Objects.requireNonNull(takeBuffered()));
            }
        }
        if (counter.isBuffering()) {
            int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
            buffer(content.retainedSlice(currentBufferStart, content.writerIndex() - currentBufferStart));
        }
    }

    private void buffer(ByteBuf buffer) {
        if (this.singleBuffer == null && this.compositeBuffer == null) {
            this.singleBuffer = buffer;
        } else {
            if (this.compositeBuffer == null) {
                this.compositeBuffer = buffer.alloc().compositeBuffer();
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
