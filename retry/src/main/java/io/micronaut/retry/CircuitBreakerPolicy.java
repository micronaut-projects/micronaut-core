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
package io.micronaut.retry;

import io.micronaut.retry.annotation.RetryPredicate;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed circuit breaker policy for programmatic execution.
 *
 * @param retryPolicy The retry policy used by the circuit breaker
 * @param resetTimeout The timeout before the circuit transitions to half open
 * @param throwWrappedException Whether open-circuit exceptions should be wrapped
 * @param window The rolling window that decides when the circuit opens and closes, or
 *               {@code null} for the circuit breaker of Micronaut: the first failure that survives
 *               the retries opens it, and the first success of a half-open circuit closes it
 * @author graemerocher
 * @since 5.0.0
 */
public record CircuitBreakerPolicy(RetryPolicy retryPolicy,
                                   Duration resetTimeout,
                                   boolean throwWrappedException,
                                   @Nullable Window window) {

    public static final Duration DEFAULT_DELAY = Duration.ofMillis(500);
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(5);
    public static final double DEFAULT_MULTIPLIER = 0.0d;
    public static final Duration DEFAULT_RESET_TIMEOUT = Duration.ofSeconds(20);

    public CircuitBreakerPolicy {
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(resetTimeout, "resetTimeout");
        if (resetTimeout.isZero() || resetTimeout.isNegative()) {
            throw new IllegalArgumentException("resetTimeout must be greater than 0");
        }
    }

    /**
     * A policy without a rolling window: the circuit breaker of Micronaut.
     *
     * @param retryPolicy The retry policy used by the circuit breaker
     * @param resetTimeout The timeout before the circuit transitions to half open
     * @param throwWrappedException Whether open-circuit exceptions should be wrapped
     */
    public CircuitBreakerPolicy(RetryPolicy retryPolicy, Duration resetTimeout, boolean throwWrappedException) {
        this(retryPolicy, resetTimeout, throwWrappedException, null);
    }

    /**
     * @return The rolling window of the policy, if it has one
     * @since 5.3.0
     */
    public Optional<Window> getWindow() {
        return Optional.ofNullable(window);
    }

    /**
     * Creates a circuit breaker policy builder.
     *
     * @return A circuit breaker policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the maximum number of attempts.
     *
     * @return The maximum number of attempts
     */
    public int getMaxAttempts() {
        return retryPolicy.maxAttempts();
    }

    /**
     * Returns the delay between retry attempts.
     *
     * @return The delay between retry attempts
     */
    public Duration getDelay() {
        return retryPolicy.delay();
    }

    /**
     * Returns the maximum overall delay.
     *
     * @return The maximum overall delay if configured
     */
    public Optional<Duration> getMaxDelay() {
        return retryPolicy.getMaxDelay();
    }

    /**
     * Returns the delay multiplier.
     *
     * @return The delay multiplier
     */
    public double getMultiplier() {
        return retryPolicy.multiplier();
    }

    /**
     * Returns the retry jitter factor.
     *
     * @return The retry jitter factor
     */
    public double getJitter() {
        return retryPolicy.jitter();
    }

    /**
     * Returns the retry predicate.
     *
     * @return The retry predicate
     */
    public RetryPredicate getPredicate() {
        return retryPolicy.predicate();
    }

    /**
     * Returns the captured exception type.
     *
     * @return The captured exception type
     */
    public Class<? extends Throwable> getCapturedException() {
        return retryPolicy.capturedException();
    }

    /**
     * Returns the included exception types.
     *
     * @return The included exception types
     */
    public List<Class<? extends Throwable>> getIncludes() {
        return retryPolicy.includes();
    }

    /**
     * Returns the excluded exception types.
     *
     * @return The excluded exception types
     */
    public List<Class<? extends Throwable>> getExcludes() {
        return retryPolicy.excludes();
    }

    /**
     * Returns the circuit reset timeout.
     *
     * @return The circuit reset timeout
     */
    public Duration getResetTimeout() {
        return resetTimeout;
    }

    /**
     * Returns whether open-circuit exceptions should be wrapped.
     *
     * @return Whether open-circuit exceptions should be wrapped
     */
    public boolean isThrowWrappedException() {
        return throwWrappedException;
    }

    /**
     * Returns the retry policy view of this circuit breaker policy.
     *
     * @return The retry policy view
     */
    public RetryPolicy asRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Builder for {@link CircuitBreakerPolicy}.
     */
    public static final class Builder {
        private final RetryPolicy.Builder retryPolicyBuilder = RetryPolicy.builder()
            .delay(DEFAULT_DELAY)
            .multiplier(DEFAULT_MULTIPLIER)
            .maxDelay(DEFAULT_MAX_DELAY);
        private Duration resetTimeout = DEFAULT_RESET_TIMEOUT;
        private boolean throwWrappedException;
        private boolean windowed;
        private int requestVolumeThreshold = Window.DEFAULT_REQUEST_VOLUME_THRESHOLD;
        private double failureRatio = Window.DEFAULT_FAILURE_RATIO;
        private int successThreshold = Window.DEFAULT_SUCCESS_THRESHOLD;
        private final List<Class<? extends Throwable>> failOn = new ArrayList<>();
        private final List<Class<? extends Throwable>> skipOn = new ArrayList<>();

        private Builder() {
        }

        /**
         * Sets the maximum number of attempts.
         *
         * @param maxAttempts The maximum number of attempts
         * @return This builder
         */
        public Builder maxAttempts(int maxAttempts) {
            retryPolicyBuilder.maxAttempts(maxAttempts);
            return this;
        }

        /**
         * Sets the delay between retry attempts.
         *
         * @param delay The delay between retry attempts
         * @return This builder
         */
        public Builder delay(Duration delay) {
            retryPolicyBuilder.delay(delay);
            return this;
        }

        /**
         * Sets the maximum overall delay.
         *
         * @param maxDelay The maximum overall delay
         * @return This builder
         */
        public Builder maxDelay(Duration maxDelay) {
            retryPolicyBuilder.maxDelay(maxDelay);
            return this;
        }

        /**
         * Sets the delay multiplier.
         *
         * @param multiplier The delay multiplier
         * @return This builder
         */
        public Builder multiplier(double multiplier) {
            retryPolicyBuilder.multiplier(multiplier);
            return this;
        }

        /**
         * Sets the jitter factor.
         *
         * @param jitter The jitter factor
         * @return This builder
         */
        public Builder jitter(double jitter) {
            retryPolicyBuilder.jitter(jitter);
            return this;
        }

        /**
         * Sets the retry predicate.
         *
         * @param predicate The retry predicate
         * @return This builder
         */
        public Builder predicate(RetryPredicate predicate) {
            retryPolicyBuilder.predicate(predicate);
            return this;
        }

        /**
         * Sets the captured exception type.
         *
         * @param capturedException The captured exception type
         * @return This builder
         */
        public Builder capturedException(Class<? extends Throwable> capturedException) {
            retryPolicyBuilder.capturedException(capturedException);
            return this;
        }

        /**
         * Adds included exception types.
         *
         * @param includes The included exception types
         * @return This builder
         */
        @SafeVarargs
        public final Builder includes(Class<? extends Throwable>... includes) {
            retryPolicyBuilder.includes(includes);
            return this;
        }

        /**
         * Adds excluded exception types.
         *
         * @param excludes The excluded exception types
         * @return This builder
         */
        @SafeVarargs
        public final Builder excludes(Class<? extends Throwable>... excludes) {
            retryPolicyBuilder.excludes(excludes);
            return this;
        }

        /**
         * Sets the circuit reset timeout.
         *
         * @param resetTimeout The circuit reset timeout
         * @return This builder
         */
        public Builder resetTimeout(Duration resetTimeout) {
            this.resetTimeout = resetTimeout;
            return this;
        }

        /**
         * Sets whether open-circuit exceptions should be wrapped.
         *
         * @param throwWrappedException Whether open-circuit exceptions should be wrapped
         * @return This builder
         */
        public Builder throwWrappedException(boolean throwWrappedException) {
            this.throwWrappedException = throwWrappedException;
            return this;
        }

        /**
         * Sets the size of the rolling window of the calls of a closed circuit, see
         * {@link Window#requestVolumeThreshold()}. Setting it, or any other setting of the window,
         * gives the policy a rolling window, with the defaults of the other settings.
         *
         * @param requestVolumeThreshold The number of calls of the window, at least 1, default 20
         * @return This builder
         * @since 5.3.0
         */
        public Builder requestVolumeThreshold(int requestVolumeThreshold) {
            this.windowed = true;
            this.requestVolumeThreshold = requestVolumeThreshold;
            return this;
        }

        /**
         * @param failureRatio The ratio of failures of a full window that opens the circuit,
         *                     between 0 and 1, default 0.5, see {@link Window#failureRatio()}
         * @return This builder
         * @since 5.3.0
         */
        public Builder failureRatio(double failureRatio) {
            this.windowed = true;
            this.failureRatio = failureRatio;
            return this;
        }

        /**
         * @param successThreshold The number of trial calls of a half-open circuit, which all
         *                         succeed to close it, at least 1, default 1, see
         *                         {@link Window#successThreshold()}
         * @return This builder
         * @since 5.3.0
         */
        public Builder successThreshold(int successThreshold) {
            this.windowed = true;
            this.successThreshold = successThreshold;
            return this;
        }

        /**
         * @param failOn The exceptions that count as a failure, default every one, see
         *               {@link Window#failOn()}
         * @return This builder
         * @since 5.3.0
         */
        @SafeVarargs
        public final Builder failOn(Class<? extends Throwable>... failOn) {
            this.windowed = true;
            this.failOn.addAll(List.of(failOn));
            return this;
        }

        /**
         * @param skipOn The exceptions that count as a success, see {@link Window#skipOn()}
         * @return This builder
         * @since 5.3.0
         */
        @SafeVarargs
        public final Builder skipOn(Class<? extends Throwable>... skipOn) {
            this.windowed = true;
            this.skipOn.addAll(List.of(skipOn));
            return this;
        }

        /**
         * Builds the circuit breaker policy.
         *
         * @return The circuit breaker policy
         */
        public CircuitBreakerPolicy build() {
            Window window = windowed ? new Window(requestVolumeThreshold, failureRatio, successThreshold, failOn, skipOn) : null;
            return new CircuitBreakerPolicy(retryPolicyBuilder.build(), resetTimeout, throwWrappedException, window);
        }
    }

    /**
     * The rolling window of a circuit breaker, as in MicroProfile Fault Tolerance: the outcomes of
     * the last {@link #requestVolumeThreshold()} calls of a closed circuit decide when it opens.
     * <ul>
     *     <li>A closed circuit opens when its window is full and the ratio of failures in it
     *     reaches {@link #failureRatio()}; never before the window is full.</li>
     *     <li>An open circuit half-opens after the reset timeout of the policy, and permits
     *     {@link #successThreshold()} trial calls: it closes when they all succeed, and any
     *     failure opens it again. A call beyond the permitted trials is rejected like a call of
     *     an open circuit, and counts for nothing.</li>
     *     <li>Every change of state starts a new window.</li>
     * </ul>
     * An exception counts as a success if it is one of {@link #skipOn()}, else as a failure if it
     * is one of {@link #failOn()}, else as a success.
     *
     * @param requestVolumeThreshold The number of calls of the window, at least 1
     * @param failureRatio           The ratio of failures of a full window that opens the circuit,
     *                               between 0 and 1; a window opens it only with a failure
     * @param successThreshold       The number of trial calls of a half-open circuit, at least 1
     * @param failOn                 The exceptions that count as a failure; empty for every one
     * @param skipOn                 The exceptions that count as a success
     * @since 5.3.0
     */
    public record Window(int requestVolumeThreshold,
                         double failureRatio,
                         int successThreshold,
                         List<Class<? extends Throwable>> failOn,
                         List<Class<? extends Throwable>> skipOn) {

        /**
         * The default size of the window.
         */
        public static final int DEFAULT_REQUEST_VOLUME_THRESHOLD = 20;
        /**
         * The default ratio of failures.
         */
        public static final double DEFAULT_FAILURE_RATIO = 0.5;
        /**
         * The default number of trial calls.
         */
        public static final int DEFAULT_SUCCESS_THRESHOLD = 1;

        /**
         * @param requestVolumeThreshold The number of calls of the window
         * @param failureRatio           The ratio of failures
         * @param successThreshold       The number of trial calls
         * @param failOn                 The exceptions that count as a failure
         * @param skipOn                 The exceptions that count as a success
         */
        public Window {
            if (requestVolumeThreshold < 1) {
                throw new IllegalArgumentException("requestVolumeThreshold must be at least 1");
            }
            if (failureRatio < 0 || failureRatio > 1 || Double.isNaN(failureRatio)) {
                throw new IllegalArgumentException("failureRatio must be between 0 and 1");
            }
            if (successThreshold < 1) {
                throw new IllegalArgumentException("successThreshold must be at least 1");
            }
            failOn = List.copyOf(Objects.requireNonNull(failOn, "failOn"));
            skipOn = List.copyOf(Objects.requireNonNull(skipOn, "skipOn"));
        }

        /**
         * @param failure An exception of a call
         * @return Whether it counts as a failure
         */
        public boolean isFailure(Throwable failure) {
            for (Class<? extends Throwable> type : skipOn) {
                if (type.isInstance(failure)) {
                    return false;
                }
            }
            if (failOn.isEmpty()) {
                return true;
            }
            for (Class<? extends Throwable> type : failOn) {
                if (type.isInstance(failure)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * @return The number of failures of a full window that opens the circuit, at least 1
         */
        public int failureThreshold() {
            return Math.max(1, (int) Math.ceil(failureRatio * requestVolumeThreshold - 1e-9));
        }
    }
}
