/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.retry.intercept;

import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared retry execution engine for synchronous, reactive, and asynchronous flows.
 *
 * @author graemerocher
 * @since 4.9.0
 */
public final class DefaultRetryRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryRunner.class);

    private final ScheduledExecutorService executorService;

    /**
     * Creates a shared retry runner.
     *
     * @param executorService The scheduler used for delayed retries
     * @param retryEventEmitter The emitter used for retry notifications
     */
    public DefaultRetryRunner(ScheduledExecutorService executorService, RetryEventEmitter retryEventEmitter) {
        this.executorService = executorService;
    }

    /**
     * Executes synchronous work with retry.
     *
     * @param supplier The supplier to invoke for each attempt
     * @param retryState The retry state
     * @param logContext The log context
     * @param retryEventEmitter The retry event emitter
     * @param <T> The result type
     * @return The computed result
     */
    public <T> T executeSync(Supplier<T> supplier,
                             MutableRetryState retryState,
                             String logContext,
                             RetryEventEmitter retryEventEmitter) {
        while (true) {
            try {
                T value = supplier.get();
                retryState.close(null);
                return value;
            } catch (Throwable exception) {
                if (!isCaptured(retryState, exception)) {
                    throw exception;
                }
                if (!retryState.canRetry(exception)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Cannot retry anymore. Rethrowing original exception for {}", logContext);
                    }
                    retryState.close(exception);
                    throw exception;
                }
                long delayMillis = retryState.nextDelay();
                retryEventEmitter.onRetry(retryState, exception);
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Retrying execution for [{}] after delay of {}ms for exception: {}", logContext, delayMillis, exception.getMessage());
                    }
                    sleep(delayMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }

    /**
     * Executes completion stage work with retry.
     *
     * @param supplier The supplier that creates a new completion stage for each attempt
     * @param retryState The retry state
     * @param logContext The log context
     * @param retryEventEmitter The retry event emitter
     * @param <T> The result type
     * @return A completion stage representing the retried execution
     */
    public <T> CompletionStage<T> executeCompletionStage(Supplier<? extends CompletionStage<T>> supplier,
                                                         MutableRetryState retryState,
                                                         String logContext,
                                                         RetryEventEmitter retryEventEmitter) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            CompletionStage<T> initialStage = Objects.requireNonNull(supplier.get(), "supplier returned null completion stage");
            initialStage.whenComplete(retryCompletionStage(future, supplier, retryState, logContext, retryEventEmitter));
        } catch (Throwable exception) {
            if (!isCaptured(retryState, exception)) {
                future.completeExceptionally(exception);
                return future;
            }
            if (!retryState.canRetry(exception)) {
                retryState.close(exception);
                future.completeExceptionally(exception);
                return future;
            }
            long delayMillis = retryState.nextDelay();
            retryEventEmitter.onRetry(retryState, exception);
            var unused = executorService.schedule(() ->
                executeScheduledCompletionStage(future, supplier, retryState, logContext, retryEventEmitter), delayMillis, TimeUnit.MILLISECONDS);
        }
        return future;
    }

    /**
     * Executes publisher work with retry.
     *
     * @param supplier The supplier that creates a new publisher for each attempt
     * @param retryState The retry state
     * @param logContext The log context
     * @param retryEventEmitter The retry event emitter
     * @param <T> The emitted type
     * @return A publisher representing the retried execution
     */
    public <T> Publisher<T> executePublisher(Supplier<? extends Publisher<T>> supplier,
                                             MutableRetryState retryState,
                                             String logContext,
                                             RetryEventEmitter retryEventEmitter) {
        return Flux.defer(() -> {
            try {
                return Flux.from(Objects.requireNonNull(supplier.get(), "supplier returned null publisher"));
            } catch (Throwable exception) {
                return Flux.error(exception);
            }
        }).onErrorResume(retryPublisher(supplier, retryState, logContext, retryEventEmitter))
            .doOnComplete(() -> retryState.close(null));
    }

    private void sleep(long delayMillis) throws InterruptedException {
        Thread.sleep(delayMillis);
    }

    private <T> void executeScheduledCompletionStage(CompletableFuture<T> future,
                                                     Supplier<? extends CompletionStage<T>> supplier,
                                                     MutableRetryState retryState,
                                                     String logContext,
                                                     RetryEventEmitter retryEventEmitter) {
        try {
            CompletionStage<T> nextStage = Objects.requireNonNull(supplier.get(), "supplier returned null completion stage");
            nextStage.whenComplete(retryCompletionStage(future, supplier, retryState, logContext, retryEventEmitter));
        } catch (Throwable exception) {
            retryCompletionStage(future, supplier, retryState, logContext, retryEventEmitter).accept(null, exception);
        }
    }

    private <T> BiConsumer<T, ? super Throwable> retryCompletionStage(CompletableFuture<T> future,
                                                                      Supplier<? extends CompletionStage<T>> supplier,
                                                                      MutableRetryState retryState,
                                                                      String logContext,
                                                                      RetryEventEmitter retryEventEmitter) {
        return (value, exception) -> {
            if (exception == null) {
                retryState.close(null);
                future.complete(value);
                return;
            }
            if (!retryState.canRetry(exception)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot retry anymore. Rethrowing original exception for {}", logContext);
                }
                retryState.close(exception);
                future.completeExceptionally(exception);
                return;
            }
            long delayMillis = retryState.nextDelay();
            retryEventEmitter.onRetry(retryState, exception);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrying execution for [{}] after delay of {}ms for exception: {}", logContext, delayMillis, exception.getMessage(), exception);
            }
            var unused = executorService.schedule(() -> executeScheduledCompletionStage(future, supplier, retryState, logContext, retryEventEmitter), delayMillis, TimeUnit.MILLISECONDS);
        };
    }

    private <T> Function<? super Throwable, ? extends Publisher<? extends T>> retryPublisher(Supplier<? extends Publisher<T>> supplier,
                                                                                               MutableRetryState retryState,
                                                                                               String logContext,
                                                                                               RetryEventEmitter retryEventEmitter) {
        return exception -> {
            if (!retryState.canRetry(exception)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Cannot retry anymore. Rethrowing original exception for {}", logContext);
                }
                retryState.close(exception);
                return Flux.error(exception);
            }
            long delayMillis = retryState.nextDelay();
            retryEventEmitter.onRetry(retryState, exception);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Retrying execution for [{}] after delay of {}ms for exception: {}", logContext, delayMillis, exception.getMessage(), exception);
            }
            return Flux.defer(() -> executePublisher(supplier, retryState, logContext, retryEventEmitter))
                .delaySubscription(Duration.of(delayMillis, ChronoUnit.MILLIS));
        };
    }

    private static boolean isCaptured(MutableRetryState retryState, Throwable exception) {
        @Nullable Class<? extends Throwable> capturedException = retryState.getCapturedException();
        return capturedException == null || capturedException.isAssignableFrom(exception.getClass());
    }
}
