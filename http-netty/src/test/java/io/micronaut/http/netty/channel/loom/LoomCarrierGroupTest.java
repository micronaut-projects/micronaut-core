package io.micronaut.http.netty.channel.loom;

import io.netty.channel.nio.NioIoHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ThreadPerTaskExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class LoomCarrierGroupTest {
    private LoomCarrierGroup group;
    private LoomCarrierGroup.Runner runner;

    @BeforeEach
    void setUp() {
        assumeTrue(PrivateLoomSupport.isSupported() || LoomBranchSupport.isSupported(),
            "Loom internals are not accessible, run with --add-opens=java.base/java.lang=ALL-UNNAMED");
        group = createGroup();
        runner = group.runners.get(0);
    }

    private static LoomCarrierGroup createGroup() {
        LoomCarrierConfiguration configuration = new LoomCarrierConfiguration(
            Duration.ofNanos(1), // one continuation per carrier loop iteration
            Duration.ofNanos(1),
            Duration.ofMillis(1),
            Duration.ofMillis(5),
            Duration.ofSeconds(1),
            10,
            Integer.MAX_VALUE, // no work spilling, there is only one runner anyway
            0 // no warmup, every thread goes straight to the runner
        );
        LoomCarrierGroup.Factory factory = new LoomCarrierGroup.Factory(new EventLoopLoomFactory(), configuration);
        return (LoomCarrierGroup) factory.create(1, new ThreadPerTaskExecutor(new DefaultThreadFactory("loom-carrier-test")), NioIoHandler.newFactory());
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        if (group != null) {
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            assertTrue(group.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void threadCarriedByRunnerIsDetected() throws Exception {
        CompletableFuture<Boolean> onRunner = new CompletableFuture<>();
        CompletableFuture<Void> writeExecuted = new CompletableFuture<>();
        Thread thread = runner.newThread(() -> {
            try {
                onRunner.complete(runner.isOnRunner(Thread.currentThread()));
                // submitting an event loop task from a carried thread takes the expedite path
                // and yields to the carrier. The task must still be executed.
                runner.eventLoop().execute(() -> writeExecuted.complete(null));
            } catch (Throwable t) {
                onRunner.completeExceptionally(t);
                writeExecuted.completeExceptionally(t);
            }
        });
        thread.start();

        assertTrue(onRunner.get(10, TimeUnit.SECONDS));
        writeExecuted.get(10, TimeUnit.SECONDS);
        thread.join(10_000);
        assertFalse(thread.isAlive());

        assertFalse(runner.isOnRunner(Thread.currentThread()));
        CompletableFuture<Boolean> plainVirtual = new CompletableFuture<>();
        Thread.ofVirtual().start(() -> plainVirtual.complete(runner.isOnRunner(Thread.currentThread())));
        assertFalse(plainVirtual.get(10, TimeUnit.SECONDS));
    }

    @Test
    void queuedContinuationsRunAfterShutdown() throws Exception {
        // occupy the carrier so that further continuations queue up behind this one
        CountDownLatch blockerStarted = new CountDownLatch(1);
        AtomicBoolean release = new AtomicBoolean();
        Thread blocker = runner.newThread(() -> {
            blockerStarted.countDown();
            while (!release.get()) {
                Thread.onSpinWait();
            }
        });
        blocker.start();
        assertTrue(blockerStarted.await(10, TimeUnit.SECONDS));

        int n = 20;
        CountDownLatch completed = new CountDownLatch(n);
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Thread thread = runner.newThread(completed::countDown);
            thread.start();
            threads.add(thread);
        }

        group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
        release.set(true);
        assertTrue(group.awaitTermination(10, TimeUnit.SECONDS));

        // the carrier loop runs at most one continuation per iteration and exits as soon as the
        // event loop is terminated, so most of the queued threads have not run on it. They must
        // be handed over to the default scheduler instead of hanging forever.
        assertTrue(completed.await(10, TimeUnit.SECONDS), "queued virtual threads never ran after shutdown");
        for (Thread thread : threads) {
            thread.join(10_000);
            assertFalse(thread.isAlive());
        }
        blocker.join(10_000);
        assertFalse(blocker.isAlive());
    }

    @Test
    void continuationsSubmittedDuringTerminationRun() throws Exception {
        // The group created by setUp is not used here, every iteration gets a fresh one so
        // that the submissions race against the final drain of the carrier.
        int iterations = 30;
        int producers = 4;
        for (int i = 0; i < iterations; i++) {
            LoomCarrierGroup raceGroup = createGroup();
            LoomCarrierGroup.Runner raceRunner = raceGroup.runners.get(0);
            List<Thread> submitted = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch producersStarted = new CountDownLatch(producers);
            List<Thread> producerThreads = new ArrayList<>();
            for (int p = 0; p < producers; p++) {
                Thread producer = new Thread(() -> {
                    producersStarted.countDown();
                    // keep submitting until the loop is terminated, so some submissions land
                    // before, some during and some after the carrier exits
                    while (!raceGroup.isTerminated()) {
                        Thread thread = raceRunner.newThread(() -> { });
                        thread.start();
                        submitted.add(thread);
                        Thread.onSpinWait();
                    }
                }, "loom-carrier-test-producer-" + p);
                producer.start();
                producerThreads.add(producer);
            }
            assertTrue(producersStarted.await(10, TimeUnit.SECONDS));
            Thread.sleep(2);
            raceGroup.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            assertTrue(raceGroup.awaitTermination(10, TimeUnit.SECONDS));
            for (Thread producer : producerThreads) {
                producer.join(10_000);
                assertFalse(producer.isAlive());
            }
            assertFalse(submitted.isEmpty());
            for (Thread thread : submitted) {
                thread.join(10_000);
                assertFalse(thread.isAlive(), "virtual thread submitted around termination never ran in iteration " + i);
            }
        }
    }
}
