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
    /**
     * The buckets in the map, and the slots reserved for the ones being added: a slot is reserved
     * before a bucket is added, so that concurrent new keys cannot go over the maximum.
     */
    private final java.util.concurrent.atomic.AtomicInteger slots = new java.util.concurrent.atomic.AtomicInteger();
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
                if (slots.incrementAndGet() > maxBuckets) {
                    // every bucket is taken, until the next sweep removes the full ones: a scan for
                    // each new key would cost a scan per request of an attack
                    slots.decrementAndGet();
                    return new Decision(false, 0);
                }
                Bucket created = new Bucket(limit.burstCapacity(), now);
                Bucket existing = buckets.putIfAbsent(id, created);
                if (existing != null) {
                    // another call added the bucket of the key: the slot is not needed
                    slots.decrementAndGet();
                    bucket = existing;
                } else {
                    bucket = created;
                }
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
            for (Map.Entry<Object, Bucket> entry : buckets.entrySet()) {
                if (entry.getValue().removeIfFull(now) && buckets.remove(entry.getKey(), entry.getValue())) {
                    slots.decrementAndGet();
                }
            }
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
            this.nanoTokens = nanos(capacity);
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
            capacity = nanos(limit.burstCapacity());
            replenishRate = limit.replenishRate();
            refill(now);
            long requested = nanos(limit.requestedTokens());
            boolean allowed = capacity > 0 && nanoTokens >= requested;
            if (allowed) {
                nanoTokens -= requested;
            }
            return new Decision(allowed, nanoTokens / NANOS_PER_SECOND);
        }

        private void refill(long now) {
            if (now <= last) {
                // a call that sampled the clock earlier than the last one: the interval since
                // was credited already, and the clock of the bucket does not go back
                return;
            }
            long elapsed = now - last;
            last = now;
            // replenishRate tokens per second: replenishRate nanotokens per nanosecond
            long refill = elapsed > Long.MAX_VALUE / Math.max(1, replenishRate) ? capacity : elapsed * replenishRate;
            nanoTokens = refill >= capacity - nanoTokens ? capacity : nanoTokens + refill;
        }

        /**
         * @param tokens A number of tokens
         * @return The number of nanotokens, at most {@link Long#MAX_VALUE}
         */
        private static long nanos(long tokens) {
            return tokens > Long.MAX_VALUE / NANOS_PER_SECOND ? Long.MAX_VALUE : tokens * NANOS_PER_SECOND;
        }

        synchronized boolean removeIfFull(long now) {
            refill(now);
            removed = nanoTokens >= capacity;
            return removed;
        }
    }
}
