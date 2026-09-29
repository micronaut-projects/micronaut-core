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

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Shared retry execution engine for synchronous, reactive, and asynchronous flows.
 *
 * @author graemerocher
 * @since 5.0.0
 */
@Internal
public final class DefaultRetryRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultRetryRunner.class);
    private static final String CANNOT_RETRY_MESSAGE = "Cannot retry anymore. Rethrowing original exception for {}";
    private static final String RETRYING_MESSAGE = "Retrying execution for [{}] after delay of {}ms for exception: {}";

    private final ScheduledExecutorService executorService;
    private final RetrySleeper retrySleeper;

    /**
     * Creates a shared retry runner.
     *
     * @param executorService The scheduler used for delayed retries
     * @param retrySleeper The strategy used to sleep between retries
     */
    public DefaultRetryRunner(ScheduledExecutorService executorService, RetrySleeper retrySleeper) {
        this.executorService = executorService;
        this.retrySleeper = retrySleeper;
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
                    retryState.onUncaptured(exception);
                    throw exception;
                }
                if (!retryState.canRetry(exception)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(CANNOT_RETRY_MESSAGE, logContext);
                    }
                    retryState.close(exception);
                    throw exception;
                }
                long delayMillis = retryState.nextDelay();
                retryEventEmitter.onRetry(retryState, exception);
                try {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(RETRYING_MESSAGE, logContext, delayMillis, exception.getMessage());
                    }
                    retrySleeper.sleep(delayMillis);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    retryState.release();
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
        CompletionStageExecution<T> execution = new CompletionStageExecution<>(supplier, retryState, logContext, retryEventEmitter);
        execution.attempt();
        return execution.future;
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
            AtomicBoolean emitted = new AtomicBoolean();
            return attemptPublisher(supplier, retryState, logContext, retryEventEmitter)
                .doOnNext(value -> emitted.set(true))
                .doFinally(signalType -> {
                    // once, whatever the retries: ON_ERROR is reported by retryPublisher, and a
                    // cancellation is a success only once the publisher produced a value
                    if (signalType == SignalType.ON_COMPLETE || signalType == SignalType.CANCEL && emitted.get()) {
                        retryState.close(null);
                    } else if (signalType == SignalType.CANCEL) {
                        retryState.onCancel();
                    }
                });
        });
    }

    private <T> Flux<T> attemptPublisher(Supplier<? extends Publisher<T>> supplier,
                                         MutableRetryState retryState,
                                         String logContext,
                                         RetryEventEmitter retryEventEmitter) {
        return Flux.<T>defer(() -> {
            try {
                return Flux.from(Objects.requireNonNull(supplier.get(), "supplier returned null publisher"));
            } catch (Exception exception) {
                return Flux.error(exception);
            }
        }).onErrorResume(retryPublisher(supplier, retryState, logContext, retryEventEmitter));
    }

    private <T> Function<? super Throwable, ? extends Publisher<? extends T>> retryPublisher(Supplier<? extends Publisher<T>> supplier,
                                                                                               MutableRetryState retryState,
                                                                                               String logContext,
                                                                                               RetryEventEmitter retryEventEmitter) {
        return exception -> {
            if (!isCaptured(retryState, exception)) {
                retryState.onUncaptured(exception);
                return Flux.error(exception);
            }
            if (!retryState.canRetry(exception)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(CANNOT_RETRY_MESSAGE, logContext);
                }
                retryState.close(exception);
                return Flux.error(exception);
            }
            long delayMillis = retryState.nextDelay();
            retryEventEmitter.onRetry(retryState, exception);
            if (LOG.isDebugEnabled()) {
                LOG.debug(RETRYING_MESSAGE, logContext, delayMillis, exception.getMessage(), exception);
            }
            return Flux.defer(() -> attemptPublisher(supplier, retryState, logContext, retryEventEmitter))
                .delaySubscription(Duration.of(delayMillis, ChronoUnit.MILLIS));
        };
    }

    private static boolean isCaptured(MutableRetryState retryState, Throwable exception) {
        @Nullable Class<? extends Throwable> capturedException = retryState.getCapturedException();
        return capturedException == null || capturedException.isAssignableFrom(exception.getClass());
    }

    private static Throwable unwrapCompletionException(Throwable exception) {
        if (exception instanceof CompletionException || exception instanceof ExecutionException) {
            Throwable cause = exception.getCause();
            if (cause != null) {
                return cause;
            }
        }
        return exception;
    }

    /**
     * An execution of completion stage work with retry: the attempts, and the retries scheduled
     * between them, until the returned future completes. Cancelling the returned future cancels
     * the attempt in progress, if it is a {@link CompletableFuture}, or the retry scheduled next,
     * and ends the retry state with {@link MutableRetryState#onCancel()}, so that a state that
     * holds a permit returns it. The retry state ends exactly once, whether by an outcome or by the
     * cancellation.
     *
     * @param <T> The result type
     */
    private final class CompletionStageExecution<T> {

        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicBoolean ended = new AtomicBoolean();
        private final Supplier<? extends CompletionStage<T>> supplier;
        private final MutableRetryState retryState;
        private final String logContext;
        private final RetryEventEmitter retryEventEmitter;
        private final AtomicReference<@Nullable Future<?>> pending = new AtomicReference<>();

        CompletionStageExecution(Supplier<? extends CompletionStage<T>> supplier,
                                 MutableRetryState retryState,
                                 String logContext,
                                 RetryEventEmitter retryEventEmitter) {
            this.supplier = supplier;
            this.retryState = retryState;
            this.logContext = logContext;
            this.retryEventEmitter = retryEventEmitter;
            future.whenComplete((value, exception) -> {
                if (future.isCancelled()) {
                    onCancel();
                }
            });
        }

        void attempt() {
            if (future.isDone()) {
                return;
            }
            CompletionStage<T> stage;
            try {
                stage = Objects.requireNonNull(supplier.get(), "supplier returned null completion stage");
            } catch (Throwable exception) {
                onOutcome(null, exception);
                return;
            }
            if (stage instanceof CompletableFuture<?> completableFuture) {
                setPending(completableFuture);
            }
            stage.whenComplete(this::onOutcome);
        }

        private void onOutcome(@Nullable T value, @Nullable Throwable exception) {
            if (future.isCancelled()) {
                return;
            }
            if (exception == null) {
                if (end()) {
                    retryState.close(null);
                    future.complete(value);
                }
                return;
            }
            Throwable cause = unwrapCompletionException(exception);
            if (!isCaptured(retryState, cause)) {
                if (end()) {
                    retryState.onUncaptured(cause);
                    future.completeExceptionally(cause);
                }
                return;
            }
            if (!retryState.canRetry(cause)) {
                if (end()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(CANNOT_RETRY_MESSAGE, logContext);
                    }
                    retryState.close(cause);
                    future.completeExceptionally(cause);
                }
                return;
            }
            long delayMillis = retryState.nextDelay();
            retryEventEmitter.onRetry(retryState, cause);
            if (LOG.isDebugEnabled()) {
                LOG.debug(RETRYING_MESSAGE, logContext, delayMillis, cause.getMessage(), cause);
            }
            setPending(executorService.schedule(this::attempt, delayMillis, TimeUnit.MILLISECONDS));
        }

        private void setPending(Future<?> next) {
            pending.set(next);
            // a cancellation that came before the attempt or the retry was pending
            if (future.isCancelled()) {
                next.cancel(false);
            }
        }

        private void onCancel() {
            if (end()) {
                retryState.onCancel();
            }
            Future<?> current = pending.get();
            if (current != null) {
                current.cancel(false);
            }
        }

        private boolean end() {
            return ended.compareAndSet(false, true);
        }
    }
}
