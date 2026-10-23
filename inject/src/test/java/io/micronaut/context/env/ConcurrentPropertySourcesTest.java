package io.micronaut.context.env;

import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrentPropertySourcesTest {

    @Test
    void additionDuringRefreshIsPublished() throws Exception {
        BlockingSource gate = new BlockingSource();
        ApplicationContext context = newContext(gate);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Environment environment = context.getEnvironment();
            environment.start();
            gate.arm(false);

            Future<?> refresh = executor.submit(() -> environment.refresh());
            assertTrue(gate.awaitEntry(), "refresh did not reach the blocking source");

            CountDownLatch addStarted = new CountDownLatch(1);
            CountDownLatch addCompleted = new CountDownLatch(1);
            Future<?> addition = executor.submit(() -> {
                addStarted.countDown();
                environment.addPropertySource(PropertySource.of("late", Map.of("late.key", "late")));
                addCompleted.countDown();
            });
            assertTrue(addStarted.await(5, TimeUnit.SECONDS));
            assertFalse(addCompleted.await(100, TimeUnit.MILLISECONDS), "addition must wait for the refresh");

            gate.release();
            refresh.get(5, TimeUnit.SECONDS);
            addition.get(5, TimeUnit.SECONDS);

            assertEquals("late", environment.getProperty("late.key", String.class).orElseThrow());
            assertTrue(sourceNames(environment).contains("late"));
        } finally {
            gate.release();
            executor.shutdownNow();
            context.close();
        }
    }

    @Test
    void additionDuringStartIsProcessed() throws Exception {
        BlockingSource gate = new BlockingSource();
        ApplicationContext context = newContext(gate);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Environment environment = context.getEnvironment();
            gate.arm(false);

            Future<?> starter = executor.submit(() -> environment.start());
            assertTrue(gate.awaitEntry(), "start did not reach the blocking source");

            CountDownLatch addStarted = new CountDownLatch(1);
            CountDownLatch addCompleted = new CountDownLatch(1);
            Future<?> addition = executor.submit(() -> {
                addStarted.countDown();
                environment.addPropertySource(PropertySource.of("late", Map.of("late.key", "late")));
                addCompleted.countDown();
            });
            assertTrue(addStarted.await(5, TimeUnit.SECONDS));
            assertFalse(addCompleted.await(100, TimeUnit.MILLISECONDS), "addition must wait for start");

            gate.release();
            starter.get(5, TimeUnit.SECONDS);
            addition.get(5, TimeUnit.SECONDS);

            assertEquals("late", environment.getProperty("late.key", String.class).orElseThrow());
        } finally {
            gate.release();
            executor.shutdownNow();
            context.close();
        }
    }

    @Test
    void changesDuringFailedRefreshAreRetained() throws Exception {
        BlockingSource gate = new BlockingSource();
        PropertySource original = PropertySource.of("original", Map.of("original.key", "original"));
        ApplicationContext context = ApplicationContext.builder()
            .deduceEnvironment(false)
            .enableDefaultPropertySources(false)
            .propertySources(original, gate)
            .build();
        ExecutorService executor = Executors.newFixedThreadPool(3);
        try {
            Environment environment = context.getEnvironment();
            environment.start();
            gate.arm(true);

            Future<?> refresh = executor.submit(() -> environment.refresh());
            assertTrue(gate.awaitEntry(), "refresh did not reach the blocking source");

            CountDownLatch changeStarted = new CountDownLatch(2);
            CountDownLatch changesCompleted = new CountDownLatch(2);
            Future<?> addition = executor.submit(() -> {
                changeStarted.countDown();
                environment.addPropertySource(PropertySource.of("late", Map.of("late.key", "late")));
                changesCompleted.countDown();
            });
            Future<?> removal = executor.submit(() -> {
                changeStarted.countDown();
                environment.removePropertySource(original);
                changesCompleted.countDown();
            });
            assertTrue(changeStarted.await(5, TimeUnit.SECONDS));
            assertFalse(changesCompleted.await(100, TimeUnit.MILLISECONDS), "changes must wait for refresh");

            gate.release();
            ExecutionException failure = assertThrows(ExecutionException.class, () -> refresh.get(5, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            addition.get(5, TimeUnit.SECONDS);
            removal.get(5, TimeUnit.SECONDS);

            Set<String> names = sourceNames(environment);
            assertTrue(names.contains("late"));
            assertFalse(names.contains("original"));
            assertEquals("late", environment.getProperty("late.key", String.class).orElseThrow());

            // A failed refresh must not leave the reading flag stuck or lose registry changes.
            environment.refresh();
            assertEquals("late", environment.getProperty("late.key", String.class).orElseThrow());
            assertFalse(sourceNames(environment).contains("original"));
        } finally {
            gate.release();
            executor.shutdownNow();
            context.close();
        }
    }

    private static ApplicationContext newContext(PropertySource gate) {
        return ApplicationContext.builder()
            .deduceEnvironment(false)
            .enableDefaultPropertySources(false)
            .propertySources(PropertySource.of("original", Map.of("original.key", "original")), gate)
            .build();
    }

    private static Set<String> sourceNames(Environment environment) {
        return environment.getPropertySources().stream().map(PropertySource::getName).collect(Collectors.toSet());
    }

    private static final class BlockingSource implements PropertySource {
        private final AtomicBoolean armed = new AtomicBoolean();
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean fail;

        void arm(boolean fail) {
            this.fail = fail;
            armed.set(true);
        }

        boolean awaitEntry() throws InterruptedException {
            return entered.await(5, TimeUnit.SECONDS);
        }

        void release() {
            released.countDown();
        }

        @Override
        public String getName() {
            return "gate";
        }

        @Override
        public int getOrder() {
            return LOWEST_PRECEDENCE;
        }

        @Override
        public Object get(String key) {
            return null;
        }

        @Override
        public Iterator<String> iterator() {
            if (armed.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    if (!released.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("timed out waiting for test to release the gate");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting for test", e);
                }
                if (fail) {
                    throw new IllegalStateException("intentional failure while reading property source");
                }
            }
            return Collections.emptyIterator();
        }
    }
}
