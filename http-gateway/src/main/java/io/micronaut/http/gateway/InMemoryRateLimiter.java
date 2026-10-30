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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/**
 * Token buckets in the memory of this gateway, like the script of Spring Cloud Gateway's
 * {@code RedisRateLimiter}: a bucket holds up to the burst capacity, and refills at the replenish
 * rate per second; a request takes its requested tokens, or is denied when the bucket has fewer.
 * The buckets are not shared with other gateways. A bucket that refilled is removed, and the
 * buckets are at most {@link #DEFAULT_MAX_BUCKETS} by default: a new key is denied while they are
 * all taken by keys that still have a partial bucket, so that keys an attacker chooses, e.g.
 * header values, cannot fill the memory.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class InMemoryRateLimiter implements GatewayRateLimiter {
    /**
     * The default maximum number of buckets.
     */
    public static final int DEFAULT_MAX_BUCKETS = 100_000;

    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    /**
     * How often the buckets that refilled are removed.
     */
    private static final long SWEEP_INTERVAL_NANOS = NANOS_PER_SECOND;

    private final Map<Object, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;
    private final int maxBuckets;
    private final AtomicBoolean sweeping = new AtomicBoolean();
    private volatile long lastSweep;

    /**
     * A limiter on the system clock.
     */
    public InMemoryRateLimiter() {
        this(DEFAULT_MAX_BUCKETS);
    }

    /**
     * A limiter on the system clock.
     *
     * @param maxBuckets The maximum number of buckets, at least 1
     */
    public InMemoryRateLimiter(int maxBuckets) {
        this(System::nanoTime, maxBuckets);
    }

    /**
     * @param nanoTime The clock, in nanoseconds, e.g. a fake one in a test
     */
    InMemoryRateLimiter(LongSupplier nanoTime) {
        this(nanoTime, DEFAULT_MAX_BUCKETS);
    }

    /**
     * @param nanoTime   The clock, in nanoseconds, e.g. a fake one in a test
     * @param maxBuckets The maximum number of buckets, at least 1
     */
    InMemoryRateLimiter(LongSupplier nanoTime, int maxBuckets) {
        if (maxBuckets < 1) {
            throw new IllegalArgumentException("The maximum number of buckets is less than 1: " + maxBuckets);
        }
        this.nanoTime = nanoTime;
        this.maxBuckets = maxBuckets;
        this.lastSweep = nanoTime.getAsLong();
    }

    @Override
    public CompletionStage<Decision> tryAcquire(BucketKey key, Limit limit) {
        return CompletableFuture.completedFuture(take(key, limit));
    }

    private Decision take(BucketKey id, Limit limit) {
        long now = nanoTime.getAsLong();
        sweep(now);
        while (true) {
            Bucket bucket = buckets.get(id);
            if (bucket == null) {
                if (buckets.size() >= maxBuckets) {
                    // every bucket is taken, until the next sweep removes the full ones: a scan for
                    // each new key would cost a scan per request of an attack
                    return new Decision(false, 0);
                }
                bucket = buckets.computeIfAbsent(id, k -> new Bucket(limit.burstCapacity(), now));
            }
            Decision decision = bucket.take(limit, now);
            if (decision != null) {
                return decision;
            }
            // the bucket was removed as full: a new one is full too
        }
    }

    /**
     * Remove the buckets that refilled: a full bucket is the same as a new one, so that the
     * buckets of the keys that are not seen any more do not stay in memory.
     *
     * @param now The time
     */
    private void sweep(long now) {
        if (now - lastSweep < SWEEP_INTERVAL_NANOS || !sweeping.compareAndSet(false, true)) {
            return;
        }
        try {
            lastSweep = now;
            buckets.values().removeIf(bucket -> bucket.removeIfFull(now));
        } finally {
            sweeping.set(false);
        }
    }

    /**
     * @return The number of buckets in memory
     */
    int size() {
        return buckets.size();
    }

    /**
     * One bucket. The tokens are counted in nanotokens, so that a refill of less than a token
     * per call is not lost.
     */
    private static final class Bucket {
        private long nanoTokens;
        private long last;
        private long capacity;
        private long replenishRate;
        private boolean removed;

        Bucket(long capacity, long now) {
            this.nanoTokens = capacity * NANOS_PER_SECOND;
            this.capacity = this.nanoTokens;
            this.last = now;
        }

        /**
         * @return The decision, or {@code null} if the bucket was removed
         */
        synchronized @Nullable Decision take(Limit limit, long now) {
            if (removed) {
                return null;
            }
            capacity = limit.burstCapacity() * NANOS_PER_SECOND;
            replenishRate = limit.replenishRate();
            refill(now);
            long requested = limit.requestedTokens() * NANOS_PER_SECOND;
            boolean allowed = capacity > 0 && nanoTokens >= requested;
            if (allowed) {
                nanoTokens -= requested;
            }
            return new Decision(allowed, nanoTokens / NANOS_PER_SECOND);
        }

        private void refill(long now) {
            long elapsed = Math.max(0, now - last);
            last = now;
            // replenishRate tokens per second: replenishRate nanotokens per nanosecond
            long refill = elapsed > Long.MAX_VALUE / Math.max(1, replenishRate) ? capacity : elapsed * replenishRate;
            nanoTokens = Math.min(capacity, nanoTokens + refill);
        }

        synchronized boolean removeIfFull(long now) {
            refill(now);
            removed = nanoTokens >= capacity;
            return removed;
        }
    }
}
