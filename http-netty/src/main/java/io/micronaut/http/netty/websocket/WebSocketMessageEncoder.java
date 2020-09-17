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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

import javax.inject.Singleton;
import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Encapsulate functionality to encode WebSocket messages.
 *
 * @author sdelamo
 * @since 1.0
 */
@Singleton
public class WebSocketMessageEncoder {

    private final MediaTypeCodecRegistry codecRegistry;

    /**
     * @param codecRegistry The codec registry
     */
    public WebSocketMessageEncoder(MediaTypeCodecRegistry codecRegistry) {
        this.codecRegistry = codecRegistry;
    }

    /**
     * Encode the given message with the given media type.
     * @param message The message
     * @param mediaType The media type
     * @return The encoded frame
     */
    public WebSocketFrame encodeMessage(Object message, MediaType mediaType) {
        if (message instanceof byte[]) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[]) message));
        } else if (ClassUtils.isJavaLangType(message.getClass()) || message instanceof CharSequence) {
            String s = message.toString();
            return new TextWebSocketFrame(s);
        } else if (message instanceof ByteBuf) {
            return new BinaryWebSocketFrame((ByteBuf) message);
        } else if (message instanceof ByteBuffer) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((ByteBuffer) message));
        } else {
            Optional<MediaTypeCodec> codec = codecRegistry.findCodec(mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
            if (codec.isPresent()) {
                io.micronaut.core.io.buffer.ByteBuffer encoded = codec.get().encode(message, new NettyByteBufferFactory(UnpooledByteBufAllocator.DEFAULT));
                return new TextWebSocketFrame((ByteBuf) encoded.asNativeBuffer());
            }
        }
        throw new WebSocketSessionException("Unable to encode WebSocket message: " + message);
    }
}
