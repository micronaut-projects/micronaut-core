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
import io.micronaut.http.MediaType;
import io.micronaut.websocket.WebSocketBroadcaster;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.Attribute;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
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
     * @param webSocketMessageEncoder A instance of {@link io.micronaut.http.netty.websocket.WebSocketMessageEncoder} responsible of encoding WebSocket messages.
     * @param webSocketSessionRepository A instance of {@link io.micronaut.http.netty.websocket.WebSocketSessionRepository}. Defines a ChannelGroup repository to handle WebSockets.
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
                    if (future.isSuccess()) {
                        emitter.next(message);
                        emitter.complete();
                    } else {
                        Throwable cause = future.cause();
                        emitter.error(new WebSocketSessionException("Broadcast Failure: " + cause.getMessage(), cause));
                    }
                });
            } catch (Throwable e) {
                emitter.error(new WebSocketSessionException("Broadcast Failure: " + e.getMessage(), e));
            }
        }, FluxSink.OverflowStrategy.BUFFER);
    }
}
