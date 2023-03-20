package io.micronaut.core.execution;

import io.micronaut.core.annotation.Nullable;

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
}
