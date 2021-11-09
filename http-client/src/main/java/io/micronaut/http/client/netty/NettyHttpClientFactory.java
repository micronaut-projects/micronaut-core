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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.HttpClientFactory;
import io.micronaut.http.client.ProxyHttpClient;
import io.micronaut.http.client.ProxyHttpClientFactory;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.StreamingHttpClientFactory;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.client.sse.SseClientFactory;
import io.micronaut.websocket.WebSocketClient;
import io.micronaut.websocket.WebSocketClientFactory;

import java.net.URI;

/**
 * A factory to create Netty HTTP clients.
 *
 * @author Sergio del Amo
 * @since 3.0
 */
@Internal
public class NettyHttpClientFactory implements
        HttpClientFactory,
        SseClientFactory,
        ProxyHttpClientFactory,
        StreamingHttpClientFactory,
        WebSocketClientFactory {

    @NonNull
    @Override
    public HttpClient createClient(URI uri) {
        return createNettyClient(uri);
    }

    @NonNull
    @Override
    public HttpClient createClient(URI uri, @NonNull HttpClientConfiguration configuration) {
        return createNettyClient(uri, configuration);
    }

    @NonNull
    @Override
    public ProxyHttpClient createProxyClient(URI uri) {
        return createNettyClient(uri);
    }

    @NonNull
    @Override
    public ProxyHttpClient createProxyClient(URI uri, @NonNull HttpClientConfiguration configuration) {
        return createNettyClient(uri, configuration);
    }

    @NonNull
    @Override
    public SseClient createSseClient(@Nullable URI uri) {
        return createNettyClient(uri);
    }

    @NonNull
    @Override
    public SseClient createSseClient(@Nullable URI uri, @NonNull HttpClientConfiguration configuration) {
        return createNettyClient(uri, configuration);
    }

    @NonNull
    @Override
    public StreamingHttpClient createStreamingClient(URI uri) {
        return createNettyClient(uri);
    }

    @NonNull
    @Override
    public StreamingHttpClient createStreamingClient(URI uri, @NonNull HttpClientConfiguration configuration) {
        return createNettyClient(uri, configuration);
    }

    @NonNull
    @Override
    public WebSocketClient createWebSocketClient(URI uri) {
        return createNettyClient(uri);
    }

    @NonNull
    @Override
    public WebSocketClient createWebSocketClient(URI uri, @NonNull HttpClientConfiguration configuration) {
        return createNettyClient(uri, configuration);
    }

    private DefaultHttpClient createNettyClient(URI uri) {
        return new DefaultHttpClient(uri);
    }

    private DefaultHttpClient createNettyClient(URI uri, HttpClientConfiguration configuration) {
        return new DefaultHttpClient(uri, configuration);
    }

}
