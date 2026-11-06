package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonPoolShutdownTest {

    @Test
    void contextCloseWaitsForInFlightPooledExecution() throws Exception {
        ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ));
        PythonPool pool = applicationContext.getBean(PythonPool.class);
        CountDownLatch entered = new CountDownLatch(1);
        Semaphore gate = new Semaphore(0);
        AtomicReference<Object> outcome = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                outcome.set(pool.withContext(ctx -> {
                    ctx.eval(PYTHON, "1");
                    entered.countDown();
                    gate.acquireUninterruptibly();
                    return ctx.eval(PYTHON, "2").asInt();
                }));
            } catch (Throwable e) {
                outcome.set(e);
            }
        });
        worker.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        Thread closer = new Thread(applicationContext::close);
        closer.start();
        closer.join(1000);
        gate.release();
        worker.join(10000);
        closer.join(10000);

        assertEquals(2, outcome.get(), "pooled execution must complete before its context is closed");
        assertFalse(closer.isAlive());
    }

    @Test
    void gracefulShutdownKeepsServingBorrowsAndCompletesWhenIdle() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            CountDownLatch entered = new CountDownLatch(1);
            Semaphore gate = new Semaphore(0);
            AtomicReference<Object> outcome = new AtomicReference<>();
            Thread worker = new Thread(() -> {
                try {
                    outcome.set(pool.withContext(ctx -> {
                        entered.countDown();
                        gate.acquireUninterruptibly();
                        return ctx.eval(PYTHON, "3").asInt();
                    }));
                } catch (Throwable e) {
                    outcome.set(e);
                }
            });
            worker.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            CompletionStage<?> shutdown = pool.shutdownGracefully();
            assertFalse(shutdown.toCompletableFuture().isDone(), "graceful shutdown must wait for pooled work");
            gate.release();
            worker.join(10000);
            assertEquals(3, outcome.get());
            shutdown.toCompletableFuture().get(5, TimeUnit.SECONDS);

            // Requests still draining may need a context after the pool reported idle.
            Integer late = pool.<Integer>withContext(ctx -> ctx.eval(PYTHON, "4").asInt());
            assertEquals(4, late.intValue());
        }
    }

    @Test
    void reusablePrimaryContextSurvivesApplicationClose() {
        PythonContextRuntime.setReuseContext(true);
        Context primary = null;
        try {
            try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
                "micronaut.python.pool.enabled", true,
                "micronaut.python.pool.size", 1
            ))) {
                primary = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
                assertEquals(1, primary.eval(PYTHON, "1").asInt());
            }
            assertEquals(1, primary.eval(PYTHON, "1").asInt(),
                "closing a Micronaut context must not close a reusable GraalPy context");
        } finally {
            PythonContextRuntime.setReuseContext(false);
            PythonContextRuntime.resetContext();
            if (primary != null) {
                GraalPyContextFactory.closeContext(primary);
            }
        }
    }
}
