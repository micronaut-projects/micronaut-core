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
package io.micronaut.websocket;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link AsyncWebSocketClient} of a {@link WebSocketClient} that only has the reactive
 * {@link WebSocketClient#connect} methods: the first endpoint a publisher emits completes the
 * stage.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class AsyncOverReactiveWebSocketClient implements AsyncWebSocketClient {

    private final WebSocketClient client;

    /**
     * @param client The reactive client
     */
    AsyncOverReactiveWebSocketClient(WebSocketClient client) {
        this.client = client;
    }

    @Override
    public <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, MutableHttpRequest<?> request) {
        return first(client.connect(clientEndpointType, request));
    }

    @Override
    public <T extends AutoCloseable> CompletionStage<T> connect(Class<T> clientEndpointType, Map<String, Object> parameters) {
        return first(client.connect(clientEndpointType, parameters));
    }

    @Override
    public void close() {
        client.close();
    }

    private static <T extends AutoCloseable> CompletionStage<T> first(Publisher<T> publisher) {
        FirstEndpoint<T> first = new FirstEndpoint<>();
        publisher.subscribe(first);
        return first;
    }

    /**
     * Completes with the first endpoint: cancelling it, or completing it otherwise, e.g. with
     * {@link CompletableFuture#orTimeout}, cancels the subscription, and an endpoint that arrives
     * after that is closed.
     *
     * @param <T> The endpoint type
     */
    private static final class FirstEndpoint<T extends AutoCloseable> extends CompletableFuture<T> implements Subscriber<T> {
        private @Nullable Subscription subscription;
        private boolean done;

        FirstEndpoint() {
            whenComplete((ignored, error) -> cancelUnlessDone());
        }

        @Override
        public void onSubscribe(Subscription s) {
            boolean completed;
            synchronized (this) {
                subscription = s;
                completed = isDone();
            }
            if (completed) {
                s.cancel();
            } else {
                s.request(1);
            }
        }

        @Override
        public void onNext(T endpoint) {
            Subscription s;
            synchronized (this) {
                if (done) {
                    s = null;
                } else {
                    done = true;
                    s = subscription;
                }
            }
            if (s != null) {
                // one endpoint per connect
                s.cancel();
            }
            if (!complete(endpoint)) {
                closeQuietly(endpoint);
            }
        }

        @Override
        public void onError(Throwable t) {
            synchronized (this) {
                done = true;
            }
            completeExceptionally(t);
        }

        @Override
        public void onComplete() {
            boolean empty;
            synchronized (this) {
                empty = !done;
                done = true;
            }
            if (empty) {
                completeExceptionally(new NoSuchElementException("The connect completed without a client endpoint"));
            }
        }

        /**
         * The future completed before the publisher signaled: nobody waits for the endpoint.
         */
        private void cancelUnlessDone() {
            Subscription s;
            synchronized (this) {
                if (done) {
                    return;
                }
                done = true;
                s = subscription;
            }
            if (s != null) {
                s.cancel();
            }
        }

        private static void closeQuietly(AutoCloseable endpoint) {
            try {
                endpoint.close();
            } catch (Exception e) {
                // the connect was cancelled, nobody waits for this endpoint
            }
        }
    }
}
