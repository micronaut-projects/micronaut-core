package io.micronaut.scheduling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

@SuppressWarnings({"removal", "deprecation"})
class LoomSupportTest {

    @Test
    void virtualThreadsAreAlwaysSupported() {
        assertTrue(LoomSupport.isSupported());
        assertTrue(new LoomSupport.LoomCondition().matches(null));
        LoomSupport.checkSupported();
    }

    @Test
    void createsAnUnstartedVirtualThread() throws InterruptedException {
        AtomicReference<String> ran = new AtomicReference<>();
        Thread thread = LoomSupport.unstarted("test-thread", null, () -> ran.set(Thread.currentThread().getName()));

        assertEquals("test-thread", thread.getName());
        assertTrue(thread.isVirtual());
        assertTrue(LoomSupport.isVirtual(thread));
        assertFalse(LoomSupport.isVirtual(Thread.currentThread()));

        thread.start();
        thread.join();
        assertEquals("test-thread", ran.get());
    }

    @Test
    void appliesTheBuilderModifier() {
        AtomicReference<Object> seen = new AtomicReference<>();
        Thread thread = LoomSupport.unstarted("modified", seen::set, () -> { });

        assertTrue(seen.get() instanceof Thread.Builder.OfVirtual);
        assertEquals("modified", thread.getName());
    }

    @Test
    void namesTheThreadsOfAFactoryWithACounter() {
        ThreadFactory factory = LoomSupport.newVirtualThreadFactory("counted-");

        assertEquals("counted-1", factory.newThread(() -> { }).getName());
        assertEquals("counted-2", factory.newThread(() -> { }).getName());
    }

    @Test
    void runsATaskPerThreadOnTheExecutor() throws Exception {
        ThreadFactory factory = LoomSupport.newVirtualThreadFactory("executor-", builder -> { });
        try (ExecutorService executor = LoomSupport.newThreadPerTaskExecutor(factory)) {
            assertTrue(executor.submit(() -> Thread.currentThread().isVirtual()).get());
        }
    }
}
