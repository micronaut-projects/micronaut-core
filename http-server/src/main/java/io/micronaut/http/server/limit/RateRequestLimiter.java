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

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

/**
 * A token bucket: {@code permits} tokens are added every {@code period}, evenly, up to
 * {@code burst} tokens; each request takes one. A request that finds the bucket empty is rejected
 * with 429 (Too Many Requests) and a {@code Retry-After} of the time until the next token.
 * A permit has nothing to give back: releasing it does nothing.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RateRequestLimiter implements RequestLimiter {

    private static final Runnable NOTHING = () -> { };

    private final String name;
    private final long capacity;
    private final double nanosPerToken;
    private final LongSupplier clock;
    private final LongAdder acquired = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private double tokens;
    private long lastRefill;

    /**
     * @param name    The name
     * @param permits The number of permits per period, at least 1
     * @param period  The period
     * @param burst   The capacity of the bucket, at least 1
     */
    public RateRequestLimiter(String name, long permits, Duration period, long burst) {
        this(name, permits, period, burst, System::nanoTime);
    }

    /**
     * @param name    The name
     * @param permits The number of permits per period, at least 1
     * @param period  The period
     * @param burst   The capacity of the bucket, at least 1
     * @param clock   The clock, in nanoseconds
     */
    RateRequestLimiter(String name, long permits, Duration period, long burst, LongSupplier clock) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be at least 1: " + permits);
        }
        if (burst < 1) {
            throw new IllegalArgumentException("burst must be at least 1: " + burst);
        }
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("period must be positive: " + period);
        }
        this.name = Objects.requireNonNull(name, "name");
        this.capacity = burst;
        this.nanosPerToken = (double) period.toNanos() / permits;
        this.clock = clock;
        this.tokens = burst;
        this.lastRefill = clock.getAsLong();
    }

    @Override
    public Acquisition tryAcquire(HttpRequest<?> request) {
        long waitNanos;
        synchronized (this) {
            refill();
            if (tokens >= 1) {
                tokens -= 1;
                acquired.increment();
                return Acquisition.granted(NOTHING);
            }
            waitNanos = (long) Math.ceil((1 - tokens) * nanosPerToken);
        }
        rejected.increment();
        return Acquisition.rejected(HttpStatus.TOO_MANY_REQUESTS, Duration.ofNanos(Math.max(1, waitNanos)));
    }

    private void refill() {
        long now = clock.getAsLong();
        long elapsed = now - lastRefill;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + elapsed / nanosPerToken);
            lastRefill = now;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getInFlight() {
        return 0;
    }

    @Override
    public synchronized long getAvailablePermits() {
        refill();
        return (long) tokens;
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
