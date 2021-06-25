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
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.ReactiveHttpClientFactory;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.websocket.WebSocketClient;

import java.net.URL;

/**
 * Implementation of {@link ReactiveHttpClientFactory} for Netty.
 *
 * @author graemerocher
 * @since 2.0
 */
@Internal
public class NettyReactiveHttpClientFactory implements ReactiveHttpClientFactory<HttpClient, SseClient, StreamingHttpClient, WebSocketClient> {
    @Override
    public HttpClient createClient(URL url) {
        return new DefaultHttpClient(url);
    }

    @Override
    public HttpClient createClient(URL url, HttpClientConfiguration configuration) {
        return new DefaultHttpClient(url, configuration);
    }

    @Override
    public StreamingHttpClient createStreamingClient(URL url) {
        return new DefaultHttpClient(url);
    }

    @Override
    public StreamingHttpClient createStreamingClient(URL url, HttpClientConfiguration configuration) {
        return new DefaultHttpClient(url, configuration);
    }

    @Override
    public SseClient createSseClient(URL url) {
        return new DefaultHttpClient(url);
    }

    @Override
    public SseClient createSseClient(URL url, HttpClientConfiguration configuration) {
        return new DefaultHttpClient(url, configuration);
    }

    @Override
    public WebSocketClient createWebSocketClient(URL url) {
        return new DefaultHttpClient(url);
    }

    @Override
    public WebSocketClient createWebSocketClient(URL url, HttpClientConfiguration configuration) {
        return new DefaultHttpClient(url, configuration);
    }
}
