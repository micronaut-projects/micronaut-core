/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.netty.websocket;

import io.micronaut.buffer.netty.NettyByteBufferFactory;
import io.micronaut.context.annotation.Requires;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Encapsulate functionality to encode WebSocket messages.
 *
 * @author sdelamo
 * @since 1.0
 */
@Requires(classes = WebSocketSessionException.class)
@Singleton
public class WebSocketMessageEncoder {

    private final MediaTypeCodecRegistry codecRegistry;
    @Nullable
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;

    /**
     * @param codecRegistry The codec registry
     * @deprecated Not used anymore
     */
    @Deprecated(forRemoval = true, since = "4.7")
    public WebSocketMessageEncoder(MediaTypeCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
        this.messageBodyHandlerRegistry = null;
    }

    /**
     * @param codecRegistry The codec registry
     * @param messageBodyHandlerRegistry The message body handler registry
     */
    @Inject
    public WebSocketMessageEncoder(MediaTypeCodecRegistry codecRegistry,
                                   MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        this.codecRegistry = codecRegistry;
        this.messageBodyHandlerRegistry = messageBodyHandlerRegistry;
    }

    /**
     * Encode the given message with the given media type. Text is copied into an unpooled heap
     * buffer, and encoded objects use the default allocator.
     * @param message The message
     * @param mediaType The media type
     * @return The encoded frame
     * @deprecated Use {@link #encodeMessage(Object, MediaType, ByteBufAllocator)} with the
     * allocator of the channel the frame is written to
     */
    @Deprecated(since = "5.3.0")
    public WebSocketFrame encodeMessage(Object message, MediaType mediaType) {
        if (!(message instanceof byte[]) && (ClassUtils.isJavaLangType(message.getClass()) || message instanceof CharSequence)) {
            return new TextWebSocketFrame(message.toString());
        }
        return encodeMessage(message, mediaType, ByteBufAllocator.DEFAULT);
    }

    /**
     * Encode the given message with the given media type. The bytes of text and of encoded
     * objects are written into buffers of the given allocator.
     * @param message The message
     * @param mediaType The media type
     * @param allocator The allocator of the buffer of the frame, that of the channel the frame
     *                  is written to
     * @return The encoded frame
     * @since 5.3.0
     */
    public WebSocketFrame encodeMessage(Object message, MediaType mediaType, ByteBufAllocator allocator) {
        if (message instanceof byte[] bytes) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        } else if (ClassUtils.isJavaLangType(message.getClass()) || message instanceof CharSequence) {
            String s = message.toString();
            // sized exactly: the frame is written as it is
            int length = ByteBufUtil.utf8Bytes(s);
            ByteBuf buf = allocator.buffer(length);
            try {
                ByteBufUtil.reserveAndWriteUtf8(buf, s, length);
            } catch (Throwable e) {
                buf.release();
                throw e;
            }
            return new TextWebSocketFrame(buf);
        } else if (message instanceof ByteBuf buf) {
            return new BinaryWebSocketFrame(buf.slice());
        } else if (message instanceof ByteBuffer buffer) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(buffer));
        } else {
            NettyByteBufferFactory bufferFactory = new NettyByteBufferFactory(allocator);
            MediaType theMediaType = mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE;
            Optional<MediaTypeCodec> codec = codecRegistry.findCodec(theMediaType);
            if (codec.isPresent()) {
                io.micronaut.core.io.buffer.ByteBuffer<?> encoded = codec.get().encode(message, bufferFactory);
                return new TextWebSocketFrame((ByteBuf) encoded.asNativeBuffer());
            }
            if (messageBodyHandlerRegistry != null) {
                Argument<Object> argument = Argument.ofInstance(message);
                MessageBodyWriter<Object> messageBodyWriter = messageBodyHandlerRegistry.findWriter(argument, theMediaType).orElse(null);
                if (messageBodyWriter != null) {
                    io.micronaut.core.io.buffer.ByteBuffer<?> encoded = messageBodyWriter.writeTo(
                        argument,
                        theMediaType,
                        message,
                        new SimpleHttpHeaders(),
                        bufferFactory
                    );
                    return new TextWebSocketFrame((ByteBuf) encoded.asNativeBuffer());
                }
            }
        }
        throw new WebSocketSessionException("Unable to encode WebSocket message: " + message);
    }
}
