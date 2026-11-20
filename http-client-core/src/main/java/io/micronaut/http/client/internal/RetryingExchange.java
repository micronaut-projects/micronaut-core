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
package io.micronaut.http.client.internal;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.discovery.exceptions.NoAvailableServiceException;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.LoadBalancer;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.exceptions.ReadTimeoutException;
import io.micronaut.http.client.exceptions.UnprocessedRequestException;
import io.micronaut.http.client.loadbalance.LoadBalancerSelection;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * One exchange of a client with {@link ExchangeRetry retry}: it sends attempts until one
 * succeeds or the retry gives up, waiting between them. An attempt is sent by an
 * {@link AttemptSender}, which selects an instance that was not tried yet, see
 * {@link Attempt#excluded()}, and asks {@link Attempt#retryStatus(HttpResponse)} once the
 * response headers arrived whether the status is retried.
 *
 * <p>Cancelling the flow cancels the attempt in flight, or the wait for the next one. The timeout
 * of an attempt reports {@link LoadBalancer.Outcome#TIMEOUT} for its instance before it cancels
 * it, so that the outlier detection sees it.
 *
 * @param <T> The type of the response
 * @since 5.3.0
 */
@Internal
public final class RetryingExchange<T> {

    private final ExchangeRetry retry;
    private final HttpRequest<?> request;
    private final PropagatedContext propagatedContext;
    private final ScheduledExecutorService scheduler;
    private final IntFunction<? extends HttpRequest<?>> requestForAttempt;
    private final AttemptSender<T> sender;
    private final Consumer<? super T> discard;
    private final Supplier<? extends Throwable> timeoutFailure;
    private final Logger log;
    private final boolean retries;
    private final DelayedExecutionFlow<T> result = DelayedExecutionFlow.create();
    private final Set<URI> tried = ConcurrentHashMap.newKeySet();
    private final AtomicLong overallDelayNanos = new AtomicLong();
    private volatile @Nullable ExecutionFlow<T> current;
    private volatile @Nullable ScheduledFuture<?> pending;
    private volatile @Nullable HttpRequest<?> pendingRequest;

    /**
     * @param retry             The retry
     * @param request           The request of the exchange, as the caller gave it
     * @param propagatedContext The context the attempts are sent in
     * @param scheduler         The scheduler of the delays and timeouts
     * @param requestForAttempt The request of an attempt, by its number from {@code 1}: each
     *                          attempt has its own, so that the filters of an attempt do not see
     *                          the changes of the filters of the previous one
     * @param sender            Sends an attempt
     * @param discard           Releases a response that arrived after its attempt timed out, or
     *                          after the exchange was cancelled
     * @param timeoutFailure    The failure of an attempt that timed out
     * @param log               The log of the client
     * @param retries           Whether the exchange is retried at all: without, its single
     *                          attempt still has the timeout of an attempt, e.g. for a request
     *                          whose body cannot be sent again
     */
    public RetryingExchange(ExchangeRetry retry,
                            HttpRequest<?> request,
                            PropagatedContext propagatedContext,
                            ScheduledExecutorService scheduler,
                            IntFunction<? extends HttpRequest<?>> requestForAttempt,
                            AttemptSender<T> sender,
                            Consumer<? super T> discard,
                            Supplier<? extends Throwable> timeoutFailure,
                            Logger log,
                            boolean retries) {
        this.retry = retry;
        this.request = request;
        this.propagatedContext = propagatedContext;
        this.scheduler = scheduler;
        this.requestForAttempt = requestForAttempt;
        this.sender = sender;
        this.discard = discard;
        this.timeoutFailure = timeoutFailure;
        this.log = log;
        this.retries = retries;
    }

    /**
     * Send the first attempt.
     *
     * @return The flow of the response of the attempt that succeeded, or the failure of the last
     * one
     */
    public ExecutionFlow<T> start() {
        result.onCancel(this::cancel);
        HttpRequest<?> first;
        try {
            first = requestForAttempt.apply(1);
        } catch (Throwable e) {
            result.completeExceptionally(e);
            return result;
        }
        attempt(1, first);
        return result;
    }

    private void cancel() {
        ScheduledFuture<?> p = pending;
        if (p != null && p.cancel(false)) {
            release(pendingRequest);
        }
        ExecutionFlow<T> c = current;
        if (c != null) {
            c.cancel();
        }
    }

    private static void release(@Nullable HttpRequest<?> request) {
        if (request instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                // the request of an attempt that is not sent
            }
        }
    }

    private void attempt(int number, HttpRequest<?> attemptRequest) {
        if (result.isCancelled()) {
            release(attemptRequest);
            return;
        }
        Attempt attempt = new Attempt(number, attemptRequest);
        ExecutionFlow<T> flow;
        try {
            flow = sender.send(attempt);
        } catch (Throwable e) {
            flow = ExecutionFlow.error(e);
        }
        current = flow;
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<@Nullable ScheduledFuture<?>> timeoutTask = new AtomicReference<>();
        // the completion is registered before the timeout is armed: a timeout that elapses at
        // once cancels a flow that is already listened to
        flow.onComplete((response, failure) -> {
            if (!done.compareAndSet(false, true)) {
                // the attempt timed out
                discard.accept(response);
                return;
            }
            ScheduledFuture<?> scheduledTimeout = timeoutTask.get();
            if (scheduledTimeout != null) {
                scheduledTimeout.cancel(false);
            }
            if (failure != null) {
                onFailure(attempt, failure);
            } else if (result.isCancelled()) {
                discard.accept(response);
            } else {
                retry.onSuccess();
                result.complete(response);
            }
        });
        Duration attemptTimeout = retry.attemptTimeout();
        if (attemptTimeout != null && !done.get()) {
            ExecutionFlow<T> timed = flow;
            timeoutTask.set(scheduler.schedule(() -> {
                if (done.compareAndSet(false, true)) {
                    // reported first: the cancel releases the selection without an outcome
                    attempt.report(LoadBalancer.Outcome.TIMEOUT);
                    timed.cancel();
                    onFailure(attempt, timeoutFailure.get());
                }
            }, attemptTimeout.toNanos(), TimeUnit.NANOSECONDS));
            if (done.get()) {
                // completed while the timeout was armed
                Objects.requireNonNull(timeoutTask.get()).cancel(false);
            }
        }
    }

    private void onFailure(Attempt attempt, Throwable failure) {
        if (result.isCancelled()) {
            return;
        }
        Duration delay;
        if (failure instanceof RetryStatusException status) {
            delay = status.delay;
        } else if (!retries || (failure instanceof HttpClientResponseException && attempt.statusDecided)) {
            // the status of the response was decided when its headers arrived
            delay = null;
        } else {
            try {
                delay = retry.retryDelay(request, attempt.number, classify(failure), Duration.ofNanos(overallDelayNanos.get()));
            } catch (Throwable e) {
                failure.addSuppressed(e);
                delay = null;
            }
        }
        if (delay == null) {
            result.completeExceptionally(failure);
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("Retrying {} {} in {} after attempt {}: {}", request.getMethodName(), request.getUri(), delay, attempt.number, failure.getMessage());
        }
        overallDelayNanos.addAndGet(delay.toNanos());
        int next = attempt.number + 1;
        // the request is prepared on this thread, e.g. its body is split, rather than on the
        // thread of the scheduler, which only waits
        HttpRequest<?> nextRequest;
        try {
            nextRequest = requestForAttempt.apply(next);
        } catch (Throwable e) {
            result.completeExceptionally(e);
            return;
        }
        Runnable sendNext = () -> propagatedContext.propagate(() -> attempt(next, nextRequest));
        if (delay.isZero() || delay.isNegative()) {
            sendNext.run();
        } else {
            pendingRequest = nextRequest;
            ScheduledFuture<?> scheduled = scheduler.schedule(sendNext, delay.toNanos(), TimeUnit.NANOSECONDS);
            pending = scheduled;
            if (result.isCancelled() && scheduled.cancel(false)) {
                release(nextRequest);
            }
        }
    }

    private static ExchangeRetry.Outcome classify(Throwable failure) {
        if (UnprocessedRequestException.isUnprocessed(failure) || failure instanceof NoAvailableServiceException) {
            return ExchangeRetry.Outcome.failure(ExchangeRetry.Kind.UNPROCESSED, failure);
        }
        if (failure instanceof ReadTimeoutException) {
            return ExchangeRetry.Outcome.failure(ExchangeRetry.Kind.TIMEOUT, failure);
        }
        if (failure instanceof HttpClientResponseException response) {
            return ExchangeRetry.Outcome.response(response.code(), retryAfter(response.getResponse().getHeaders()));
        }
        return ExchangeRetry.Outcome.failure(ExchangeRetry.Kind.FAILURE, failure);
    }

    /**
     * @param headers The headers of a response
     * @return The delay its {@code Retry-After} header asks for, in seconds or as a date, or
     * {@code null}
     */
    static @Nullable Duration retryAfter(HttpHeaders headers) {
        String value = headers.get(HttpHeaders.RETRY_AFTER);
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.strip();
        try {
            long seconds = Long.parseLong(value);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            // a date
        }
        try {
            Duration until = Duration.between(ZonedDateTime.now(), ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME));
            return until.isNegative() ? Duration.ZERO : until;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Sends an attempt.
     *
     * @param <T> The type of the response
     */
    @FunctionalInterface
    public interface AttemptSender<T> {
        /**
         * @param attempt The attempt
         * @return The flow of its response
         */
        ExecutionFlow<T> send(RetryingExchange<T>.Attempt attempt);
    }

    /**
     * One attempt of the exchange.
     */
    public final class Attempt {
        private final int number;
        private final HttpRequest<?> attemptRequest;
        private volatile @Nullable LoadBalancerSelection selection;
        private volatile boolean statusDecided;

        private Attempt(int number, HttpRequest<?> attemptRequest) {
            this.number = number;
            this.attemptRequest = attemptRequest;
        }

        /**
         * @return The number of the attempt, from {@code 1}
         */
        public int number() {
            return number;
        }

        /**
         * @return The request of the attempt
         */
        public HttpRequest<?> request() {
            return attemptRequest;
        }

        /**
         * @return The URIs of the instances the previous attempts were sent to
         */
        public Set<URI> excluded() {
            return Set.copyOf(tried);
        }

        /**
         * Record the instance the load balancer selected for this attempt.
         *
         * @param selection The selection, or {@code null} if the request was not load balanced
         */
        public void selected(@Nullable LoadBalancerSelection selection) {
            this.selection = selection;
            if (selection != null) {
                tried.add(selection.instance().getURI());
            }
        }

        private void report(LoadBalancer.Outcome outcome) {
            LoadBalancerSelection s = selection;
            if (s != null) {
                s.report(outcome);
            }
        }

        /**
         * Whether the status of the response of this attempt is retried. If it is, the outcome
         * is reported to the load balancer, and the caller closes the response and fails the
         * attempt with the returned exception.
         *
         * @param response The response, whose body is not read yet
         * @return The failure of the attempt, or {@code null} if the response is passed on
         */
        public @Nullable HttpClientException retryStatus(HttpResponse<?> response) {
            if (!retries) {
                return null;
            }
            statusDecided = true;
            int status = response.code();
            Duration delay = retry.retryDelay(request, number, ExchangeRetry.Outcome.response(status, retryAfter(response.getHeaders())), Duration.ofNanos(overallDelayNanos.get()));
            if (delay == null) {
                return null;
            }
            report(status >= 500 ? LoadBalancer.Outcome.SERVER_ERROR : LoadBalancer.Outcome.SUCCESS);
            return new RetryStatusException(status, delay);
        }
    }

    /**
     * The failure of an attempt whose status is retried. It never reaches the caller.
     */
    private static final class RetryStatusException extends HttpClientException {
        private final transient Duration delay;

        RetryStatusException(int status, Duration delay) {
            super("Response status " + status + " is retried");
            this.delay = delay;
        }
    }
}
