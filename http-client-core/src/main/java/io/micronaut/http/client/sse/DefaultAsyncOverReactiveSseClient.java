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
import io.micronaut.http.sse.Event;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.io.Closeable;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        ReactiveEventElements<B> elements = new ReactiveEventElements<>();
        sseClient.exchangeEventStream(request, eventType, errorType).subscribe(elements);
        return elements.response;
    }

    @Override
    public void close() throws IOException {
        if (sseClient instanceof Closeable closeable) {
            closeable.close();
        }
    }

    /**
     * The events of the responses a reactive exchange emits, one request per read.
     *
     * @param <B> The event data type
     */
    private static final class ReactiveEventElements<B> extends PulledBodyElements<Event<B>> implements Subscriber<HttpResponse<Event<B>>> {

        final CompletableFuture<HttpResponse<BodyElements<Event<B>>>> response = new CompletableFuture<>();

        // guarded by this
        private @Nullable Subscription subscription;
        private boolean requested;
        private boolean done;

        ReactiveEventElements() {
            response.whenComplete((r, error) -> {
                if (error instanceof CancellationException) {
                    // cancels the subscription
                    close();
                }
            });
        }

        @Override
        protected void demand() {
            Subscription s;
            synchronized (this) {
                if (done || requested || subscription == null) {
                    return;
                }
                requested = true;
                s = subscription;
            }
            s.request(1);
        }

        @Override
        protected void release() {
            Subscription s;
            synchronized (this) {
                s = done ? null : subscription;
                done = true;
            }
            if (s != null) {
                s.cancel();
            }
        }

        @Override
        public void onSubscribe(Subscription s) {
            boolean cancel;
            synchronized (this) {
                subscription = s;
                cancel = done;
                requested = !cancel;
            }
            if (cancel) {
                s.cancel();
            } else {
                // the response
                s.request(1);
            }
        }

        @Override
        public void onNext(HttpResponse<Event<B>> next) {
            synchronized (this) {
                requested = false;
            }
            Event<B> event = next.body();
            if (event != null) {
                push(event);
            }
            if (!response.isDone()) {
                if (!response.complete(new EventStreamResponse<>(next, this))) {
                    // cancelled meanwhile
                    close();
                }
            } else if (isWaiting()) {
                // a response without an event
                demand();
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
            }
            if (!response.completeExceptionally(t)) {
                fail(t);
            }
        }

        @Override
        public void onComplete() {
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
            }
            if (!response.completeExceptionally(new IllegalStateException("The exchange completed without a response"))) {
                end();
            }
        }
    }
}
