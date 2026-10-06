/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.AsyncHttpClient;
import io.micronaut.http.client.AsyncStreamingHttpClient;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link AsyncHttpClient} and {@link AsyncStreamingHttpClient} backed by
 * {@link NettyHttpClient}. The exchanges run without Reactor; the elements of a JSON stream are
 * decoded by the chunked JSON reader.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class DefaultAsyncHttpClient implements AsyncStreamingHttpClient {

    private final NettyHttpClient nettyHttpClient;

    /**
     * Constructor used to wrap an existing {@link NettyHttpClient} instance.
     *
     * @param nettyHttpClient The delegate client
     */
    DefaultAsyncHttpClient(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = nettyHttpClient;
    }

    @Override
    public <I, O, E> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request,
                                                               @Nullable Argument<O> bodyType,
                                                               Argument<E> errorType) {
        return nettyHttpClient.exchangeFlow(request, bodyType, errorType).toCompletableFuture();
    }

    @Override
    public <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
        return ElementsFutures.response(nettyHttpClient.exchangeStreamFlow(request, errorType));
    }

    @Override
    public <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        return ElementsFutures.elements(nettyHttpClient.jsonStreamFlow(request, type, errorType));
    }

    @Override
    public DefaultAsyncHttpClient start() {
        nettyHttpClient.start();
        return this;
    }

    @Override
    public DefaultAsyncHttpClient stop() {
        nettyHttpClient.stop();
        return this;
    }

    @Override
    public boolean isRunning() {
        return nettyHttpClient.isRunning();
    }

    @Override
    public void close() {
        nettyHttpClient.close();
    }

}
