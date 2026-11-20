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

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

class RequestLimitersTest {

    private static final HttpRequest<?> REQUEST = HttpRequest.GET("/");

    @Test
    void concurrencyLimiterNeverExceedsItsLimitAndReturnsToZero() throws Exception {
        int max = 4;
        ConcurrencyRequestLimiter limiter = new ConcurrencyRequestLimiter("c", max, null);
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        int threads = 16;
        int iterations = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        Acquisition acquisition = limiter.tryAcquire(REQUEST);
                        if (acquisition instanceof Acquisition.Permit permit) {
                            int now = holding.incrementAndGet();
                            maxSeen.accumulateAndGet(now, Math::max);
                            holding.decrementAndGet();
                            permit.release();
                            // a second release must not give back another permit
                            permit.release();
                        } else {
                            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ((Acquisition.Rejected) acquisition).status());
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        Assertions.assertTrue(maxSeen.get() <= max, "Held " + maxSeen.get() + " permits at once");
        Assertions.assertEquals(0, limiter.getInFlight());
        Assertions.assertEquals(max, limiter.getAvailablePermits());
        Assertions.assertEquals((long) threads * iterations, limiter.getAcquiredCount() + limiter.getRejectedCount());
    }

    @Test
    void concurrencyLimiterRejectsOverTheLimit() {
        ConcurrencyRequestLimiter limiter = new ConcurrencyRequestLimiter("c", 1, Duration.ofSeconds(3));
        Acquisition.Permit permit = (Acquisition.Permit) limiter.tryAcquire(REQUEST);
        Acquisition.Rejected rejected = (Acquisition.Rejected) limiter.tryAcquire(REQUEST);
        Assertions.assertEquals(Duration.ofSeconds(3), rejected.retryAfter());
        permit.release();
        Assertions.assertInstanceOf(Acquisition.Permit.class, limiter.tryAcquire(REQUEST));
    }

    @Test
    void rateLimiterRefillsEvenly() {
        AtomicLong clock = new AtomicLong();
        RateRequestLimiter limiter = new RateRequestLimiter("r", 2, Duration.ofSeconds(1), 2, clock::get);
        Assertions.assertInstanceOf(Acquisition.Permit.class, limiter.tryAcquire(REQUEST));
        Assertions.assertInstanceOf(Acquisition.Permit.class, limiter.tryAcquire(REQUEST));
        Acquisition.Rejected rejected = (Acquisition.Rejected) limiter.tryAcquire(REQUEST);
        Assertions.assertEquals(HttpStatus.TOO_MANY_REQUESTS, rejected.status());
        Assertions.assertEquals(Duration.ofMillis(500), rejected.retryAfter());

        clock.addAndGet(Duration.ofMillis(250).toNanos());
        rejected = (Acquisition.Rejected) limiter.tryAcquire(REQUEST);
        Assertions.assertEquals(Duration.ofMillis(250), rejected.retryAfter());

        clock.addAndGet(Duration.ofMillis(250).toNanos());
        Assertions.assertInstanceOf(Acquisition.Permit.class, limiter.tryAcquire(REQUEST));
        Assertions.assertInstanceOf(Acquisition.Rejected.class, limiter.tryAcquire(REQUEST));

        // a long quiet time does not fill the bucket over its burst
        clock.addAndGet(Duration.ofHours(1).toNanos());
        Assertions.assertEquals(2, limiter.getAvailablePermits());
        Assertions.assertEquals(3, limiter.getAcquiredCount());
        Assertions.assertEquals(3, limiter.getRejectedCount());
    }

    @Test
    void rateLimiterGrantsNoMoreThanItsTokensUnderContention() throws Exception {
        AtomicLong clock = new AtomicLong();
        RateRequestLimiter limiter = new RateRequestLimiter("r", 100, Duration.ofSeconds(1), 100, clock::get);
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger granted = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < 1000; i++) {
                        if (limiter.tryAcquire(REQUEST) instanceof Acquisition.Permit) {
                            granted.incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        Assertions.assertEquals(100, granted.get());
    }

    @Test
    void invalidSettingsAreRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ConcurrencyRequestLimiter("c", 0, null));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new RateRequestLimiter("r", 0, Duration.ofSeconds(1), 1));
        Assertions.assertThrows(IllegalArgumentException.class, () -> new RateRequestLimiter("r", 1, Duration.ZERO, 1));
    }
}
