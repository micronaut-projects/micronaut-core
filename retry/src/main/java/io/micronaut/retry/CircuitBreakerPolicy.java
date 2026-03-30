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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.retry.annotation.RetryPredicate;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed circuit breaker policy for programmatic execution.
 *
 * @author graemerocher
 * @since 5.0.0
 */
public record CircuitBreakerPolicy(@NonNull RetryPolicy retryPolicy,
                                   @NonNull Duration resetTimeout,
                                   boolean throwWrappedException) {

    public CircuitBreakerPolicy {
        Objects.requireNonNull(retryPolicy, "retryPolicy");
        Objects.requireNonNull(resetTimeout, "resetTimeout");
        if (resetTimeout.isZero() || resetTimeout.isNegative()) {
            throw new IllegalArgumentException("resetTimeout must be greater than 0");
        }
    }

    /**
     * Creates a circuit breaker policy builder.
     *
     * @return A circuit breaker policy builder
     */
    public static @NonNull Builder builder() {
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
    public @NonNull Duration getDelay() {
        return retryPolicy.delay();
    }

    /**
     * Returns the maximum overall delay.
     *
     * @return The maximum overall delay if configured
     */
    public @NonNull Optional<Duration> getMaxDelay() {
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
    public @NonNull RetryPredicate getPredicate() {
        return retryPolicy.predicate();
    }

    /**
     * Returns the captured exception type.
     *
     * @return The captured exception type
     */
    public @NonNull Class<? extends Throwable> getCapturedException() {
        return retryPolicy.capturedException();
    }

    /**
     * Returns the included exception types.
     *
     * @return The included exception types
     */
    public @NonNull List<Class<? extends Throwable>> getIncludes() {
        return retryPolicy.includes();
    }

    /**
     * Returns the excluded exception types.
     *
     * @return The excluded exception types
     */
    public @NonNull List<Class<? extends Throwable>> getExcludes() {
        return retryPolicy.excludes();
    }

    /**
     * Returns the circuit reset timeout.
     *
     * @return The circuit reset timeout
     */
    public @NonNull Duration getResetTimeout() {
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
    public @NonNull RetryPolicy asRetryPolicy() {
        return retryPolicy;
    }

    /**
     * Builder for {@link CircuitBreakerPolicy}.
     */
    public static final class Builder {
        private final RetryPolicy.Builder retryPolicyBuilder = RetryPolicy.builder()
            .delay(Duration.ofMillis(500))
            .multiplier(0.0d)
            .maxDelay(Duration.ofSeconds(5));
        private Duration resetTimeout = Duration.ofSeconds(20);
        private boolean throwWrappedException;

        private Builder() {
        }

        /**
         * Sets the maximum number of attempts.
         *
         * @param maxAttempts The maximum number of attempts
         * @return This builder
         */
        public @NonNull Builder maxAttempts(int maxAttempts) {
            retryPolicyBuilder.maxAttempts(maxAttempts);
            return this;
        }

        /**
         * Sets the delay between retry attempts.
         *
         * @param delay The delay between retry attempts
         * @return This builder
         */
        public @NonNull Builder delay(@NonNull Duration delay) {
            retryPolicyBuilder.delay(delay);
            return this;
        }

        /**
         * Sets the maximum overall delay.
         *
         * @param maxDelay The maximum overall delay
         * @return This builder
         */
        public @NonNull Builder maxDelay(Duration maxDelay) {
            retryPolicyBuilder.maxDelay(maxDelay);
            return this;
        }

        /**
         * Sets the delay multiplier.
         *
         * @param multiplier The delay multiplier
         * @return This builder
         */
        public @NonNull Builder multiplier(double multiplier) {
            retryPolicyBuilder.multiplier(multiplier);
            return this;
        }

        /**
         * Sets the jitter factor.
         *
         * @param jitter The jitter factor
         * @return This builder
         */
        public @NonNull Builder jitter(double jitter) {
            retryPolicyBuilder.jitter(jitter);
            return this;
        }

        /**
         * Sets the retry predicate.
         *
         * @param predicate The retry predicate
         * @return This builder
         */
        public @NonNull Builder predicate(@NonNull RetryPredicate predicate) {
            retryPolicyBuilder.predicate(predicate);
            return this;
        }

        /**
         * Sets the captured exception type.
         *
         * @param capturedException The captured exception type
         * @return This builder
         */
        public @NonNull Builder capturedException(@NonNull Class<? extends Throwable> capturedException) {
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
        public final @NonNull Builder includes(@NonNull Class<? extends Throwable>... includes) {
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
        public final @NonNull Builder excludes(@NonNull Class<? extends Throwable>... excludes) {
            retryPolicyBuilder.excludes(excludes);
            return this;
        }

        /**
         * Sets the circuit reset timeout.
         *
         * @param resetTimeout The circuit reset timeout
         * @return This builder
         */
        public @NonNull Builder resetTimeout(@NonNull Duration resetTimeout) {
            this.resetTimeout = resetTimeout;
            return this;
        }

        /**
         * Sets whether open-circuit exceptions should be wrapped.
         *
         * @param throwWrappedException Whether open-circuit exceptions should be wrapped
         * @return This builder
         */
        public @NonNull Builder throwWrappedException(boolean throwWrappedException) {
            this.throwWrappedException = throwWrappedException;
            return this;
        }

        /**
         * Builds the circuit breaker policy.
         *
         * @return The circuit breaker policy
         */
        public @NonNull CircuitBreakerPolicy build() {
            return new CircuitBreakerPolicy(retryPolicyBuilder.build(), resetTimeout, throwWrappedException);
        }
    }
}
