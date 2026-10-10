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
import org.jspecify.annotations.Nullable;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
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

    private static final String BROADCAST_FAILURE = "Broadcast Failure: ";
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

    /**
     * Broadcast and wait until the message is written to the matching sessions.
     *
     * <p>Do not call it on an event loop thread, e.g. in a handler that runs on the event loop:
     * the writes it waits for may have to run on that thread, which then blocks for good. Use
     * {@link #broadcastAsync(Object, MediaType, Predicate)} there.</p>
     *
     * @param message   The message
     * @param mediaType The media type of the message
     * @param filter    The filter
     * @param <T>       The message type
     */
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
        return Flux.create(emitter -> broadcastFrame(message, mediaType, filter, error -> {
            if (error != null) {
                emitter.error(error);
            } else {
                emitter.next(message);
                emitter.complete();
            }
        }), FluxSink.OverflowStrategy.BUFFER);
    }

    @Override
    public <T> CompletableFuture<T> broadcastAsync(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        if (getClass() != NettyServerWebSocketBroadcaster.class) {
            // a subclass may change broadcast: broadcast through it, as before
            return WebSocketBroadcaster.super.broadcastAsync(message, mediaType, filter);
        }
        CompletableFuture<T> broadcast = new CompletableFuture<>();
        broadcastFrame(message, mediaType, filter, error -> {
            if (error != null) {
                broadcast.completeExceptionally(error);
            } else {
                broadcast.complete(message);
            }
        });
        return broadcast;
    }

    /**
     * Write the message to the matching sessions.
     *
     * @param message   The message
     * @param mediaType The media type of the message
     * @param filter    The filter of the sessions
     * @param done      Called with the failure of the broadcast, or {@code null} once it is written
     */
    private void broadcastFrame(Object message, MediaType mediaType, Predicate<WebSocketSession> filter, Consumer<@Nullable Throwable> done) {
        try {
            WebSocketFrame frame = webSocketMessageEncoder.encodeMessage(message, mediaType);
            // a filter that throws must not stop the group half way: it would not release the frame
            Throwable[] filterFailure = new Throwable[1];
            webSocketSessionRepository.getChannelGroup().writeAndFlush(frame, ch -> {
                Attribute<NettyWebSocketSession> attr = ch.attr(NettyWebSocketSession.WEB_SOCKET_SESSION_KEY);
                NettyWebSocketSession s = attr.get();
                if (s == null || !s.isOpen()) {
                    return false;
                }
                try {
                    return filter.test(s);
                } catch (Throwable e) {
                    if (filterFailure[0] == null) {
                        filterFailure[0] = e;
                    }
                    return false;
                }
            }).addListener(future -> {
                if (filterFailure[0] != null) {
                    done.accept(new WebSocketSessionException(BROADCAST_FAILURE + filterFailure[0].getMessage(), filterFailure[0]));
                    return;
                }
                if (!future.isSuccess()) {
                    Throwable cause = extractBroadcastFailure(future.cause());
                    if (cause != null) {
                        done.accept(new WebSocketSessionException(BROADCAST_FAILURE + cause.getMessage(), cause));
                        return;
                    }
                }
                done.accept(null);
            });
        } catch (Throwable e) {
            done.accept(new WebSocketSessionException(BROADCAST_FAILURE + e.getMessage(), e));
        }
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
