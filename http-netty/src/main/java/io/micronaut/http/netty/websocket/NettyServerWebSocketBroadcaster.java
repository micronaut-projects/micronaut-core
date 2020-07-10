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
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;

import javax.inject.Singleton;
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
                Attribute<NettyRxWebSocketSession> attr = ch.attr(NettyRxWebSocketSession.WEB_SOCKET_SESSION_KEY);
                NettyRxWebSocketSession s = attr.get();
                return s != null && s.isOpen() && filter.test(s);
            }).sync();
        } catch (InterruptedException e) {
            throw new WebSocketSessionException("Broadcast Interrupted");
        }
    }

    @Override
    public <T> Flowable<T> broadcast(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        return Flowable.create(emitter -> {
            try {
                WebSocketFrame frame = webSocketMessageEncoder.encodeMessage(message, mediaType);
                webSocketSessionRepository.getChannelGroup().writeAndFlush(frame, ch -> {
                    Attribute<NettyRxWebSocketSession> attr = ch.attr(NettyRxWebSocketSession.WEB_SOCKET_SESSION_KEY);
                    NettyRxWebSocketSession s = attr.get();
                    return s != null && s.isOpen() && filter.test(s);
                }).addListener(future -> {
                    if (future.isSuccess()) {
                        emitter.onNext(message);
                        emitter.onComplete();
                    } else {
                        Throwable cause = future.cause();
                        emitter.onError(new WebSocketSessionException("Broadcast Failure: " + cause.getMessage(), cause));
                    }
                });
            } catch (Throwable e) {
                emitter.onError(new WebSocketSessionException("Broadcast Failure: " + e.getMessage(), e));
            }
        }, BackpressureStrategy.BUFFER);
    }
}
