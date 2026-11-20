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

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.client.exceptions.ConcurrencyLimitExceededException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

class ClientConcurrencyLimiterTest {

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    @AfterEach
    void stop() {
        scheduler.shutdownNow();
    }

    @Test
    void aFullQueueRejectsAtOnce() {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1, 0, Duration.ofSeconds(10));
        Runnable permit = acquireNow(limiter);
        ConcurrencyLimitExceededException e = failure(limiter.acquire(scheduler));
        Assertions.assertFalse(e.isTimedOut());
        Assertions.assertEquals("svc", e.getLimiterName());
        permit.run();
        permit.run();
        Assertions.assertEquals(0, limiter.getInFlight());
        acquireNow(limiter).run();
        Assertions.assertEquals(1, limiter.getRejectedCount());
    }

    @Test
    void aWaiterGetsThePermitOfTheReleasedExchange() throws Exception {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1, 1, Duration.ofSeconds(10));
        Runnable first = acquireNow(limiter);
        CompletableFuture<ClientConcurrencyLimiter.Permit> second = toFuture(limiter.acquire(scheduler));
        Assertions.assertFalse(second.isDone());
        Assertions.assertEquals(1, limiter.getQueued());
        first.run();
        ClientConcurrencyLimiter.Permit permit = second.get(5, TimeUnit.SECONDS);
        Assertions.assertTrue(permit.take());
        Assertions.assertEquals(1, limiter.getInFlight());
        Assertions.assertEquals(0, limiter.getQueued());
        permit.run();
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void aCancellationAfterThePermitWasTakenKeepsIt() throws Exception {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1, 1, Duration.ZERO);
        Runnable first = acquireNow(limiter);
        ExecutionFlow<ClientConcurrencyLimiter.Permit> flow = limiter.acquire(scheduler);
        CompletableFuture<ClientConcurrencyLimiter.Permit> second = toFuture(flow);
        first.run();
        ClientConcurrencyLimiter.Permit permit = second.get(5, TimeUnit.SECONDS);
        Assertions.assertTrue(permit.take());
        // e.g. a Mono that cancels its source once it emitted
        flow.cancel();
        Assertions.assertEquals(1, limiter.getInFlight());
        permit.run();
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void aWaiterTimesOut() throws Exception {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1, 1, Duration.ofMillis(50));
        Runnable first = acquireNow(limiter);
        CompletableFuture<ClientConcurrencyLimiter.Permit> second = toFuture(limiter.acquire(scheduler));
        Exception e = Assertions.assertThrows(Exception.class, () -> second.get(5, TimeUnit.SECONDS));
        ConcurrencyLimitExceededException limit = Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class, e.getCause());
        Assertions.assertTrue(limit.isTimedOut());
        Assertions.assertEquals(0, limiter.getQueued());
        first.run();
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    @Test
    void aCancelledWaiterLeavesTheQueue() {
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", 1, 1, Duration.ZERO);
        Runnable first = acquireNow(limiter);
        ExecutionFlow<ClientConcurrencyLimiter.Permit> second = limiter.acquire(scheduler);
        second.cancel();
        Assertions.assertEquals(0, limiter.getQueued());
        first.run();
        Assertions.assertEquals(0, limiter.getInFlight());
        acquireNow(limiter).run();
        Assertions.assertEquals(0, limiter.getInFlight());
    }

    /**
     * Many callers acquire, cancel while waiting, time out and release at random, concurrently:
     * the cap is never exceeded, and once all is done no permit is left in flight.
     */
    @Test
    void noPermitLeaksUnderRandomCancellation() throws Exception {
        int max = 3;
        ClientConcurrencyLimiter limiter = new ClientConcurrencyLimiter("svc", max, 8, Duration.ofMillis(20));
        AtomicInteger holding = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        int threads = 12;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    ThreadLocalRandom random = ThreadLocalRandom.current();
                    for (int i = 0; i < 2000; i++) {
                        boolean cancel = random.nextInt(4) == 0;
                        ExecutionFlow<ClientConcurrencyLimiter.Permit> flow = limiter.acquire(scheduler);
                        AtomicReference<Runnable> got = new AtomicReference<>();
                        CountDownLatch done = new CountDownLatch(1);
                        flow.onComplete((permit, failure) -> {
                            // whoever takes a permit owns it and gives it back
                            if (permit != null && permit.take()) {
                                if (cancel) {
                                    permit.run();
                                } else {
                                    int now = holding.incrementAndGet();
                                    maxSeen.accumulateAndGet(now, Math::max);
                                    got.set(permit);
                                }
                            }
                            done.countDown();
                        });
                        if (cancel) {
                            // the caller gives up, maybe while the permit is being granted
                            flow.cancel();
                        } else {
                            done.await(5, TimeUnit.SECONDS);
                        }
                        Runnable permit = got.getAndSet(null);
                        if (permit != null) {
                            holding.decrementAndGet();
                            permit.run();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(120, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        // a permit granted to a cancelled flow is given back by the limiter itself
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while ((limiter.getInFlight() != 0 || limiter.getQueued() != 0) && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        Assertions.assertTrue(maxSeen.get() <= max, "Held " + maxSeen.get() + " permits at once");
        Assertions.assertEquals(0, limiter.getQueued());
        Assertions.assertEquals(0, limiter.getInFlight());
        Assertions.assertEquals(max, limiter.getAvailablePermits());
    }

    private Runnable acquireNow(ClientConcurrencyLimiter limiter) {
        ClientConcurrencyLimiter.Permit permit = limiter.acquire(scheduler).tryCompleteValue();
        Assertions.assertNotNull(permit);
        Assertions.assertTrue(permit.take());
        return permit;
    }

    private ConcurrencyLimitExceededException failure(ExecutionFlow<ClientConcurrencyLimiter.Permit> flow) {
        return Assertions.assertInstanceOf(ConcurrencyLimitExceededException.class, flow.tryCompleteError());
    }

    private static CompletableFuture<ClientConcurrencyLimiter.Permit> toFuture(ExecutionFlow<ClientConcurrencyLimiter.Permit> flow) {
        return flow.toCompletableFuture();
    }
}
