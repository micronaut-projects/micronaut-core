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
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.sse.AsyncSseClient;
import io.micronaut.http.sse.Event;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Default implementation of {@link AsyncSseClient} backed by {@link NettyHttpClient}, without
 * Reactor. Cancelling the future of an exchange before the response arrived cancels the
 * exchange, and closes the events of a response that arrives anyway.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultAsyncSseClient implements AsyncSseClient {

    private final NettyHttpClient nettyHttpClient;

    /**
     * @param nettyHttpClient The delegate client
     */
    DefaultAsyncSseClient(NettyHttpClient nettyHttpClient) {
        this.nettyHttpClient = nettyHttpClient;
    }

    @Override
    public <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        ExecutionFlow<HttpResponse<BodyElements<Event<B>>>> flow = nettyHttpClient.exchangeEventStreamFlow(request, eventType, errorType);
        CompletableFuture<HttpResponse<BodyElements<Event<B>>>> future = new CompletableFuture<>();
        future.whenComplete((response, error) -> {
            if (error instanceof CancellationException) {
                flow.cancel();
            }
        });
        flow.onComplete((response, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else if (!future.complete(response)) {
                // cancelled before the response arrived: nobody reads the events
                BodyElements<Event<B>> events = response.body();
                if (events != null) {
                    events.close();
                }
            }
        });
        return future;
    }

    @Override
    public void close() {
        nettyHttpClient.close();
    }
}
