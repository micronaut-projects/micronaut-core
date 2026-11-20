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
package io.micronaut.http.netty.channel.loom;

import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The FIFO fallback of the carrier must engage, so the oldest queued continuation
 * waits until the whole backlog has run.
 */
class LoomCarrierFifoFallbackTest {

    @Test
    void oldestContinuationRunsWithinBoundedTimeUnderBacklog() throws Exception {
        assumeTrue(PrivateLoomSupport.isSupported() || LoomBranchSupport.isSupported());
        LoomCarrierConfiguration configuration = new LoomCarrierConfiguration(
            Duration.ofMillis(5),
            Duration.ofMillis(5),
            Duration.ofMillis(1), // fifoSwitchTime
            Duration.ofMillis(5), // taskFifoThreshold
            Duration.ofSeconds(1),
            10,
            Integer.MAX_VALUE,
            Duration.ofMillis(5), // workSpillMinQueueAge
            0
        );
        LoomCarrierGroup.Factory factory = new LoomCarrierGroup.Factory(new EventLoopLoomFactory(), configuration);
        LoomCarrierGroup group = (LoomCarrierGroup) factory.create(1, new ThreadPerTaskExecutor(new DefaultThreadFactory("fifo-fallback")), NioIoHandler.newFactory());
        try {
            LoomCarrierGroup.Runner runner = group.runners.get(0);
            CountDownLatch blockerStarted = new CountDownLatch(1);
            AtomicBoolean release = new AtomicBoolean();
            runner.newThread(() -> {
                blockerStarted.countDown();
                while (!release.get()) {
                    Thread.onSpinWait();
                }
            }).start();
            assertTrue(blockerStarted.await(10, TimeUnit.SECONDS));

            int attackers = 200;
            AtomicInteger attackersDone = new AtomicInteger();
            AtomicInteger seenByVictim = new AtomicInteger(-1);
            CountDownLatch all = new CountDownLatch(attackers + 1);
            runner.newThread(() -> {
                seenByVictim.set(attackersDone.get());
                all.countDown();
            }).start();
            for (int i = 0; i < attackers; i++) {
                runner.newThread(() -> {
                    long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(1);
                    while (System.nanoTime() < end) {
                        Thread.onSpinWait();
                    }
                    attackersDone.incrementAndGet();
                    all.countDown();
                }).start();
            }
            release.set(true);
            assertTrue(all.await(30, TimeUnit.SECONDS));
            // with a working FIFO fallback the victim runs after about taskFifoThreshold (5 tasks of 1ms)
            assertTrue(seenByVictim.get() < attackers / 2,
                "victim continuation ran only after " + seenByVictim.get() + "/" + attackers + " later continuations");
        } finally {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            group.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
