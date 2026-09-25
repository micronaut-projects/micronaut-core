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
import io.micronaut.http.exceptions.ContentLengthExceededException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.IOException;

/**
 * Adapted from JsonContentProcessor. This class takes input data and splits it up according to the
 * {@link #counter} configuration.
 *
 * @since 4.0.0
 * @author Jonas Konrad
 */
@Internal
final class JsonChunkedProcessor {
    final JsonCounter counter = new JsonCounter();
    /**
     * The maximum number of bytes of a JSON value that is buffered to be emitted.
     */
    private final long maxElementSize;
    // guarded by this: the subscriber may cancel, which releases them, on another thread than
    // the one that processes the input
    @Nullable
    private ByteBuf singleBuffer;
    @Nullable
    private CompositeByteBuf compositeBuffer;
    // the buffers were released: what the processing still buffers is released at once
    private boolean released;

    JsonChunkedProcessor() {
        this(Long.MAX_VALUE);
    }

    /**
     * @param maxElementSize The maximum number of bytes of a JSON value: a larger one fails the
     *                       processing with a {@link ContentLengthExceededException}, once that
     *                       many bytes of it were buffered
     */
    JsonChunkedProcessor(long maxElementSize) {
        this.maxElementSize = maxElementSize;
    }

    public Flux<ByteBuffer<?>> process(Flux<ByteBuf> input) {
        return Flux.concat(input
                .concatMap(b -> Flux.<ByteBuffer<?>>create(s -> {
                    try {
                        countLoop(s, b);
                        s.complete();
                    } catch (IOException | ContentLengthExceededException e) {
                        s.error(e);
                    } finally {
                        b.release();
                    }
                })), Flux.create(s -> {
                try {
                    complete(s);
                    s.complete();
                } catch (Throwable e) {
                    s.error(e);
                }
            }))
            // also when the subscriber cancels, e.g. a reader that stops before the last element:
            // the partial element and the elements and input not delivered yet are released
            .doFinally(signal -> releaseBuffers())
            .doOnDiscard(ByteBuffer.class, JsonChunkedProcessor::release)
            .doOnDiscard(ByteBuf.class, ByteBuf::release);
    }

    private static void release(ByteBuffer<?> buffer) {
        if (buffer.asNativeBuffer() instanceof ByteBuf buf) {
            buf.release();
        }
    }

    private synchronized void releaseBuffers() {
        released = true;
        if (this.singleBuffer != null) {
            this.singleBuffer.release();
            this.singleBuffer = null;
        }
        if (this.compositeBuffer != null) {
            this.compositeBuffer.release();
            this.compositeBuffer = null;
        }
    }

    private void countLoop(FluxSink<? super ByteBuffer<?>> out, ByteBuf content) throws IOException {
        long initialPosition = counter.position();
        long bias = initialPosition - content.readerIndex();
        while (content.isReadable()) {
            counter.feed(content);
            JsonCounter.BufferRegion bufferRegion = counter.pollFlushedRegion();
            if (bufferRegion != null) {
                checkSize(bufferRegion.end() - bufferRegion.start());
                long start = Math.max(initialPosition, bufferRegion.start());
                buffer(content.retainedSlice(
                    Math.toIntExact(start - bias),
                    Math.toIntExact(bufferRegion.end() - start)
                ));
                flush(out);
            }
        }
        if (counter.isBuffering()) {
            // what is buffered of a value that is not complete yet: a value that is too large
            // fails before it is buffered whole
            checkSize(counter.position() - counter.bufferStart());
            int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
            content.readerIndex(currentBufferStart);
            buffer(content.retain());
        }
    }

    private void checkSize(long size) {
        if (size > maxElementSize) {
            throw new ContentLengthExceededException("The size of a JSON value [" + size + "] exceeds the maximum allowed content length [" + maxElementSize + "]");
        }
    }

    private synchronized void buffer(ByteBuf buffer) {
        if (released) {
            buffer.release();
            return;
        }
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

    private void flush(FluxSink<? super ByteBuffer<?>> out) {
        ByteBuf completedNode = take();
        if (completedNode != null) {
            // emitted without the lock: the subscriber may cancel meanwhile
            out.next(NettyByteBufferFactory.DEFAULT.wrap(completedNode));
        }
    }

    /**
     * @return The buffered element, taken from this, or {@code null} if nothing is buffered, or
     * the buffers were released
     */
    private synchronized @Nullable ByteBuf take() {
        ByteBuf completedNode = compositeBuffer == null ? singleBuffer : compositeBuffer;
        compositeBuffer = null;
        singleBuffer = null;
        return completedNode;
    }

    private void complete(FluxSink<? super ByteBuffer<?>> out) throws IOException {
        counter.noMoreInput();
        flush(out);
    }
}
