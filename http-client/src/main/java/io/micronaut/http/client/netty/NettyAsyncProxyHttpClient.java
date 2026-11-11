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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.client.AsyncProxyHttpClient;
import io.micronaut.http.client.ProxyRequestOptions;

import java.util.concurrent.CompletionStage;

/**
 * {@link AsyncProxyHttpClient} backed by {@link NettyHttpClient}: the future of a proxied
 * exchange is completed by its {@link io.micronaut.core.execution.ExecutionFlow} directly,
 * without a reactive streams publisher in between.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyAsyncProxyHttpClient implements AsyncProxyHttpClient {

    private final NettyHttpClient nettyHttpClient;

    NettyAsyncProxyHttpClient(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = nettyHttpClient;
    }

    @Override
    public CompletionStage<MutableHttpResponse<?>> proxy(HttpRequest<?> request, ProxyRequestOptions options) {
        return nettyHttpClient.proxyAsync(request, options);
    }

    @Override
    public void close() {
        nettyHttpClient.close();
    }
}
