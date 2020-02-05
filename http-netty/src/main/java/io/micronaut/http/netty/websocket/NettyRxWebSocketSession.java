/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.ConvertibleMultiValues;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.MediaTypeCodecRegistry;
import io.micronaut.websocket.CloseReason;
import io.micronaut.websocket.RxWebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.AttributeKey;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Implementation of the {@link RxWebSocketSession} interface for Netty and RxJava.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class NettyRxWebSocketSession implements RxWebSocketSession {
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
    private final MutableConvertibleValues<Object> attributes;
    private final WebSocketMessageEncoder messageEncoder;

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
        this.messageEncoder = new WebSocketMessageEncoder(this.codecRegistry);
        this.attributes = request.getAttribute("micronaut.SESSION", MutableConvertibleValues.class).orElseGet(() -> new MutableConvertibleValuesMap());
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
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

                WebSocketFrame frame = messageEncoder.encodeMessage(message, mediaType);
                channel.writeAndFlush(frame).addListener(f -> {
                    if (f.isSuccess()) {
                        future.complete(message);
                    } else {
                        future.completeExceptionally(new WebSocketSessionException("Send Failure: " + f.cause().getMessage(), f.cause()));
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
                    WebSocketFrame frame = messageEncoder.encodeMessage(message, mediaType);
                    channel.writeAndFlush(frame).sync().get();
                } catch (InterruptedException e) {
                    throw new WebSocketSessionException("Send interrupt: " + e.getMessage(), e);
                } catch (ExecutionException e) {
                    throw new WebSocketSessionException("Send Failure: " + e.getMessage(), e);
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
                WebSocketFrame frame = messageEncoder.encodeMessage(message, mediaType);

                ChannelFuture channelFuture = channel.writeAndFlush(frame);
                channelFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        emitter.onNext(message);
                        emitter.onComplete();
                    } else {
                        emitter.onError(new WebSocketSessionException("Send Failure: " + future.cause().getMessage(), future.cause()));
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

    @Override
    public String toString() {
        return "WebSocket Session: " + getId();
    }

    @Override
    public MutableConvertibleValues<Object> put(CharSequence key, @Nullable Object value) {
        return attributes.put(key, value);
    }

    @Override
    public MutableConvertibleValues<Object> remove(CharSequence key) {
        return attributes.remove(key);
    }

    @Override
    public MutableConvertibleValues<Object> clear() {
        return attributes.clear();
    }

    @Override
    public Set<String> names() {
        return attributes.names();
    }

    @Override
    public Collection<Object> values() {
        return attributes.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return attributes.get(name, conversionContext);
    }
}
