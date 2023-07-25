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
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
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
    private ByteBuf singleBuffer;
    private CompositeByteBuf compositeBuffer;

    public Flux<ByteBuffer<?>> process(Flux<ByteBuf> input) {
        return Flux.concat(input
                .concatMap(b -> Flux.<ByteBuffer<?>>create(s -> {
                    try {
                        countLoop(s, b);
                        s.complete();
                    } catch (IOException e) {
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
            .doOnTerminate(this::releaseBuffers);
    }

    private void releaseBuffers() {
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
                long start = Math.max(initialPosition, bufferRegion.start());
                buffer(content.retainedSlice(
                    Math.toIntExact(start - bias),
                    Math.toIntExact(bufferRegion.end() - start)
                ));
                flush(out);
            }
        }
        if (counter.isBuffering()) {
            int currentBufferStart = Math.toIntExact(Math.max(initialPosition, counter.bufferStart()) - bias);
            content.readerIndex(currentBufferStart);
            buffer(content.retain());
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

    private void flush(FluxSink<? super ByteBuffer<?>> out) {
        ByteBuf completedNode = compositeBuffer == null ? singleBuffer : compositeBuffer;
        ByteBuffer<ByteBuf> wrapped = NettyByteBufferFactory.DEFAULT.wrap(completedNode);
        out.next(wrapped);
        compositeBuffer = null;
        singleBuffer = null;
    }

    private void complete(FluxSink<? super ByteBuffer<?>> out) {
        if (this.singleBuffer != null || this.compositeBuffer != null) {
            flush(out);
        }
    }
}
