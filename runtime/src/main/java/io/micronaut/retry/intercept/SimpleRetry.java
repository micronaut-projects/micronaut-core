/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.retry.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.retry.RetryState;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A context object for {@link io.micronaut.retry.annotation.Retryable} operations.
 *
 * @author graemerocher
 * @since 1.0
 */
class SimpleRetry implements RetryState, MutableRetryState {

    private final int maxAttempts;
    private final double multiplier;
    private final Duration delay;
    private final Duration maxDelay;
    private final boolean hasIncludes;
    private final boolean hasExcludes;
    private AtomicInteger attemptNumber = new AtomicInteger(0);
    private AtomicLong overallDelay = new AtomicLong(0);
    private final Set<Class<? extends Throwable>> includes;
    private final Set<Class<? extends Throwable>> excludes;

    /**
     * @param maxAttempts The maximum number of attemps
     * @param multiplier The multiplier to use between delays
     * @param delay The overall delay so far
     * @param maxDelay The maximum overall delay
     * @param includes Classes to include for retry
     * @param excludes Classes to exclude for retry
     */
    SimpleRetry(
        int maxAttempts,
        double multiplier,
        Duration delay,
        Duration maxDelay,
        Set<Class<? extends Throwable>> includes,
        Set<Class<? extends Throwable>> excludes) {

        this.maxAttempts = maxAttempts;
        this.multiplier = multiplier;
        this.delay = delay;
        this.maxDelay = maxDelay;
        this.includes = includes == null ? Collections.emptySet() : includes;
        this.excludes = excludes == null ? Collections.emptySet() : excludes;
        this.hasIncludes = !this.includes.isEmpty();
        this.hasExcludes = !this.excludes.isEmpty();
    }

    /**
     * @param maxAttempts The maximum number of attemps
     * @param multiplier The multiplier to use between delays
     * @param delay The overall delay so far
     * @param maxDelay The maximum overall delay
     */
    SimpleRetry(int maxAttempts, double multiplier, Duration delay, Duration maxDelay) {
        this(maxAttempts, multiplier, delay, maxDelay, Collections.emptySet(), Collections.emptySet());
    }

    /**
     * @param maxAttempts The maximum number of attemps
     * @param multiplier  The multiplier to use between delays
     * @param delay       The overall delay so far
     */
    SimpleRetry(int maxAttempts, double multiplier, Duration delay) {
        this(maxAttempts, multiplier, delay, null);
    }

    /**
     * Should a retry attempt occur.
     *
     * @return True if it should
     */
    @Override
    public boolean canRetry(Throwable exception) {
        if (exception == null) {
            return false;
        }

        Class<? extends Throwable> exceptionClass = exception.getClass();
        if (hasIncludes && !includes.contains(exceptionClass)) {
            return false;
        } else if (hasExcludes && excludes.contains(exceptionClass)) {
            return false;
        } else {
            return this.attemptNumber.incrementAndGet() < (maxAttempts + 1) && ((maxDelay == null) || overallDelay.get() < maxDelay.toMillis());
        }
    }

    /**
     * @return The maximum number of attempts
     */
    @Override
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * @return The number of the current attempt
     */
    @Override
    public int currentAttempt() {
        return this.attemptNumber.get();
    }

    /**
     * @return The multiplier to use between delays
     */
    @Override
    public OptionalDouble getMultiplier() {
        return multiplier > 0 ? OptionalDouble.of(multiplier) : OptionalDouble.empty();
    }

    /**
     * @return The delay between attempts
     */
    @Override
    public Duration getDelay() {
        return delay;
    }

    /**
     * @return The overall delay so far
     */
    @Override
    public Duration getOverallDelay() {
        return Duration.ofMillis(overallDelay.get());
    }

    /**
     * @return The maximum overall delay
     */
    @Override
    public Optional<Duration> getMaxDelay() {
        return Optional.ofNullable(maxDelay);
    }

    /**
     * @return Return the milli second value for the next delay
     */
    @Override
    @Internal
    public long nextDelay() {
        double multiplier = getMultiplier().orElse(1.0);
        int current = attemptNumber.get() + 1;
        long delay = (long) (getDelay().toMillis() * multiplier) * current;
        overallDelay.addAndGet(delay);
        return delay;
    }
}
