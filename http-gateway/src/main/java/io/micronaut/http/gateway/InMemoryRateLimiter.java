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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Token buckets in the memory of this gateway, like the script of Spring Cloud Gateway's
 * {@code RedisRateLimiter}: a bucket holds up to the burst capacity, and refills at the replenish
 * rate per second; a request takes its requested tokens, or is denied when the bucket has fewer.
 * The buckets are not shared with other gateways.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class InMemoryRateLimiter implements GatewayRateLimiter {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier nanoTime;

    /**
     * A limiter on the system clock.
     */
    public InMemoryRateLimiter() {
        this(System::nanoTime);
    }

    /**
     * @param nanoTime The clock, in nanoseconds, e.g. a fake one in a test
     */
    InMemoryRateLimiter(LongSupplier nanoTime) {
        this.nanoTime = nanoTime;
    }

    @Override
    public CompletionStage<Decision> tryAcquire(String id, GatewayRateLimit limit) {
        Bucket bucket = buckets.computeIfAbsent(id, k -> new Bucket(limit.burstCapacity(), nanoTime.getAsLong()));
        return CompletableFuture.completedFuture(bucket.take(limit, nanoTime.getAsLong()));
    }

    /**
     * One bucket. The tokens are counted in nanotokens, so that a refill of less than a token
     * per call is not lost.
     */
    private static final class Bucket {
        private long nanoTokens;
        private long last;

        Bucket(long capacity, long now) {
            this.nanoTokens = capacity * NANOS_PER_SECOND;
            this.last = now;
        }

        synchronized Decision take(GatewayRateLimit limit, long now) {
            long capacity = limit.burstCapacity() * NANOS_PER_SECOND;
            long elapsed = Math.max(0, now - last);
            last = now;
            // replenishRate tokens per second: replenishRate nanotokens per nanosecond
            long refill = elapsed > Long.MAX_VALUE / Math.max(1, limit.replenishRate()) ? capacity : elapsed * limit.replenishRate();
            nanoTokens = Math.min(capacity, nanoTokens + refill);
            long requested = limit.requestedTokens() * NANOS_PER_SECOND;
            boolean allowed = capacity > 0 && nanoTokens >= requested;
            if (allowed) {
                nanoTokens -= requested;
            }
            return new Decision(allowed, nanoTokens / NANOS_PER_SECOND);
        }
    }
}
