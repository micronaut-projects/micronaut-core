/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.codec.CodecException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.ReferenceCounted;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.function.Function;

/**
 * The {@link PieceReader} of JSON, without Reactive Streams: the {@link JsonChunkedProcessor}
 * splits the pieces into the bytes of the values, and a value is decoded when it is polled.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class JsonPieceReader<T> implements PieceReader<T> {

    private static final NettyReadBufferFactory READ_BUFFERS = NettyReadBufferFactory.of(ByteBufAllocator.DEFAULT);

    private final JsonChunkedProcessor processor;
    private final Function<ByteBuffer<?>, @Nullable T> valueReader;
    /**
     * The bytes of the values the pieces read so far complete, not polled yet.
     */
    private final ArrayDeque<ByteBuffer<?>> values = new ArrayDeque<>(1);
    private boolean closed;

    /**
     * @param processor   The processor that splits the pieces into values, configured for the
     *                    JSON to read: a stream of values, or the elements of an array
     * @param valueReader Decodes the bytes of a value, and releases them
     */
    JsonPieceReader(JsonChunkedProcessor processor, Function<ByteBuffer<?>, @Nullable T> valueReader) {
        this.processor = processor;
        this.valueReader = valueReader;
    }

    /**
     * The read buffer of a buffer of the input of a chunked reader, without copying a Netty
     * buffer.
     *
     * @param buffer The buffer, which the read buffer takes over
     * @return The read buffer
     */
    static ReadBuffer adapt(ByteBuffer<?> buffer) {
        return READ_BUFFERS.adapt(buffer);
    }

    /**
     * Releases a Netty object that a Reactor input of a chunked reader discards, e.g. a buffer
     * it held before it was mapped to a piece.
     *
     * @param object The discarded object
     */
    static void discardForeign(Object object) {
        if (object instanceof ReferenceCounted counted && counted.refCnt() > 0) {
            ReferenceCountUtil.safeRelease(counted);
        }
    }

    @Override
    public void read(ReadBuffer piece) throws IOException {
        if (closed) {
            piece.close();
            return;
        }
        ByteBuf content = NettyReadBufferFactory.toByteBuf(piece);
        try {
            processor.feed(content, values::add);
        } finally {
            content.release();
        }
    }

    @Override
    public void complete() throws IOException {
        if (!closed) {
            processor.finish(values::add);
        }
    }

    @Override
    public @Nullable T poll() {
        ByteBuffer<?> value = values.poll();
        if (value == null) {
            return null;
        }
        @Nullable T element = JsonChunkedProcessor.<@Nullable T>readReleasing(value, valueReader);
        if (element == null) {
            // null means that no element is available: a JSON null is not an element, as the
            // reactive readers refuse it
            throw new CodecException("A JSON null is not an element of a JSON array or stream");
        }
        return element;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        processor.discard();
        ByteBuffer<?> value;
        while ((value = values.poll()) != null) {
            JsonChunkedProcessor.release(value);
        }
    }
}
