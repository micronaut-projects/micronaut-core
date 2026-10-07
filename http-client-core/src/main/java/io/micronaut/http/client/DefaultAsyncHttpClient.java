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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link AsyncHttpClient} and {@link AsyncStreamingHttpClient} backed by
 * {@link AbstractHttpClient}. The exchanges run without Reactor; the elements of a JSON stream are
 * decoded by the chunked JSON reader.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
@Internal
final class DefaultAsyncHttpClient implements AsyncStreamingHttpClient {

    private final AbstractHttpClient<?> client;

    /**
     * Constructor used to wrap an existing {@link AbstractHttpClient} instance.
     *
     * @param client The delegate client
     */
    DefaultAsyncHttpClient(AbstractHttpClient<?> client) {
        this.client = client;
    }

    @Override
    public <I, O, E> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request,
                                                               @Nullable Argument<O> bodyType,
                                                               Argument<E> errorType) {
        // cancelling the future cancels the exchange, as the future of a reactive exchange does
        return ElementsFutures.result(client.exchangeFlow(request, bodyType, errorType));
    }

    @Override
    public <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
        return ElementsFutures.response(client.exchangeStreamFlow(request, errorType));
    }

    @Override
    public <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        return ElementsFutures.elements(client.jsonStreamFlow(request, type, errorType));
    }

    @Override
    public DefaultAsyncHttpClient start() {
        client.start();
        return this;
    }

    @Override
    public DefaultAsyncHttpClient stop() {
        client.stop();
        return this;
    }

    @Override
    public boolean isRunning() {
        return client.isRunning();
    }

    @Override
    public void close() {
        client.close();
    }

}
