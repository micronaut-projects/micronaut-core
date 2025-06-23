/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.CompletionException;

/**
 * {@link ExecutionFlow} that can be completed similar to a
 * {@link java.util.concurrent.CompletableFuture}.
 *
 * @param <T> The type of this flow
 */
public sealed interface DelayedExecutionFlow<T> extends ExecutionFlow<T> permits DelayedExecutionFlowImpl {
    static <T> DelayedExecutionFlow<T> create() {
        return new DelayedExecutionFlowImpl<>();
    }

    /**
     * Complete this flow normally.
     *
     * @param result The result value
     */
    void complete(@Nullable T result);

    /**
     * Complete this flow with an exception.
     *
     * @param exc The exception
     */
    void completeExceptionally(Throwable exc);

    /**
     * Complete this flow normally.
     *
     * @param result The result value
     * @return {@code false} if this flow has already been completed
     */
    boolean tryComplete(@Nullable T result);

    /**
     * Complete this flow with an exception.
     *
     * @param exc The exception
     * @return {@code false} if this flow has already been completed
     */
    boolean tryCompleteExceptionally(Throwable exc);

    /**
     * Check for cancellation.
     *
     * @return {@code true} iff this flow or any downstream flow has been cancelled
     * @since 4.8.0
     */
    boolean isCancelled();

    /**
     * Add a listener that is called if this flow or any downstream flow is cancelled.
     *
     * @param hook The hook to call on cancellation
     * @since 4.8.0
     */
    void onCancel(@NonNull Runnable hook);

    /**
     * Complete this flow from the given flow.
     *
     * @param flow The input flow
     * @since 4.7.0
     */
    void completeFrom(@NonNull ExecutionFlow<T> flow);

    /**
     * Complete the flow with value / exception.
     *
     * @param value     The value
     * @param throwable The exception
     * @since 4.7.0
     */
    default void complete(T value, Throwable throwable) {
        if (throwable != null) {
            if (throwable instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }
            completeExceptionally(throwable);
        } else {
            complete(value);
        }
    }
}
