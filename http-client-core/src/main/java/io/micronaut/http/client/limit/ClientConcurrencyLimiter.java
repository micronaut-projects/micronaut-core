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
package io.micronaut.http.client.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.limit.LimiterState;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Caps the exchanges in flight of a service client, whatever the connections and HTTP/2 streams
 * they use. An exchange over the cap fails at once with a
 * {@link io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException} before it is
 * sent: there is no wait queue.
 *
 * <p>A permit is a {@link Runnable} that gives it back; only its first run has an effect.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ClientConcurrencyLimiter implements LimiterState {

    private final String name;
    private final int maxConcurrent;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final LongAdder acquired = new LongAdder();
    private final LongAdder rejected = new LongAdder();

    /**
     * @param name          The name, usually the service id
     * @param maxConcurrent The maximum number of exchanges in flight, at least 1
     */
    public ClientConcurrencyLimiter(String name, int maxConcurrent) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("max-concurrent-requests must be at least 1: " + maxConcurrent);
        }
        this.name = Objects.requireNonNull(name, "name");
        this.maxConcurrent = maxConcurrent;
    }

    /**
     * Take a permit if one is free.
     *
     * @return The permit, which gives it back when run, or {@code null} if the cap is reached
     */
    public @Nullable Runnable tryAcquire() {
        int current = inFlight.get();
        while (current < maxConcurrent) {
            if (inFlight.compareAndSet(current, current + 1)) {
                acquired.increment();
                AtomicBoolean released = new AtomicBoolean();
                return () -> {
                    if (released.compareAndSet(false, true)) {
                        inFlight.decrementAndGet();
                    }
                };
            }
            current = inFlight.get();
        }
        rejected.increment();
        return null;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * @return The maximum number of exchanges in flight
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    @Override
    public int getInFlight() {
        return inFlight.get();
    }

    @Override
    public long getAvailablePermits() {
        return Math.max(0, maxConcurrent - inFlight.get());
    }

    @Override
    public long getAcquiredCount() {
        return acquired.sum();
    }

    @Override
    public long getRejectedCount() {
        return rejected.sum();
    }
}
