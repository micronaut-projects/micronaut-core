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

import io.micronaut.retry.annotation.DefaultRetryPredicate;
import io.micronaut.retry.annotation.RetryPredicate;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typed retry policy for programmatic retry execution.
 *
 * @param maxAttempts The maximum number of attempts
 * @param delay The delay between retry attempts
 * @param maxDelay The maximum overall delay
 * @param multiplier The delay multiplier
 * @param jitter The retry jitter factor
 * @param predicate The retry predicate
 * @param capturedException The captured exception type
 * @param includes The included exception types
 * @param excludes The excluded exception types
 * @author graemerocher
 * @since 5.0.0
 */
public record RetryPolicy(int maxAttempts,
                          Duration delay,
                          @Nullable Duration maxDelay,
                          double multiplier,
                          double jitter,
                          RetryPredicate predicate,
                          Class<? extends Throwable> capturedException,
                          List<Class<? extends Throwable>> includes,
                          List<Class<? extends Throwable>> excludes) {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;
    public static final Duration DEFAULT_DELAY = Duration.ofSeconds(1);
    public static final double DEFAULT_MULTIPLIER = 1.0d;
    public static final double DEFAULT_JITTER = 0.0d;
    public static final Class<? extends Throwable> DEFAULT_CAPTURED_EXCEPTION = RuntimeException.class;

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
        }
        Objects.requireNonNull(delay, "delay");
        if (delay.isNegative()) {
            throw new IllegalArgumentException("delay must be greater than or equal to 0");
        }
        if (maxDelay != null && maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must be greater than or equal to 0");
        }
        if (multiplier < 0) {
            throw new IllegalArgumentException("multiplier must be greater than or equal to 0");
        }
        if (jitter < 0 || jitter > 1) {
            throw new IllegalArgumentException("jitter must be between 0 and 1");
        }
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(capturedException, "capturedException");
        includes = List.copyOf(Objects.requireNonNull(includes, "includes"));
        excludes = List.copyOf(Objects.requireNonNull(excludes, "excludes"));
    }

    /**
     * Creates a retry policy builder.
     *
     * @return A retry policy builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the maximum overall delay.
     *
     * @return The maximum overall delay if configured
     */
    public Optional<Duration> getMaxDelay() {
        return Optional.ofNullable(maxDelay);
    }

    /**
     * Builder for {@link RetryPolicy}.
     */
    public static final class Builder {
        private int maxAttempts = DEFAULT_MAX_ATTEMPTS;
        private Duration delay = DEFAULT_DELAY;
        @Nullable
        private Duration maxDelay;
        private double multiplier = DEFAULT_MULTIPLIER;
        private double jitter = DEFAULT_JITTER;
        private RetryPredicate predicate = new DefaultRetryPredicate();
        private Class<? extends Throwable> capturedException = DEFAULT_CAPTURED_EXCEPTION;
        private final List<Class<? extends Throwable>> includes = new ArrayList<>();
        private final List<Class<? extends Throwable>> excludes = new ArrayList<>();
        private boolean explicitPredicate;

        private Builder() {
        }

        /**
         * Sets the maximum number of attempts.
         *
         * @param maxAttempts The maximum number of attempts
         * @return This builder
         */
        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Sets the delay between retry attempts.
         *
         * @param delay The delay between retry attempts
         * @return This builder
         */
        public Builder delay(Duration delay) {
            this.delay = delay;
            return this;
        }

        /**
         * Sets the maximum overall delay.
         *
         * @param maxDelay The maximum overall delay
         * @return This builder
         */
        public Builder maxDelay(@Nullable Duration maxDelay) {
            this.maxDelay = maxDelay;
            return this;
        }

        /**
         * Sets the delay multiplier.
         *
         * @param multiplier The delay multiplier
         * @return This builder
         */
        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Sets the jitter factor.
         *
         * @param jitter The jitter factor
         * @return This builder
         */
        public Builder jitter(double jitter) {
            this.jitter = jitter;
            return this;
        }

        /**
         * Sets the retry predicate.
         *
         * @param predicate The retry predicate
         * @return This builder
         */
        public Builder predicate(RetryPredicate predicate) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
            explicitPredicate = true;
            return this;
        }

        /**
         * Sets the captured exception type.
         *
         * @param capturedException The captured exception type
         * @return This builder
         */
        public Builder capturedException(Class<? extends Throwable> capturedException) {
            this.capturedException = Objects.requireNonNull(capturedException, "capturedException");
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
            Collections.addAll(this.includes, Objects.requireNonNull(includes, "includes"));
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
            Collections.addAll(this.excludes, Objects.requireNonNull(excludes, "excludes"));
            return this;
        }

        /**
         * Builds the retry policy.
         *
         * @return The retry policy
         */
        public RetryPolicy build() {
            RetryPredicate resolvedPredicate = explicitPredicate
                ? predicate
                : new DefaultRetryPredicate(List.copyOf(includes), List.copyOf(excludes));
            return new RetryPolicy(
                maxAttempts,
                delay,
                maxDelay,
                multiplier,
                jitter,
                resolvedPredicate,
                capturedException,
                includes,
                excludes
            );
        }
    }
}
