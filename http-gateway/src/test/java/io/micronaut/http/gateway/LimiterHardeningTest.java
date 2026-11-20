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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The in-memory limiter under concurrency, with a clock that the threads sample out of order, and
 * with values near the limits of its counters.
 */
class LimiterHardeningTest {

    private static GatewayRateLimiter.Decision take(InMemoryRateLimiter limiter, String key, GatewayRateLimiter.Limit limit) throws Exception {
        return limiter.tryAcquire(new GatewayRateLimiter.BucketKey("route", key), limit).toCompletableFuture().get();
    }

    @Test
    void concurrentNewKeysDoNotGoOverTheMaximumNumberOfBuckets() throws Exception {
        for (int round = 0; round < 20; round++) {
            InMemoryRateLimiter limiter = new InMemoryRateLimiter(new AtomicLong()::get, 1);
            GatewayRateLimiter.Limit limit = new GatewayRateLimiter.Limit(1, 1, 1);
            int threads = 8;
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger allowed = new AtomicInteger();
            List<Thread> started = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                String key = "k" + i;
                Thread thread = Thread.startVirtualThread(() -> {
                    try {
                        start.await();
                        if (take(limiter, key, limit).allowed()) {
                            allowed.incrementAndGet();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
                started.add(thread);
            }
            start.countDown();
            for (Thread thread : started) {
                thread.join();
            }
            assertEquals(1, limiter.size(), "buckets");
            assertEquals(1, allowed.get(), "only the key of the one bucket is allowed");
        }
    }

    @Test
    void aCallThatSampledTheClockEarlierDoesNotCreditTheSameIntervalTwice() throws Exception {
        AtomicLong now = new AtomicLong(1_000_000_000L);
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);
        // one token per second, a burst of 2
        GatewayRateLimiter.Limit limit = new GatewayRateLimiter.Limit(1, 2, 1);
        assertTrue(take(limiter, "k", limit).allowed());
        assertTrue(take(limiter, "k", limit).allowed());
        assertFalse(take(limiter, "k", limit).allowed());
        // a second later: one token
        now.set(2_000_000_000L);
        assertTrue(take(limiter, "k", limit).allowed());
        // a call that sampled the clock half a second earlier enters the bucket after it
        now.set(1_500_000_000L);
        assertFalse(take(limiter, "k", limit).allowed());
        // back at the latest time: nothing more was credited
        now.set(2_000_000_000L);
        assertFalse(take(limiter, "k", limit).allowed(), "the interval was credited twice");
    }

    @Test
    void hugeLimitsDoNotOverflow() throws Exception {
        AtomicLong now = new AtomicLong();
        InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);
        GatewayRateLimiter.Limit limit = new GatewayRateLimiter.Limit(Long.MAX_VALUE / 2, Long.MAX_VALUE / 2, 1);
        assertTrue(take(limiter, "k", limit).allowed());
        now.addAndGet(Long.MAX_VALUE / 4);
        assertTrue(take(limiter, "k", limit).allowed());
        assertTrue(take(limiter, "k", limit).remaining() > 0);
    }

    @Test
    void aRateLimitNeedsARefillAndCountableTokens() {
        assertThrows(IllegalArgumentException.class, () -> GatewayRateLimit.builder(0, 10));
        assertThrows(IllegalArgumentException.class, () -> GatewayRateLimit.builder(10_000_000_000L, 10_000_000_000L));
        assertThrows(IllegalArgumentException.class, () -> GatewayRateLimit.builder(1, 1).requestedTokens(10_000_000_000L));
        // no tokens at all denies every request, as before
        GatewayRateLimit.builder(0, 0).build();
    }
}
