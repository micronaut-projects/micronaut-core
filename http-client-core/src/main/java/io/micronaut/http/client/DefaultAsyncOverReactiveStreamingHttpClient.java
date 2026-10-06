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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;

import java.util.concurrent.CompletionStage;

/**
 * Default {@link AsyncStreamingHttpClient} implementation that adapts a reactive
 * {@link StreamingHttpClient}. Each read requests the next element of the reactive stream.
 * <ul>
 *     <li>{@link #exchangeStream} completes with the first emitted response. The reactive
 *     {@code exchangeStream} emits no response for an empty body, so the stage then fails.</li>
 *     <li>{@link #dataStream} and {@link #jsonStream} complete once the first element, the end of
 *     the body, or a failure arrived.</li>
 * </ul>
 * The buffers of the reactive client are released after they were emitted, so they are copied.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultAsyncOverReactiveStreamingHttpClient extends DefaultAsyncOverReactiveHttpClient implements AsyncStreamingHttpClient {

    private final StreamingHttpClient streamingHttpClient;

    /**
     * @param streamingHttpClient The delegate client
     */
    public DefaultAsyncOverReactiveStreamingHttpClient(StreamingHttpClient streamingHttpClient) {
        super(streamingHttpClient);
        this.streamingHttpClient = streamingHttpClient;
    }

    @Override
    public <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType) {
        return ResponseSubscriberElements.exchange(streamingHttpClient.exchangeStream(request, errorType), DefaultAsyncOverReactiveStreamingHttpClient::copy);
    }

    @Override
    public <I> CompletionStage<BodyElements<ByteBuffer<?>>> dataStream(HttpRequest<I> request, Argument<?> errorType) {
        return SubscriberBodyElements.subscribe(Publishers.map(streamingHttpClient.dataStream(request, errorType), DefaultAsyncOverReactiveStreamingHttpClient::copy));
    }

    @Override
    public <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        return SubscriberBodyElements.subscribe(streamingHttpClient.jsonStream(request, type, errorType));
    }

    @Override
    public DefaultAsyncOverReactiveStreamingHttpClient start() {
        super.start();
        return this;
    }

    @Override
    public DefaultAsyncOverReactiveStreamingHttpClient stop() {
        super.stop();
        return this;
    }

    private static ByteBuffer<?> copy(ByteBuffer<?> buffer) {
        return ByteArrayBufferFactory.INSTANCE.wrap(buffer.toByteArray());
    }
}
