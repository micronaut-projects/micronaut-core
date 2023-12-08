/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.core.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The execution flow class represents a data flow which state can be represented as a simple imperative flow or an async/reactive.
 * The state can be resolved or lazy - based on the implementation.
 * NOTE: The instance of the flow is not supposed to be used after a mapping operator is used.
 *
 * @param <T> The flow type
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public interface ExecutionFlow<T> {

    /**
     * Create a simple flow representing a value.
     *
     * @param value The value
     * @param <K>   The value type
     * @return a new flow
     */
    @NonNull
    static <K> ExecutionFlow<K> just(@Nullable K value) {
        return (ExecutionFlow<K>) new ImperativeExecutionFlowImpl(value, null);
    }

    /**
     * Create a simple flow representing an error.
     *
     * @param e   The exception
     * @param <K> The value type
     * @return a new flow
     */
    @NonNull
    static <K> ExecutionFlow<K> error(@NonNull Throwable e) {
        return (ExecutionFlow<K>) new ImperativeExecutionFlowImpl(null, e);
    }

    /**
     * Create a simple flow representing an empty state.
     *
     * @param <T>      The flow value type
     * @return a new flow
     */
    @NonNull
    static <T> ExecutionFlow<T> empty() {
        return (ExecutionFlow<T>) new ImperativeExecutionFlowImpl(null, null);
    }

    /**
     * Create a flow by invoking a supplier asynchronously.
     *
     * @param executor The executor
     * @param supplier The supplier
     * @param <T>      The flow value type
     * @return a new flow
     */
    @NonNull
    static <T> ExecutionFlow<T> async(@NonNull Executor executor, @NonNull Supplier<? extends ExecutionFlow<T>> supplier) {
        DelayedExecutionFlow<T> completableFuture = DelayedExecutionFlow.create();
        executor.execute(() -> supplier.get().onComplete((t, throwable) -> {
            if (throwable != null) {
                if (throwable instanceof CompletionException completionException) {
                    throwable = completionException.getCause();
                }
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(t);
            }
        }));
        return completableFuture;
    }

    /**
     * Map a not-empty value.
     *
     * @param transformer The value transformer
     * @param <R>         New value Type
     * @return a new flow
     */
    @NonNull
    <R> ExecutionFlow<R> map(@NonNull Function<? super T, ? extends R> transformer);

    /**
     * Map a not-empty value to a new flow.
     *
     * @param transformer The value transformer
     * @param <R>         New value Type
     * @return a new flow
     */
    @NonNull
    <R> ExecutionFlow<R> flatMap(@NonNull Function<? super T, ? extends ExecutionFlow<? extends R>> transformer);

    /**
     * Supply a new flow after the existing flow value is resolved.
     *
     * @param supplier The supplier
     * @param <R>      New value Type
     * @return a new flow
     */
    @NonNull
    <R> ExecutionFlow<R> then(@NonNull Supplier<? extends ExecutionFlow<? extends R>> supplier);

    /**
     * Supply a new flow if the existing flow is erroneous.
     *
     * @param fallback The fallback
     * @return a new flow
     */
    @NonNull
    ExecutionFlow<T> onErrorResume(@NonNull Function<? super Throwable, ? extends ExecutionFlow<? extends T>> fallback);

    /**
     * Store a contextual value.
     *
     * @param key   The key
     * @param value The value
     * @return a new flow
     */
    @NonNull
    ExecutionFlow<T> putInContext(@NonNull String key, @NonNull Object value);

    /**
     * Invokes a provided function when the flow is resolved, or immediately if it is already done.
     *
     * @param fn The function
     */
    void onComplete(@NonNull BiConsumer<? super T, Throwable> fn);

    /**
     * Create an {@link ImperativeExecutionFlow} from this execution flow, if possible. The flow
     * will have its result immediately available.
     *
     * @return The imperative flow, or {@code null} if this flow is not complete or does not
     * support this operation
     */
    @Nullable
    ImperativeExecutionFlow<T> tryComplete();

    /**
     * Alternative to {@link #tryComplete()} which will unwrap the flow's value.
     *
     * @return The imperative flow then returns its value, or {@code null} if this flow is not complete or does not
     * support this operation
     * @since 4.3
     */
    @Nullable
    default T tryCompleteValue() {
        ImperativeExecutionFlow<T> imperativeFlow = tryComplete();
        if (imperativeFlow != null) {
            return imperativeFlow.getValue();
        }
        return null;
    }

    /**
     * Alternative to {@link #tryComplete()} which will unwrap the flow's error.
     *
     * @return The imperative flow then returns its error, or {@code null} if this flow is not complete or does not
     * support this operation
     * @since 4.3
     */
    @Nullable
    default Throwable tryCompleteError() {
        ImperativeExecutionFlow<T> imperativeFlow = tryComplete();
        if (imperativeFlow != null) {
            return imperativeFlow.getError();
        }
        return null;
    }

    /**
     * Converts the existing flow into the completable future.
     *
     * @return a {@link CompletableFuture} that represents the state if this flow.
     */
    @NonNull
    default CompletableFuture<T> toCompletableFuture() {
        CompletableFuture<T> completableFuture = new CompletableFuture<>();
        onComplete((value, throwable) -> {
            if (throwable != null) {
                if (throwable instanceof CompletionException completionException) {
                    throwable = completionException.getCause();
                }
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(value);
            }
        });
        return completableFuture;
    }

}

