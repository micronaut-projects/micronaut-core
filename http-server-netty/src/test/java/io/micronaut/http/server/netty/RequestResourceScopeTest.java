package io.micronaut.http.server.netty;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestResourceScopeTest {

    @Test
    void releasesInRegistrationOrder() {
        RequestResourceScope scope = new RequestResourceScope();
        List<String> released = new ArrayList<>();
        scope.add(() -> released.add("a"));
        scope.add(() -> released.add("b"));
        scope.add(() -> released.add("c"));

        assertTrue(released.isEmpty());
        scope.release();

        assertEquals(List.of("a", "b", "c"), released);
    }

    @Test
    void lateRegistrationIsReleasedImmediately() {
        RequestResourceScope scope = new RequestResourceScope();
        scope.release();

        AtomicInteger released = new AtomicInteger();
        scope.add(released::incrementAndGet);
        assertEquals(1, released.get());

        scope.release();
        assertEquals(1, released.get(), "a late resource must not be released again");
    }

    @Test
    void lateRegistrationFailurePropagatesToTheRegistrant() {
        RequestResourceScope scope = new RequestResourceScope();
        scope.release();

        IllegalStateException failure = new IllegalStateException("late");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> scope.add(() -> {
            throw failure;
        })));
    }

    @Test
    void throwingCleanupInTheMiddleDoesNotSkipTheOthers() {
        RequestResourceScope scope = new RequestResourceScope();
        List<String> released = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        scope.add(() -> released.add("a"));
        scope.add(() -> {
            released.add("b");
            throw first;
        });
        scope.add(() -> released.add("c"));
        scope.add(() -> {
            released.add("d");
            throw second;
        });
        scope.add(() -> released.add("e"));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, scope::release);

        assertSame(first, thrown);
        assertArrayEquals(new Throwable[]{second}, thrown.getSuppressed());
        assertEquals(List.of("a", "b", "c", "d", "e"), released);
    }

    @Test
    void earlierFailureIsRethrownWithCleanupFailuresSuppressed() {
        RequestResourceScope scope = new RequestResourceScope();
        AtomicInteger released = new AtomicInteger();
        IllegalStateException cleanupFailure = new IllegalStateException("cleanup");
        scope.add(() -> {
            released.incrementAndGet();
            throw cleanupFailure;
        });
        scope.add(released::incrementAndGet);

        IllegalArgumentException earlier = new IllegalArgumentException("earlier");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> scope.release(earlier));

        assertSame(earlier, thrown);
        assertArrayEquals(new Throwable[]{cleanupFailure}, thrown.getSuppressed());
        assertEquals(2, released.get());
    }

    @Test
    void doubleReleaseRunsEachCleanupOnce() {
        RequestResourceScope scope = new RequestResourceScope();
        AtomicInteger released = new AtomicInteger();
        scope.add(released::incrementAndGet);
        scope.add(released::incrementAndGet);

        scope.release();
        scope.release();

        assertEquals(2, released.get());
    }

    @Test
    void doubleReleaseDoesNotRethrowAnEarlierCleanupFailure() {
        RequestResourceScope scope = new RequestResourceScope();
        scope.add(() -> {
            throw new IllegalStateException("cleanup");
        });

        assertThrows(IllegalStateException.class, scope::release);
        scope.release();
    }

    @Test
    void concurrentRegistrationAndReleaseReleaseEveryResourceExactlyOnce() throws Exception {
        int registrants = 4;
        int perRegistrant = 200;
        int rounds = 2_000;
        ExecutorService pool = Executors.newFixedThreadPool(registrants + 1);
        try {
            for (int round = 0; round < rounds; round++) {
                RequestResourceScope scope = new RequestResourceScope();
                AtomicInteger[] counts = new AtomicInteger[registrants * perRegistrant];
                for (int i = 0; i < counts.length; i++) {
                    counts[i] = new AtomicInteger();
                }
                CyclicBarrier barrier = new CyclicBarrier(registrants + 1);
                List<Future<?>> futures = new ArrayList<>();
                for (int r = 0; r < registrants; r++) {
                    int offset = r * perRegistrant;
                    futures.add(pool.submit(() -> {
                        barrier.await();
                        for (int i = 0; i < perRegistrant; i++) {
                            scope.add(counts[offset + i]::incrementAndGet);
                        }
                        return null;
                    }));
                }
                futures.add(pool.submit(() -> {
                    barrier.await();
                    scope.release();
                    return null;
                }));
                for (Future<?> future : futures) {
                    future.get();
                }
                for (int i = 0; i < counts.length; i++) {
                    assertEquals(1, counts[i].get(), "resource " + i + " in round " + round);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
