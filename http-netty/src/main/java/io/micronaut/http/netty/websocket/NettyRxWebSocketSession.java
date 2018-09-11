/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodec;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.RxWebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the {@link RxWebSocketSession} interface for Netty and RxJava.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class NettyRxWebSocketSession extends MutableConvertibleValuesMap<Object> implements RxWebSocketSession {
    /**
     * The WebSocket session is stored within a Channel attribute using the given key.
     */
    public static final AttributeKey<NettyRxWebSocketSession> WEB_SOCKET_SESSION_KEY = AttributeKey.newInstance("micronaut.websocket.session");

    private final String id;
    private final Channel channel;
    private final HttpRequest<?> request;
    private final String protocolVersion;
    private final boolean isSecure;
    private final MediaTypeCodecRegistry codecRegistry;

    /**
     * Creates a new netty web socket session.
     * @param id The ID
     * @param channel The channel
     * @param request The original request used to create the session
     * @param codecRegistry The codec registry
     * @param protocolVersion The protocol version
     * @param isSecure Whether the session is secure
     */
    protected NettyRxWebSocketSession(
            String id,
            Channel channel,
            HttpRequest<?> request,
            MediaTypeCodecRegistry codecRegistry,
            String protocolVersion,
            boolean isSecure) {
        this.id = id;
        this.channel = channel;
        this.request = request;
        this.protocolVersion = protocolVersion;
        this.isSecure = isSecure;
        this.channel.attr(WEB_SOCKET_SESSION_KEY).set(this);
        this.codecRegistry = codecRegistry;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isOpen() {
        return channel.isOpen() && channel.isActive();
    }

    @Override
    public boolean isSecure() {
        return isSecure;
    }

    @Override
    public Set<? extends RxWebSocketSession> getOpenSessions() {
        return Collections.emptySet();
    }

    @Override
    public URI getRequestURI() {
        return request.getUri();
    }

    @Override
    public ConvertibleMultiValues<String> getRequestParameters() {
        return request.getParameters();
    }

    @Override
    public String getProtocolVersion() {
        return protocolVersion;
    }

    @Override
    public <T> CompletableFuture<T> sendAsync(T message, MediaType mediaType) {
        if (isOpen()) {
            if (message != null) {
                CompletableFuture<T> future = new CompletableFuture<>();

                WebSocketFrame frame = encodeMessage(message, mediaType);
                channel.writeAndFlush(frame).addListener(f -> {
                    if (f.isSuccess()) {
                        future.complete(message);
                    } else {
                        future.completeExceptionally(f.cause());
                    }
                });
                return future;
            } else {
                return CompletableFuture.completedFuture(null);
            }
        } else {
            throw new WebSocketSessionException("Session closed");
        }
    }

    @Override
    public void sendSync(Object message, MediaType mediaType) {
        if (isOpen()) {
            if (message != null) {
                try {
                    WebSocketFrame frame = encodeMessage(message, mediaType);
                    channel.writeAndFlush(frame).sync();
                } catch (InterruptedException e) {
                    throw new WebSocketSessionException("Send interrupt: " + e.getMessage(), e);
                }
            }
        } else {
            throw new WebSocketSessionException("Session closed");
        }
    }

    @Override
    public <T> Flowable<T> send(T message, MediaType mediaType) {
        if (message == null) {
            return Flowable.empty();
        }

        return Flowable.create(emitter -> {
            if (!isOpen()) {
                emitter.onError(new WebSocketSessionException("Session closed"));
            } else {
                WebSocketFrame frame = encodeMessage(message, mediaType);

                ChannelFuture channelFuture = channel.writeAndFlush(frame);
                channelFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        emitter.onNext(message);
                        emitter.onComplete();
                    } else {
                        emitter.onError(future.cause());
                    }
                });
            }
        }, BackpressureStrategy.ERROR);
    }

    @Override
    public void close() {
        close(CloseReason.NORMAL);
    }

    @Override
    public void close(CloseReason closeReason) {
        if (channel.isOpen()) {
            channel.writeAndFlush(new CloseWebSocketFrame(closeReason.getCode(), closeReason.getReason()))
                    .addListener(future -> channel.close());
        }
    }

    private WebSocketFrame encodeMessage(Object message, MediaType mediaType) {
        if (ClassUtils.isJavaLangType(message.getClass())) {
            String s = message.toString();
            return new TextWebSocketFrame(s);
        } else if (message instanceof byte[]) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((byte[]) message));
        } else if (message instanceof ByteBuf) {
            return new BinaryWebSocketFrame((ByteBuf) message);
        } else if (message instanceof ByteBuffer) {
            return new BinaryWebSocketFrame(Unpooled.wrappedBuffer((ByteBuffer) message));
        } else {
            Optional<MediaTypeCodec> codec = codecRegistry.findCodec(mediaType != null ? mediaType : MediaType.APPLICATION_JSON_TYPE);
            if (codec.isPresent()) {
                io.micronaut.core.io.buffer.ByteBuffer encoded = codec.get().encode(message, new NettyByteBufferFactory(channel.alloc()));
                return new TextWebSocketFrame((ByteBuf) encoded.asNativeBuffer());
            }
        }
        throw new WebSocketSessionException("Unable to encode WebSocket message: " + message);
    }
}
