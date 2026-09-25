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
package io.micronaut.http.reactive.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.execution.ImperativeExecutionFlow;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A flow that is created on first use, depending on the subscriber. Completed as an
 * {@link ExecutionFlow} ({@link #onComplete}, {@link #tryComplete} etc.) it is created without any
 * reactive code. Converted to a publisher ({@link ReactiveExecutionFlow#fromFlow},
 * {@link ReactiveExecutionFlow#toPublisher}) it is created on subscription as a reactive flow, so
 * it can keep the Reactor context of the subscriber. The operators compose without creating the
 * flow, so they keep the subscriber detection.
 *
 * @param <T> The value type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public abstract class SubscriberAwareExecutionFlow<T> implements ExecutionFlow<T> {

    private @Nullable ExecutionFlow<T> flow;

    /**
     * Create the flow.
     *
     * @param reactive Whether the flow is subscribed by a reactive subscriber
     * @return The flow
     */
    protected abstract ExecutionFlow<T> create(boolean reactive);

    /**
     * @return The flow created for a reactive subscriber, unless it was already created
     */
    final ExecutionFlow<T> reactiveFlow() {
        return resolve(true);
    }

    private ExecutionFlow<T> flow() {
        return resolve(false);
    }

    private ExecutionFlow<T> resolve(boolean reactive) {
        ExecutionFlow<T> f = flow;
        if (f == null) {
            f = create(reactive);
            flow = f;
        }
        return f;
    }

    /**
     * Compose an operator without creating the flow, so the subscriber is still unknown.
     *
     * @param operator The operator
     * @param <R>      The result type
     * @return The composed flow
     */
    private <R> ExecutionFlow<R> compose(Function<ExecutionFlow<T>, ExecutionFlow<R>> operator) {
        SubscriberAwareExecutionFlow<T> parent = this;
        return new SubscriberAwareExecutionFlow<>() {
            @Override
            protected ExecutionFlow<R> create(boolean reactive) {
                return operator.apply(parent.resolve(reactive));
            }
        };
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super T, ? extends R> transformer) {
        return compose(f -> f.map(transformer));
    }

    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super T, ? extends ExecutionFlow<? extends R>> transformer) {
        return compose(f -> f.flatMap(transformer));
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        return compose(f -> f.then(supplier));
    }

    @Override
    public ExecutionFlow<T> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<? extends T>> fallback) {
        return compose(f -> f.onErrorResume(fallback));
    }

    @Override
    public ExecutionFlow<T> putInContext(String key, Object value) {
        return compose(f -> f.putInContext(key, value));
    }

    @Override
    public ExecutionFlow<T> putInContextIfAbsent(String key, Object value) {
        return compose(f -> f.putInContextIfAbsent(key, value));
    }

    @Override
    public void onComplete(BiConsumer<? super T, @Nullable Throwable> fn) {
        flow().onComplete(fn);
    }

    @Override
    public void completeTo(CompletableFuture<T> completableFuture) {
        flow().completeTo(completableFuture);
    }

    @Override
    public @Nullable ImperativeExecutionFlow<T> tryComplete() {
        return flow().tryComplete();
    }

    @Override
    public CompletableFuture<T> toCompletableFuture() {
        return flow().toCompletableFuture();
    }

    @Override
    public void cancel() {
        ExecutionFlow<T> f = flow;
        if (f != null) {
            f.cancel();
        }
    }

    @Override
    public void cancel(Consumer<T> discard) {
        ExecutionFlow<T> f = flow;
        if (f != null) {
            f.cancel(discard);
        }
    }
}
