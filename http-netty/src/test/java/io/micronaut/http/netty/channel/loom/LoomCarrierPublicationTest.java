package io.micronaut.http.netty.channel.loom;

import io.netty.channel.nio.NioIoHandler;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LoomCarrierPublicationTest {
    @Test
    void deferredExecutorDoesNotBlockConstructionOrSchedulingAndWarmupIsShared() throws Exception {
        assumeTrue(PrivateLoomSupport.isSupported() || LoomBranchSupport.isSupported());
        LoomCarrierConfiguration configuration = new LoomCarrierConfiguration(
            Duration.ofMillis(1), Duration.ofMillis(5), Duration.ofMillis(1),
            Duration.ofMillis(5), Duration.ofSeconds(1), 10, Integer.MAX_VALUE, 8
        );
        LoomCarrierGroup.Factory factory = new LoomCarrierGroup.Factory(new EventLoopLoomFactory(), configuration);
        List<Runnable> carriers = new CopyOnWriteArrayList<>();
        CountDownLatch submitted = new CountDownLatch(2);
        CompletableFuture<LoomCarrierGroup> created = CompletableFuture.supplyAsync(() ->
            (LoomCarrierGroup) factory.create(2, task -> {
                carriers.add(task);
                submitted.countDown();
            }, NioIoHandler.newFactory()));
        LoomCarrierGroup group = null;
        boolean carriersStarted = false;
        try {
            assertTrue(submitted.await(10, TimeUnit.SECONDS));
            group = created.get(2, TimeUnit.SECONDS);
            LoomCarrierGroup.Runner runner = group.runners.getFirst();
            CountDownLatch queued = new CountDownLatch(1);
            CompletableFuture.runAsync(() -> runner.execute(queued::countDown)).get(2, TimeUnit.SECONDS);
            carriers.forEach(task -> Thread.ofPlatform().daemon().start(task));
            carriersStarted = true;
            assertTrue(queued.await(10, TimeUnit.SECONDS));
            CountDownLatch finished = new CountDownLatch(32);
            List<CompletableFuture<Thread>> threads = new ArrayList<>();
            for (int i = 0; i < 32; i++) {
                threads.add(CompletableFuture.supplyAsync(() -> runner.newThread(finished::countDown)));
            }
            int defaultScheduler = 0;
            for (CompletableFuture<Thread> future : threads) {
                Thread thread = future.get(10, TimeUnit.SECONDS);
                if (!LoomBranchSupport.isSupported() && PrivateLoomSupport.getScheduler(thread) == PrivateLoomSupport.getDefaultScheduler()) {
                    defaultScheduler++;
                }
                thread.start();
            }
            assertEquals(0, runner.warmupTasks.get());
            if (!LoomBranchSupport.isSupported()) {
                assertEquals(8, defaultScheduler);
            }
            assertTrue(finished.await(10, TimeUnit.SECONDS));
        } finally {
            // Release deferred carriers even when a constructor or scheduling assertion times out.
            if (!carriersStarted) {
                carriers.forEach(task -> Thread.ofPlatform().daemon().start(task));
            }
            if (group == null) {
                group = created.get(10, TimeUnit.SECONDS);
            }
            if (group != null) {
                group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).await(10, TimeUnit.SECONDS);
            }
        }
    }
}
