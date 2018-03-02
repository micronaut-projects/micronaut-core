/*
 * Copyright 2018 original authors
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
package org.particleframework.retry;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A context object for {@link org.particleframework.retry.annotation.Retry} operations
 *
 * @author graemerocher
 * @since 1.0
 */
public class RetryContext {
    private final int maxAttempts;
    private final double multiplier;
    private final Duration delay;
    private final Duration maxDelay;
    private AtomicInteger attemptNumber = new AtomicInteger(0);
    private long overallDelay = 0;

    public RetryContext(int maxAttempts, double multiplier, Duration delay, Duration maxDelay) {
        this.maxAttempts = maxAttempts;
        this.multiplier = multiplier;
        this.delay = delay;
        this.maxDelay = maxDelay;
    }

    public RetryContext(int maxAttempts, double multiplier, Duration delay) {
        this(maxAttempts, multiplier, delay, null);
    }

    /**
     * Should a retry attempt occur
     * @return True if it should
     */
    public boolean canRetry() {
        return attemptNumber.get() < maxAttempts && ((maxDelay == null) || overallDelay < maxDelay.toMillis());
    }
    /**
     * @return The maximum number of attempts
     */
    public int getMaxAttempts() {
        return maxAttempts;
    }

    /**
     * @return The number of the current attempt
     */
    public int currentAttempt() {
        return this.attemptNumber.get();
    }

    /**
     * @return Increments the attempts
     */
    public int incrementAttempts() {
        return this.attemptNumber.incrementAndGet();
    }
    /**
     * @return The multiplier to use between delays
     */
    public OptionalDouble getMultiplier() {
        return multiplier > 0 ? OptionalDouble.of(multiplier) : OptionalDouble.empty();
    }

    /**
     * @return The delay between attempts
     */
    public Duration getDelay() {
        return delay;
    }

    /**
     * @return Return the milli second value for the next delay
     */
    public long nextDelay() {
        OptionalDouble multiplier = getMultiplier();
        int current = currentAttempt();
        long delay;
        if(multiplier.isPresent()) {
            delay = (long) (getDelay().toMillis() * multiplier.getAsDouble())  * current;
        }
        else {
            delay = getDelay().toMillis() * current;
        }
        overallDelay += delay;
        return delay;
    }

    /**
     * @return The maximum overall delay
     */
    public Optional<Duration> getMaxDelay() {
        return Optional.ofNullable(maxDelay);
    }
}
