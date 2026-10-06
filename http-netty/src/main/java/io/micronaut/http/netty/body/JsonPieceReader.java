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
import io.netty.buffer.ByteBuf;
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

    private final JsonChunkedProcessor processor;
    private final Function<ByteBuffer<?>, T> valueReader;
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
    JsonPieceReader(JsonChunkedProcessor processor, Function<ByteBuffer<?>, T> valueReader) {
        this.processor = processor;
        this.valueReader = valueReader;
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
        return value == null ? null : JsonChunkedProcessor.readReleasing(value, valueReader);
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
