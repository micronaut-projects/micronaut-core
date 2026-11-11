package io.micronaut.http.netty.channel.loom;

import io.micronaut.http.netty.channel.DefaultEventLoopGroupConfiguration;
import io.micronaut.http.netty.channel.EventLoopGroupConfiguration;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.executor.ExecutorType;
import io.micronaut.scheduling.executor.UserExecutorConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLoopLoomFactoryTest {
    private static final EventLoopGroupConfiguration LOOM_CARRIER = new DefaultEventLoopGroupConfiguration(
        "default", 1, 1, null, false, null, null, null, null, true);
    private static final EventLoopGroupConfiguration NO_LOOM_CARRIER = new DefaultEventLoopGroupConfiguration();

    private static UserExecutorConfiguration virtualConfiguration() {
        UserExecutorConfiguration configuration = UserExecutorConfiguration.of(TaskExecutors.VIRTUAL, ExecutorType.THREAD_PER_TASK);
        configuration.setVirtual(true);
        return configuration;
    }

    @Test
    void rejectsNonVirtualConfiguration() {
        EventLoopLoomFactory holder = new EventLoopLoomFactory();
        UserExecutorConfiguration configuration = UserExecutorConfiguration.of(TaskExecutors.VIRTUAL, ExecutorType.THREAD_PER_TASK);
        assertFalse(configuration.isVirtual());
        assertThrows(IllegalStateException.class, () -> holder.eventLoopGroupThreadFactory(configuration, List.of(LOOM_CARRIER)));
    }

    @Test
    void plainFactoryWithoutLoomCarrierGroup() {
        EventLoopLoomFactory holder = new EventLoopLoomFactory();
        ThreadFactory factory = holder.eventLoopGroupThreadFactory(virtualConfiguration(), List.of(NO_LOOM_CARRIER));
        AtomicInteger customCalls = new AtomicInteger();
        ThreadFactory custom = r -> {
            customCalls.incrementAndGet();
            return Thread.ofVirtual().name("custom").unstarted(r);
        };
        holder.targetScheduler.set(custom);
        try {
            // the plain factory never consults the target scheduler
            Thread thread = factory.newThread(() -> { });
            assertTrue(thread.isVirtual());
            assertTrue(thread.getName().startsWith("virtual-executor-"), thread.getName());
            assertEquals(0, customCalls.get());
        } finally {
            holder.targetScheduler.remove();
        }
    }

    @Test
    void eventLoopAwareFactoryWithLoomCarrierGroup() {
        EventLoopLoomFactory holder = new EventLoopLoomFactory();
        ThreadFactory factory = holder.eventLoopGroupThreadFactory(virtualConfiguration(), List.of(NO_LOOM_CARRIER, LOOM_CARRIER));

        // no target scheduler on this (non-netty) thread: fall back to the plain virtual factory
        Thread plain = factory.newThread(() -> { });
        assertTrue(plain.isVirtual());
        assertTrue(plain.getName().startsWith("virtual-executor-"), plain.getName());

        Thread expected = Thread.ofVirtual().name("custom").unstarted(() -> { });
        AtomicInteger customCalls = new AtomicInteger();
        ThreadFactory custom = r -> {
            customCalls.incrementAndGet();
            return expected;
        };
        holder.targetScheduler.set(custom);
        try {
            assertSame(expected, factory.newThread(() -> { }));
            assertEquals(1, customCalls.get());
        } finally {
            holder.targetScheduler.remove();
        }
        assertTrue(factory.newThread(() -> { }).getName().startsWith("virtual-executor-"));
        assertEquals(1, customCalls.get());
    }
}
