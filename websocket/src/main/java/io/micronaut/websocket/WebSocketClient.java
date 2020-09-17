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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import org.reactivestreams.Publisher;
import java.net.URI;
import java.util.Map;

/**
 * Interface that provides a way to connect a client over WebSocket.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface WebSocketClient extends AutoCloseable {

    /**
     * Constant for HTTP scheme.
     */
    String SCHEME_WS = "ws";

    /**
     * Constant for HTTPS scheme.
     */
    String SCHEME_WSS = "wss";


    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param request The original request to establish the connection
     * @param <T> The generic type
     * @return A {@link Publisher} that emits the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    <T extends AutoCloseable> Publisher<T> connect(
            Class<T> clientEndpointType,
            MutableHttpRequest<?> request
    );

    /**
     * Connect the given client endpoint type. Unlike {@link #connect(Class, URI)} this method will the value declared within the {@link io.micronaut.websocket.annotation.ClientWebSocket} as the URI
     * and expand the URI with the given parameters.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param parameters The URI parameters for the endpoint
     * @param <T> The generic type
     * @return A {@link Publisher} that emits the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    <T extends AutoCloseable> Publisher<T> connect(
            Class<T> clientEndpointType,
            Map<String, Object> parameters
    );

    @Override
    void close();

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param uri The URI to connect over
     * @param <T> The generic type
     * @return A {@link Publisher} that emits the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    default <T extends AutoCloseable> Publisher<T> connect(
            Class<T> clientEndpointType,
            String uri
    ) {
        return connect(clientEndpointType, URI.create(uri));
    }

    /**
     * Connect the given client endpoint type to the URI over WebSocket.
     *
     * @param clientEndpointType The endpoint type. Should be a class annotated with {@link io.micronaut.websocket.annotation.ClientWebSocket}
     * @param uri The URI to connect over
     * @param <T> The generic type
     * @return A {@link Publisher} that emits the {@link io.micronaut.websocket.annotation.ClientWebSocket} instance
     */
    default <T extends AutoCloseable> Publisher<T> connect(
            Class<T> clientEndpointType,
            URI uri
    ) {
        return connect(clientEndpointType, HttpRequest.GET(uri));
    }
}
