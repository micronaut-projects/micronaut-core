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
package io.micronaut.http.server.multipart;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Optional;
import java.util.concurrent.CompletionException;

/**
 * Subscriber that requests a single element from a publisher and completes a
 * {@link DelayedExecutionFlow} with it. Further elements are cancelled, an empty publisher completes
 * the flow with {@code null}, and cancelling the flow cancels the upstream subscription. This
 * replaces a Reactor {@code Mono} plus {@code CompletableFuture} for single form field binding.
 *
 * @param <T> The element type
 * @since 5.3.0
 */
@Internal
public final class FirstElementFlow<T> implements Subscriber<T> {
    private final DelayedExecutionFlow<T> flow = DelayedExecutionFlow.create();
    // volatile only publishes the subscription to a cancel from another thread; cancelling a
    // Reactive Streams subscription is thread-safe by specification
    @SuppressWarnings("java:S3077")
    private volatile @Nullable Subscription subscription;
    private boolean done;

    private FirstElementFlow() {
        flow.onCancel(() -> {
            Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
        });
    }

    /**
     * Subscribe to the given publisher and return a flow that completes with its first element.
     *
     * @param publisher The publisher
     * @param <T>       The element type
     * @return The flow, completing with the first element, {@code null} if the publisher is empty,
     * or the publisher error
     */
    public static <T> ExecutionFlow<T> first(Publisher<T> publisher) {
        FirstElementFlow<T> subscriber = new FirstElementFlow<>();
        publisher.subscribe(subscriber);
        return subscriber.flow;
    }

    /**
     * Observe the outcome of a single-consumer flow. The returned holder exposes the result for
     * polling from a binding result, and a separate flow for the route to wait on. Cancelling that
     * flow cancels the observed flow.
     *
     * @param flow The flow to observe. It must not be consumed elsewhere
     * @param <T>  The element type
     * @return The holder
     */
    public static <T> Settled<T> settle(ExecutionFlow<T> flow) {
        Settled<T> settled = new Settled<>();
        settled.flow.onCancel(flow::cancel);
        flow.onComplete((value, error) -> {
            settled.value = value;
            settled.error = error;
            settled.done = true;
            settled.flow.complete(value, error);
        });
        return settled;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        if (flow.isCancelled()) {
            s.cancel();
        } else {
            s.request(1);
        }
    }

    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }
        done = true;
        Subscription s = subscription;
        if (s != null) {
            s.cancel();
        }
        flow.complete(t);
    }

    @Override
    public void onError(Throwable t) {
        if (done) {
            return;
        }
        done = true;
        flow.completeExceptionally(t);
    }

    @Override
    public void onComplete() {
        if (done) {
            return;
        }
        done = true;
        flow.complete(null);
    }

    /**
     * Outcome of a flow observed via {@link #settle(ExecutionFlow)}.
     *
     * @param <T> The element type
     */
    public static final class Settled<T> {
        private final DelayedExecutionFlow<T> flow = DelayedExecutionFlow.create();
        private @Nullable T value;
        private @Nullable Throwable error;
        private volatile boolean done;

        private Settled() {
        }

        /**
         * @return A flow completing with the same outcome, for the route to wait on
         */
        public ExecutionFlow<T> flow() {
            return flow;
        }

        /**
         * @return {@code true} if the observed flow has completed
         */
        public boolean isDone() {
            return done;
        }

        /**
         * Get the value, mirroring {@link java.util.concurrent.CompletableFuture#getNow}: empty
         * while pending or when the flow completed without a value, and a
         * {@link CompletionException} when it failed.
         *
         * @return The value, if present
         */
        public Optional<T> valueNow() {
            if (!done) {
                return Optional.empty();
            }
            Throwable e = error;
            if (e != null) {
                throw e instanceof CompletionException ce ? ce : new CompletionException(e);
            }
            return Optional.ofNullable(value);
        }
    }
}
