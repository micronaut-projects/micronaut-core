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
package io.micronaut.http.client.netty;

import io.micronaut.buffer.netty.NettyReadBufferFactory;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.http.body.PieceReader;
import io.micronaut.http.client.sse.EventStreamDecoder;
import io.micronaut.http.sse.Event;
import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.function.Function;

/**
 * The reader of the events of an event stream of the Netty buffers of the response: a heap buffer
 * is decoded in its array, and a direct buffer is copied, with one copy of the piece, into an
 * array that the reader reuses, so that no piece allocates. The lines are split and interpreted by
 * the {@link EventStreamDecoder}, eight bytes at a time, and the data of an event is decoded when
 * the event is polled.
 *
 * @param <B> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyEventStreamReader<B> implements PieceReader<Event<B>> {

    private final EventStreamDecoder decoder;
    private final Function<byte[], B> dataReader;
    private final ArrayDeque<Event<byte[]>> events = new ArrayDeque<>(1);
    /**
     * The bytes of a direct buffer.
     */
    private byte[] scratch = new byte[0];
    private boolean closed;

    /**
     * @param decoder    The decoder, which splits and interprets the lines
     * @param dataReader Decodes the data of an event
     */
    NettyEventStreamReader(EventStreamDecoder decoder, Function<byte[], B> dataReader) {
        this.decoder = decoder;
        this.dataReader = dataReader;
    }

    @Override
    public void read(ReadBuffer piece) {
        if (closed) {
            piece.close();
            return;
        }
        ByteBuf buf = NettyReadBufferFactory.toByteBuf(piece);
        try {
            int length = buf.readableBytes();
            if (buf.hasArray()) {
                events.addAll(decoder.decode(buf.array(), buf.arrayOffset() + buf.readerIndex(), length));
            } else {
                if (scratch.length < length) {
                    scratch = new byte[Math.max(length, scratch.length * 2)];
                }
                buf.getBytes(buf.readerIndex(), scratch, 0, length);
                events.addAll(decoder.decode(scratch, 0, length));
            }
        } finally {
            buf.release();
        }
    }

    @Override
    public void complete() {
        // an event not terminated by a blank line is discarded
    }

    @Override
    public @Nullable Event<B> poll() {
        Event<byte[]> event = events.poll();
        return event == null ? null : Event.of(event, dataReader.apply(event.getData()));
    }

    @Override
    public void close() {
        closed = true;
        events.clear();
    }
}
