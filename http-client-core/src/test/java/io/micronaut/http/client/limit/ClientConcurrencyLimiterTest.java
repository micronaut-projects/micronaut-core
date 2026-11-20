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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class ClientConcurrencyLimiterTest {

    @Test
    void anExchangeOverTheCapIsRejectedAtOnce() {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1);
        Runnable permit = limiter.tryAcquire();
        Assertions.assertNotNull(permit);
        Assertions.assertNull(limiter.tryAcquire());
        Assertions.assertEquals(1, limiter.getRejectedCount());
        permit.run();
        // a second release does not give back another permit
        permit.run();
        Assertions.assertEquals(0, limiter.getInFlight());
        Runnable next = limiter.tryAcquire();
        Assertions.assertNotNull(next);
        Assertions.assertNull(limiter.tryAcquire());
        next.run();
        Assertions.assertEquals(1, limiter.getAvailablePermits());
    }

    @Test
    void theCapIsNeverExceededAndEveryPermitComesBack() throws Exception {
        int max = 3;
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", max);
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        int threads = 12;
        int iterations = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int i = 0; i < iterations; i++) {
                        Runnable permit = limiter.tryAcquire();
                        if (permit != null) {
                            maxSeen.accumulateAndGet(holding.incrementAndGet(), Math::max);
                            holding.decrementAndGet();
                            permit.run();
                            permit.run();
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
        Assertions.assertEquals((long) threads * iterations, limiter.getAcquiredCount() + limiter.getRejectedCount());
    }

    @Test
    void invalidSettingsAreRejected() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> new ClientConcurrencyLimiter("svc", 0));
    }
}
