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
package io.micronaut.websocket;

import io.micronaut.http.MediaType;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Predicate;

/**
 * Defines WebSocket methods to broadcast messages.
 *
 * @author sdelamo
 * @since 1.0
 */
public interface WebSocketBroadcaster {
    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections that match the given filter.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link io.micronaut.websocket.annotation.OnOpen} and {@link io.micronaut.websocket.annotation.OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param filter The filter to apply
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    <T> Publisher<T> broadcast(T message, MediaType mediaType, Predicate<WebSocketSession> filter);

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link io.micronaut.websocket.annotation.OnOpen} and {@link io.micronaut.websocket.annotation.OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    default <T> Publisher<T> broadcast(T message, MediaType mediaType) {
        return broadcast(message, mediaType, (s) -> true);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link io.micronaut.websocket.annotation.OnOpen} and {@link io.micronaut.websocket.annotation.OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    default <T> Publisher<T> broadcast(T message) {
        return broadcast(message, MediaType.APPLICATION_JSON_TYPE, (s) -> true);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections that match the given filter.
     * The resulting {@link Publisher} does not start sending until subscribed to.
     * If you return it from Micronaut annotated methods such as {@link io.micronaut.websocket.annotation.OnOpen} and {@link io.micronaut.websocket.annotation.OnMessage},
     * Micronaut will subscribe to it and send the message without blocking.
     *
     * @param message The message
     * @param filter The filter to apply
     * @param <T> The message type
     * @return A {@link Publisher} that either emits an error or emits the message once it has been published successfully.
     */
    default <T> Publisher<T> broadcast(T message, Predicate<WebSocketSession> filter) {
        Objects.requireNonNull(filter, "The filter cannot be null");
        return broadcast(message, MediaType.APPLICATION_JSON_TYPE, filter);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param filter The filter
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    default <T> CompletableFuture<T> broadcastAsync(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Flowable.fromPublisher(broadcast(message, mediaType, filter)).subscribe(
                (o) -> { },
                future::completeExceptionally,
                () -> future.complete(message)
        );
        return future;
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    default <T> CompletableFuture<T> broadcastAsync(T message) {
        return broadcastAsync(message, MediaType.APPLICATION_JSON_TYPE, (o) -> true);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections that match the given filter.
     *
     * @param message The message
     * @param filter The filter to apply
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    default <T> CompletableFuture<T> broadcastAsync(T message, Predicate<WebSocketSession> filter) {
        return broadcastAsync(message, MediaType.APPLICATION_JSON_TYPE, filter);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param <T> The message type
     * @return A {@link CompletableFuture} that tracks the execution. {@link CompletableFuture#get()} and related methods will return the message on success, on error throw the underlying Exception.
     */
    default <T> CompletableFuture<T> broadcastAsync(T message, MediaType mediaType) {
        return broadcastAsync(message, mediaType, (o) -> true);
    }


    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param filter The filter
     * @param <T> The message type
     */
    default <T> void broadcastSync(T message, MediaType mediaType, Predicate<WebSocketSession> filter) {
        try {
            broadcastAsync(message, mediaType, filter).get();
        } catch (InterruptedException e) {
            throw new WebSocketSessionException("Broadcast Interrupted");
        } catch (ExecutionException e) {
            throw new WebSocketSessionException("Broadcast Failure: " + e.getMessage(), e);
        }
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param <T> The message type
     */
    default <T> void broadcastSync(T message) {
        broadcastSync(message, MediaType.APPLICATION_JSON_TYPE, (o) -> true);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections that match the given filter.
     *
     * @param message The message
     * @param filter The filter to apply
     * @param <T> The message type
     */
    default <T> void broadcastSync(T message, Predicate<WebSocketSession> filter) {
        broadcastSync(message, MediaType.APPLICATION_JSON_TYPE, filter);
    }

    /**
     * When used on the server this method will broadcast a message to all open WebSocket connections.
     *
     * @param message The message
     * @param mediaType The media type of the message. Used to lookup an appropriate codec via the {@link io.micronaut.http.codec.MediaTypeCodecRegistry}.
     * @param <T> The message type
     */
    default <T> void broadcastSync(T message, MediaType mediaType) {
        broadcastSync(message, mediaType, (o) -> true);
    }
}
