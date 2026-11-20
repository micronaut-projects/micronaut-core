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
package io.micronaut.http.server.limit;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Limits the number of requests in flight. A request over the limit is rejected with
 * 503 (Service Unavailable), since the server cannot tell when a permit will be free; a
 * {@code Retry-After} is only sent if a hint was configured.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class ConcurrencyRequestLimiter implements RequestLimiter {

    private final String name;
    private final int maxConcurrent;
    private final Acquisition.Rejected rejected;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final LongAdder acquired = new LongAdder();
    private final LongAdder rejectedCount = new LongAdder();

    /**
     * @param name          The name
     * @param maxConcurrent The maximum number of requests in flight, at least 1
     * @param retryAfter    The {@code Retry-After} hint of a rejection, or {@code null} for none
     */
    public ConcurrencyRequestLimiter(String name, int maxConcurrent, @Nullable Duration retryAfter) {
        if (maxConcurrent < 1) {
            throw new IllegalArgumentException("max-concurrent must be at least 1: " + maxConcurrent);
        }
        this.name = Objects.requireNonNull(name, "name");
        this.maxConcurrent = maxConcurrent;
        this.rejected = Acquisition.rejected(HttpStatus.SERVICE_UNAVAILABLE, retryAfter);
    }

    @Override
    public Acquisition tryAcquire(HttpRequest<?> request) {
        int current = inFlight.get();
        while (current < maxConcurrent) {
            if (inFlight.compareAndSet(current, current + 1)) {
                acquired.increment();
                return Acquisition.granted(inFlight::decrementAndGet);
            }
            current = inFlight.get();
        }
        rejectedCount.increment();
        return rejected;
    }

    /**
     * @return The maximum number of requests in flight
     */
    public int getMaxConcurrent() {
        return maxConcurrent;
    }

    @Override
    public String getName() {
        return name;
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
        return rejectedCount.sum();
    }
}
