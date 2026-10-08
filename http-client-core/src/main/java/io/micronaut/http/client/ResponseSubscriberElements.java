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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.stream.PulledBodyElements;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The elements of a reactive exchange that emits each piece of the body wrapped in the response,
 * such as {@code SseClient.exchangeEventStream} or {@code StreamingHttpClient.exchangeStream}:
 * the first emitted response completes the exchange, and each read requests the next one.
 *
 * @param <X> The body type of an emitted response
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ResponseSubscriberElements<X, T> extends PulledBodyElements<T> implements Subscriber<HttpResponse<X>> {

    private final CompletableFuture<HttpResponse<BodyElements<T>>> response = new CompletableFuture<>();
    private final Function<? super X, ? extends T> element;

    // guarded by this
    private @Nullable Subscription subscription;
    private boolean requested;
    private boolean done;

    private ResponseSubscriberElements(Function<? super X, ? extends T> element) {
        this.element = element;
        response.whenComplete((r, error) -> {
            if (error instanceof CancellationException) {
                // cancels the subscription
                close();
            }
        });
    }

    /**
     * Subscribe to the responses of a reactive exchange. Cancelling the stage before the first
     * response arrived cancels the subscription.
     *
     * @param responses The responses, each with a piece of the body, or without a body
     * @param element   The element of the body of a response
     * @param <X>       The body type of an emitted response
     * @param <T>       The type of an element
     * @return Completes with the first response, whose body is the elements
     */
    public static <X, T> CompletableFuture<HttpResponse<BodyElements<T>>> exchange(Publisher<? extends HttpResponse<X>> responses,
                                                                                    Function<? super X, ? extends T> element) {
        ResponseSubscriberElements<X, T> elements = new ResponseSubscriberElements<>(element);
        responses.subscribe(elements);
        return elements.response;
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
    public void onNext(HttpResponse<X> next) {
        synchronized (this) {
            requested = false;
        }
        X body = next.body();
        if (body != null) {
            T value;
            try {
                value = element.apply(body);
            } catch (Throwable e) {
                abort(e);
                return;
            }
            push(value);
        }
        if (!response.isDone()) {
            if (!response.complete(ElementsResponse.of(next, this))) {
                // cancelled meanwhile
                close();
            }
        } else if (isWaiting()) {
            // a response without a body
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

    private void abort(Throwable error) {
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
        if (!response.completeExceptionally(error)) {
            fail(error);
        }
    }
}
