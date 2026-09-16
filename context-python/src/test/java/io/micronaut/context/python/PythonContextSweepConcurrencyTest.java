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

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A context seen for the first time sweeps the states of closed contexts. The sweep must not enter
 * contexts that other threads are still initializing or closing: a context entered from the
 * sweeping thread during its initialization evaluated the runtime helpers into a main module its
 * bindings did not expose, and the helper lookup failed with a {@link NullPointerException}.
 */
final class PythonContextSweepConcurrencyTest {

    private static final int THREADS = 8;
    private static final int CONTEXTS = 48;

    @Test
    void registeredContextsStartingConcurrentlyResolveHelpers() throws Exception {
        assertHelpersResolveConcurrently(true);
    }

    @Test
    void unregisteredContextsStartingConcurrentlyResolveHelpers() throws Exception {
        assertHelpersResolveConcurrently(false);
    }

    private static void assertHelpersResolveConcurrently(boolean register) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try (Engine engine = Engine.newBuilder(PYTHON).build()) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < CONTEXTS; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    Context context = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
                    try {
                        if (register) {
                            PythonContextRegistry.registerContext(context);
                        }
                        PythonContextRuntime.helper(context, "__micronaut_import_module");
                        PythonContextRuntime.helper(context, "__micronaut_inspect_isclass");
                    } finally {
                        PythonContextRegistry.unregisterContext(context);
                        context.close();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(2, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
        }
        assertEquals(0, PythonContextRegistry.activeExecutions());
    }
}
