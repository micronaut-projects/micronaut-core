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
package io.micronaut.context.python;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonPoolPrestartTest {

    private static final String PRESTART = PythonPoolConfiguration.PREFIX + ".prestart";
    private static final String SIZE = PythonPoolConfiguration.PREFIX + ".size";

    @Test
    void prestartsOneContextByDefaultExceptInTests() {
        assertEquals(1, PythonPool.prestartCount(null, 4, false));
        assertEquals(0, PythonPool.prestartCount(null, 4, true));
        // configured, it applies in tests too
        assertEquals(2, PythonPool.prestartCount(2, 4, true));
        assertEquals(0, PythonPool.prestartCount(0, 4, false));
        // never more than the pool holds, and nothing without a pool
        assertEquals(4, PythonPool.prestartCount(10, 4, false));
        assertEquals(0, PythonPool.prestartCount(null, 0, false));
        assertEquals(0, PythonPool.prestartCount(-1, 4, false));
    }

    @Test
    void bindsPrestart() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(PRESTART, 0))) {
            assertEquals(0, context.getBean(PythonPoolConfiguration.class).prestart());
        }
        try (ApplicationContext context = ApplicationContext.run()) {
            assertNull(context.getBean(PythonPoolConfiguration.class).prestart());
        }
    }

    @Test
    void nothingIsPrestartedInTheTestEnvironmentUnlessConfigured() {
        try (ApplicationContext context = ApplicationContext.run(Map.of(SIZE, 2))) {
            PythonPool pool = context.getBean(PythonPool.class);
            assertNull(pool.prestartThread());
            assertEquals(0, pool.pooledContextCount());
        }
    }

    @Test
    void prestartedContextServesTheFirstBorrowWithoutWaiting() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(SIZE, 3, PRESTART, 2))) {
            PythonPool pool = context.getBean(PythonPool.class);
            Thread prestart = pool.prestartThread();
            assertNotNull(prestart);
            prestart.join(Duration.ofSeconds(120));
            assertFalse(prestart.isAlive());

            assertEquals(2, pool.pooledContextCount());
            assertEquals(2, pool.availableContextCount());
            Context borrowed = pool.borrow();
            try {
                // warmed up as well: the runtime module every bridge call needs is imported already
                assertNotNull(PythonContextRegistry.state(borrowed).runtimeModule.get());
            } finally {
                pool.release(borrowed);
            }
            assertEquals(2, pool.<Integer>withContext(ctx -> ctx.eval(PYTHON, "1 + 1").asInt()));
            // the borrow found a context ready: none was created for it and it did not wait
            assertEquals(2, pool.pooledContextCount());
            assertEquals(0, pool.statistics().waits());
        }
    }

    @Test
    void borrowDuringPrestartWaitsForTheContextBeingBuilt() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        BlockingGraalPyContextCustomizer.Gate gate = BlockingGraalPyContextCustomizer.blockNextContextOn(PythonPool.PRESTART_THREAD_NAME);
        try (ApplicationContext context = ApplicationContext.run(Map.of(SIZE, 2, PRESTART, 1))) {
            PythonPool pool = context.getBean(PythonPool.class);
            assertTrue(gate.entered.await(120, TimeUnit.SECONDS), "the pre-start must build a context");

            Future<Integer> request = executor.submit(() -> pool.<Integer>withContext(ctx -> ctx.eval(PYTHON, "40 + 2").asInt()));
            // the request does not build a second context next to the one in flight: it waits for it
            assertThrows(TimeoutException.class, () -> request.get(500, TimeUnit.MILLISECONDS));
            assertEquals(0, pool.pooledContextCount());

            gate.proceed.countDown();
            assertEquals(42, request.get(120, TimeUnit.SECONDS));
            assertEquals(1, pool.pooledContextCount(), "the request must borrow the pre-started context");
            assertEquals(1, pool.statistics().waits());
            pool.prestartThread().join(Duration.ofSeconds(120));
            assertEquals(1, pool.pooledContextCount());
        } finally {
            gate.proceed.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void contextCreatedOnDemandCountsTowardsPrestart() throws Exception {
        BlockingGraalPyContextCustomizer.Gate gate = BlockingGraalPyContextCustomizer.blockNextContextOn(PythonPool.PRESTART_THREAD_NAME);
        try (ApplicationContext context = ApplicationContext.run(Map.of(SIZE, 2, PRESTART, 1))) {
            PythonPool pool = context.getBean(PythonPool.class);
            assertTrue(gate.entered.await(120, TimeUnit.SECONDS));
            gate.proceed.countDown();
            pool.prestartThread().join(Duration.ofSeconds(120));
            // the pre-start stops at the configured count, however busy the pool gets later
            Context first = pool.borrow();
            Context second = pool.borrow();
            pool.release(first);
            pool.release(second);
            assertEquals(2, pool.pooledContextCount());
        } finally {
            gate.proceed.countDown();
        }
    }

    @Test
    void shutdownDuringPrestartClosesTheContextBeingBuiltBeforeTheEngine() throws Exception {
        BlockingGraalPyContextCustomizer.Gate gate = BlockingGraalPyContextCustomizer.blockNextContextOn(PythonPool.PRESTART_THREAD_NAME);
        ApplicationContext context = ApplicationContext.run(Map.of(SIZE, 2, PRESTART, 2));
        Thread closer = null;
        try {
            PythonPool pool = context.getBean(PythonPool.class);
            Engine engine = context.getBean(Engine.class, Qualifiers.byName(PYTHON));
            assertTrue(gate.entered.await(120, TimeUnit.SECONDS));
            Thread prestart = pool.prestartThread();

            closer = new Thread(context::close);
            closer.start();
            closer.join(Duration.ofSeconds(120));
            assertFalse(closer.isAlive(), "closing the application must not wait for the pre-start");

            // the gate the engine closes on: it must wait for the context still being built
            CountDownLatch noContexts = new CountDownLatch(1);
            PythonContextRegistry.onNoContexts(engine, noContexts::countDown);
            assertFalse(noContexts.await(500, TimeUnit.MILLISECONDS), "the engine must outlive the context being built");

            gate.proceed.countDown();
            prestart.join(Duration.ofSeconds(120));
            assertFalse(prestart.isAlive(), "the pre-start must stop at shutdown");
            assertEquals(0, pool.pooledContextCount(), "no context is pre-started after shutdown");
            assertTrue(noContexts.await(120, TimeUnit.SECONDS), "the context built during shutdown must be closed");
        } finally {
            gate.proceed.countDown();
            if (closer == null) {
                context.close();
            }
        }
    }

    @Test
    void engineGateWaitsForContextsBeingBuilt() {
        try (Engine engine = Engine.newBuilder(PYTHON).build()) {
            AtomicInteger runs = new AtomicInteger();
            PythonContextRegistry.beginContextBuild(engine);
            PythonContextRegistry.onNoContexts(engine, runs::incrementAndGet);
            assertEquals(0, runs.get(), "no context is registered yet, but one is being built");

            Context context = Context.newBuilder(PYTHON).engine(engine).build();
            PythonContextRegistry.registerContext(context);
            PythonContextRegistry.endContextBuild(engine);
            assertEquals(0, runs.get(), "the built context is registered: the gate waits for it");

            PythonContextRegistry.unregisterContext(context);
            context.close();
            assertEquals(1, runs.get());

            // a failed build releases the gate too
            PythonContextRegistry.beginContextBuild(engine);
            PythonContextRegistry.onNoContexts(engine, runs::incrementAndGet);
            assertEquals(1, runs.get());
            PythonContextRegistry.endContextBuild(engine);
            assertEquals(2, runs.get());
        }
    }
}
