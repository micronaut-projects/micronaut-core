/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClientConfiguration;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * A {@link WebSocketClient} whose connections complete a {@link CompletionStage} instead of
 * emitting from a Reactive Streams publisher.
 *
 * <p>Each call connects once. Cancelling the future of the stage (see
 * {@link CompletionStage#toCompletableFuture()}) before the connection is established aborts it;
 * a client endpoint that connects after that is closed.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
public interface AsyncWebSocketClient extends AutoCloseable {

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param request            The original request to establish the connection
     * @param <T>                The generic type
     * @return A {@link CompletionStage} that completes with the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request);

    /**
     * Connect the given client endpoint type. Unlike {@link #connect(Class, URI)} this method uses
     * the value declared within the {@link io.micronaut.websocket.annotation.ClientWebSocket} as
     * the URI and expands the URI with the given parameters.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param parameters         The URI parameters for the endpoint
     * @param <T>                The generic type
     * @return A {@link CompletionStage} that completes with the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters);

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param uri                The URI to connect over
     * @param <T>                The generic type
     * @return A {@link CompletionStage} that completes with the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    default <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, String uri) {
        return connect(clientEndpointType, URI.create(uri));
    }

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param uri                The URI to connect over
     * @param <T>                The generic type
     * @return A {@link CompletionStage} that completes with the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    default <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, URI uri) {
        return connect(clientEndpointType, HttpRequest.GET(uri));
    }

    @Override
    void close();

    /**
     * Create a new {@link AsyncWebSocketClient}.
     * Note that this method should only be used outside the context of a Micronaut application.
     * The returned client is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param uri The base URI
     * @return The client
     */
    static AsyncWebSocketClient create(@Nullable URI uri) {
        return WebSocketClientFactoryResolver.getFactory().createAsyncWebSocketClient(uri);
    }

    /**
     * Create a new {@link AsyncWebSocketClient} with the specified configuration. Note that this
     * method should only be used outside the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param uri           The base URI
     * @param configuration The client configuration
     * @return The client
     */
    static AsyncWebSocketClient create(@Nullable URI uri, HttpClientConfiguration configuration) {
        return WebSocketClientFactoryResolver.getFactory().createAsyncWebSocketClient(uri, configuration);
    }
}
