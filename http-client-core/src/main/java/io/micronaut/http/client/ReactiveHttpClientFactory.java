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
package io.micronaut.http.client;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.websocket.WebSocketClient;

import java.net.URL;

/**
 * Factory interface for creating clients.
 *
 * @author Sergio del Amo
 * @since 3.0
 * @param <T> Reactive HTTP Client
 * @param <E> Server Sent Event Client
 * @param <S> Reactive Streaming HTTP Client
 * @param <W> Web Socket Client
 */
public interface ReactiveHttpClientFactory<T extends HttpClient, E extends SseClient, S extends StreamingHttpClient, W extends WebSocketClient>  {
    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    T createClient(@Nullable URL url);

    /**
     * Create a new {@link HttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    @NonNull
    T createClient(@Nullable URL url, HttpClientConfiguration configuration);

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    S createStreamingClient(@Nullable URL url);

    /**
     * Create a new {@link HttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     * @since 2.2.0
     */
    @NonNull
    S createStreamingClient(@Nullable URL url, HttpClientConfiguration configuration);

    /**
     * Create a new {@link SseClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    E createSseClient(@Nullable URL url);

    /**
     * Create a new {@link SseClient} with the specified configuration. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     */
    @NonNull
    E createSseClient(@Nullable URL url,  HttpClientConfiguration configuration);

    /**
     * Create a new {@link WebSocketClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    @NonNull
    W createWebSocketClient(@Nullable URL url);

    /**
     * Create a new {@link WebSocketClient} with the specified configuration. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration The client configuration
     * @return The client
     */
    @NonNull
    W createWebSocketClient(@Nullable URL url,  HttpClientConfiguration configuration);
}

