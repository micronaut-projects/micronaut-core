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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.simple.SimpleHttpHeaders;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import jakarta.inject.Singleton;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Encapsulates functionality to encode WebSocket messages using message body handlers.
 *
 * @author sdelamo
 * @since 1.0
 */
@Singleton
@Internal
public final class WebSocketMessageEncoder {

    private final MessageBodyHandlerRegistry handlerRegistry;
    private final ConversionService conversionService;

    public WebSocketMessageEncoder(MessageBodyHandlerRegistry handlerRegistry,
                                   ConversionService conversionService) {
        this.handlerRegistry = Objects.requireNonNull(handlerRegistry, "handlerRegistry");
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService");
    }

    /**
     * Encode the given message with the given media type.
     *
     * @param message   The message
     * @param mediaType The media type
     * @return The encoded frame
     */
    WebSocketFrame encodeMessage(Object message, MediaType mediaType) {
        if (message instanceof byte[] bytes) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        }
        if (message instanceof ByteBuf buf) {
            return new BinaryWebSocketFrame(buf.slice());
        }
        if (message instanceof ByteBuffer buffer) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(buffer));
        }
        if (ClassUtils.isJavaLangType(message.getClass()) || message instanceof CharSequence) {
            return new TextWebSocketFrame(message.toString());
        }

        MediaType effectiveMediaType = mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE;
        Argument<Object> argument = Argument.ofInstance(message);
        MessageBodyWriter<Object> writer = handlerRegistry.findWriter(argument, effectiveMediaType).orElse(null);
        if (writer != null) {
            io.micronaut.core.io.buffer.ByteBuffer<?> encoded;
            try {
                encoded = writer.writeTo(
                    argument,
                    effectiveMediaType,
                    message,
                    new SimpleHttpHeaders(),
                    NettyByteBufferFactory.DEFAULT
                );
            } catch (CodecException e) {
                throw new WebSocketSessionException("Unable to encode WebSocket message: " + e.getMessage(), e);
            }
            WebSocketFrame frame = createFrameFromBuffer(effectiveMediaType, encoded);
            if (frame != null) {
                return frame;
            }
        }

        return conversionService.convert(message, String.class)
            .<WebSocketFrame>map(TextWebSocketFrame::new)
            .orElseThrow(() -> new WebSocketSessionException("Unable to encode WebSocket message: " + message));
    }

    private static WebSocketFrame createFrameFromBuffer(MediaType mediaType,
                                                        io.micronaut.core.io.buffer.ByteBuffer<?> encoded) {
        Object nativeBuffer = encoded.asNativeBuffer();
        if (nativeBuffer instanceof ByteBuf byteBuf) {
            if (isTextMediaType(mediaType)) {
                return new TextWebSocketFrame(byteBuf);
            }
            return new BinaryWebSocketFrame(byteBuf);
        }
        byte[] bytes = encoded.toByteArray();
        if (isTextMediaType(mediaType)) {
            return new TextWebSocketFrame(Unpooled.wrappedBuffer(bytes));
        }
        return new BinaryWebSocketFrame(Unpooled.wrappedBuffer(bytes));
    }

    private static boolean isTextMediaType(MediaType mediaType) {
        if (MediaType.TEXT_PLAIN_TYPE.getType().equals(mediaType.getType())) {
            return true;
        }
        if (mediaType.equals(MediaType.APPLICATION_JSON_TYPE) || mediaType.equals(MediaType.APPLICATION_JSON_STREAM_TYPE)) {
            return true;
        }
        return mediaType.matchesAllOrWildcardOrExtension(MediaType.EXTENSION_JSON);
    }
}
