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
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.CloseableByteBody;
import io.micronaut.http.client.AsyncRawHttpClient;
import io.micronaut.http.client.RawRequestOptions;
import io.micronaut.http.client.RawResponseFuture;
import io.micronaut.http.netty.body.NettyByteBodyFactory;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * {@link AsyncRawHttpClient} backed by {@link NettyHttpClient}: the future of an exchange is
 * completed by its {@link io.micronaut.core.execution.ExecutionFlow} directly, without a
 * reactive streams publisher in between.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class NettyAsyncRawHttpClient implements AsyncRawHttpClient {
    private final NettyHttpClient nettyHttpClient;

    NettyAsyncRawHttpClient(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = nettyHttpClient;
    }

    @Override
    public CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody) {
        return exchange0(request, requestBody, null);
    }

    @Override
    public CompletionStage<HttpResponse<?>> exchange(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, RawRequestOptions options) {
        Objects.requireNonNull(options, "options");
        return exchange0(request, requestBody, options);
    }

    private CompletionStage<HttpResponse<?>> exchange0(HttpRequest<?> request, @Nullable CloseableByteBody requestBody, @Nullable RawRequestOptions options) {
        CloseableByteBody body = requestBody == null ? NettyByteBodyFactory.empty() : requestBody;
        return RawResponseFuture.of(nettyHttpClient.rawExchangeFlow(PropagatedContext.getOrEmpty(), request, body, null, options), body);
    }

    @Override
    public void close() {
        nettyHttpClient.close();
    }
}
