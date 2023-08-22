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

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.MediaType;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroupException;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.Attribute;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.nio.channels.ClosedChannelException;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Netty implementation of {@link io.micronaut.websocket.WebSocketBroadcaster}.
 *
 * @author sdelamo
 * @since 1.0
 */
@Singleton
@Requires(beans = WebSocketSessionRepository.class)
public class NettyServerWebSocketBroadcaster implements WebSocketBroadcaster {

    private final WebSocketMessageEncoder webSocketMessageEncoder;
    private final WebSocketSessionRepository webSocketSessionRepository;

    /**
     *
     * @param webSocketMessageEncoder An instance of {@link io.micronaut.http.netty.websocket.WebSocketMessageEncoder} responsible for encoding WebSocket messages.
     * @param webSocketSessionRepository An instance of {@link io.micronaut.http.netty.websocket.WebSocketSessionRepository}. Defines a ChannelGroup repository to handle WebSockets.
     */
    public NettyServerWebSocketBroadcaster(WebSocketMessageEncoder webSocketMessageEncoder,
                                           WebSocketSessionRepository webSocketSessionRepository) {
        this.webSocketMessageEncoder = webSocketMessageEncoder;
        this.webSocketSessionRepository = webSocketSessionRepository;
    }

    @Override
    public <T> void broadcastSync(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        WebSocketFrame frame = webSocketMessageEncoder.encodeMessage(message, mediaType);
        try {
            webSocketSessionRepository.getChannelGroup().writeAndFlush(frame, ch -> {
                Attribute<NettyWebSocketSession> attr = ch.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY);
                NettyWebSocketSession s = attr.get();
                return s != null && s.isOpen() && filter.test(s);
            }).sync();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WebSocketSessionException("Broadcast Interrupted");
        }
    }

    @Override
    public <T> Publisher<T> broadcast(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        return Flux.create(emitter -> {
            try {
                WebSocketFrame frame = webSocketMessageEncoder.encodeMessage(message, mediaType);
                webSocketSessionRepository.getChannelGroup().writeAndFlush(frame, ch -> {
                    Attribute<NettyWebSocketSession> attr = ch.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY);
                    NettyWebSocketSession s = attr.get();
                    return s != null && s.isOpen() && filter.test(s);
                }).addListener(future -> {
                    if (!future.isSuccess()) {
                        Throwable cause = extractBroadcastFailure(future.cause());
                        if (cause != null) {
                            emitter.error(new WebSocketSessionException("Broadcast Failure: " + cause.getMessage(), cause));
                            return;
                        }
                    }
                    emitter.next(message);
                    emitter.complete();
                });
            } catch (Throwable e) {
                emitter.error(new WebSocketSessionException("Broadcast Failure: " + e.getMessage(), e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }

    /**
     * Attempt to extract a single failure from a failure of {@link io.netty.channel.group.ChannelGroup#write}
     * exception. {@link io.netty.channel.group.ChannelGroup} aggregates exceptions into a {@link ChannelGroupException}
     * that has no useful stacktrace. If there was only one actual failure, we will just forward that instead of the
     * {@link ChannelGroupException}.
     *
     * We also need to ignore {@link ClosedChannelException}s.
     */
    @Nullable
    private Throwable extractBroadcastFailure(Throwable failure) {
        if (failure instanceof ChannelGroupException exception) {
            Throwable singleCause = null;
            for (Map.Entry<Channel, Throwable> entry : exception) {
                Throwable entryCause = extractBroadcastFailure(entry.getValue());
                if (entryCause != null) {
                    if (singleCause == null) {
                        singleCause = entryCause;
                    } else {
                        return failure;
                    }
                }
            }
            return singleCause;
        } else if (failure instanceof ClosedChannelException) {
            // ClosedChannelException can happen when there is a race condition between the call to writeAndFlush and
            // the closing of a channel. session.isOpen will still return true, but when to write is actually
            // performed, the channel is closed. Since we would have skipped to write anyway had we known the channel
            // would go away, we can safely ignore this error.
            return null;
        } else {
            return failure;
        }
    }
}
