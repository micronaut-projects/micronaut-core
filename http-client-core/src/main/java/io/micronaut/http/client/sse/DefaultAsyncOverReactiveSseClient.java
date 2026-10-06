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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.ResponseSubscriberElements;
import io.micronaut.http.sse.Event;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Default {@link AsyncSseClient} implementation that adapts the reactive
 * {@link SseClient#exchangeEventStream(HttpRequest, Argument, Argument)}: the first emitted
 * response completes the exchange, and each read requests the next one. Cancelling the future of
 * an exchange before the response arrived cancels the subscription.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultAsyncOverReactiveSseClient implements AsyncSseClient {

    private final SseClient sseClient;

    /**
     * @param sseClient The delegate client
     */
    public DefaultAsyncOverReactiveSseClient(SseClient sseClient) {
        this.sseClient = Objects.requireNonNull(sseClient, "sseClient");
    }

    @Override
    public <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        return ResponseSubscriberElements.exchange(sseClient.exchangeEventStream(request, eventType, errorType), Function.identity());
    }

    @Override
    public void close() throws IOException {
        if (sseClient instanceof Closeable closeable) {
            closeable.close();
        }
    }
}
