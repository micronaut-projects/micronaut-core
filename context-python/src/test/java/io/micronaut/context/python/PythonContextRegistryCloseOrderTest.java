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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The application context closes the primary Python context and the pool closes its contexts, the primary one
 * included in what it waits for, in whichever order the two destruction listeners run: they have the same order,
 * and the order differs between the JVM and a native image. When the primary context is closed first, the pool's
 * close must not leave a state behind for it: the state kept the closed context, and the engine, which closes
 * only once no state of its contexts remains, stayed open with everything it held.
 */
final class PythonContextRegistryCloseOrderTest {

    @Test
    void closingThePoolAfterThePrimaryContextLeavesNoStateAndClosesTheEngine() {
        Engine engine = Engine.create(PYTHON);
        Context primary = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        Context pooled = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        AtomicBoolean engineClosed = new AtomicBoolean();
        try {
            PythonContextRegistry.registerContext(primary);
            PythonContextRegistry.state(pooled);

            // the application context closes the primary context first
            PythonContextRegistry.closeWhenIdle(primary, () -> GraalPyContextFactory.closeContext(primary));
            assertNull(PythonContextRegistry.existingState(primary));

            // then the pool, told the primary context is destroyed, waits for it to be idle, as PythonPool.onDestroyed does,
            // and closes its contexts once they and the primary one are idle; the engine is destroyed meanwhile, waiting
            // for the contexts of the engine
            PythonContextRegistry.onNoActiveExecutionsAfterCurrentFrame(primary, () -> PythonContextRegistry.closeWhenIdle(List.of(primary, pooled), () -> {
                PythonContextRegistry.onNoContexts(engine, () -> {
                    engine.close();
                    engineClosed.set(true);
                });
                GraalPyContextFactory.closeContext(pooled);
            }));

            assertNull(PythonContextRegistry.existingState(primary), "the pool's close kept a state for the closed primary context");
            assertNull(PythonContextRegistry.existingState(pooled), "the pooled context kept its state");
            assertTrue(engineClosed.get(), "the engine waits for a context that is closed");
        } finally {
            PythonContextRegistry.unregisterContext(primary);
            PythonContextRegistry.unregisterContext(pooled);
            if (!engineClosed.get()) {
                engine.close(true);
            }
        }
    }

    /**
     * The order the native image showed: the pool closes after the primary context while a pooled context still
     * counts an execution, and that context is closed and unregistered before the execution leaves, as a context
     * handed back to a closed pool is. Its exit then finds no state, so the pool's close must run when it is
     * unregistered, or it never does, and the state marked closing for the primary context, and the engine, stay.
     */
    @Test
    void aPooledContextUnregisteredDuringAnExecutionReleasesThePoolsClose() {
        Engine engine = Engine.create(PYTHON);
        Context primary = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        Context pooled = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        AtomicBoolean poolClosed = new AtomicBoolean();
        AtomicBoolean engineClosed = new AtomicBoolean();
        try {
            PythonContextRegistry.registerContext(primary);
            PythonContextRegistry.enterExecution(pooled);

            PythonContextRegistry.closeWhenIdle(primary, () -> GraalPyContextFactory.closeContext(primary));
            PythonContextRegistry.closeWhenIdle(List.of(primary, pooled), () -> poolClosed.set(true));
            PythonContextRegistry.onNoContexts(engine, () -> {
                engine.close();
                engineClosed.set(true);
            });
            // the pool waits for the pooled context's execution
            assertNotNull(PythonContextRegistry.existingState(primary));

            // handed back to the closing pool, the pooled context is closed before its execution leaves
            GraalPyContextFactory.closeContext(pooled);
            PythonContextRegistry.exitExecution(pooled);

            assertTrue(poolClosed.get(), "the pool's close waits for an execution of an unregistered context");
            assertNull(PythonContextRegistry.existingState(primary), "the pool's close kept a state for the closed primary context");
            assertNull(PythonContextRegistry.existingState(pooled));
            assertTrue(engineClosed.get(), "the engine waits for a context that is closed");
        } finally {
            PythonContextRegistry.unregisterContext(primary);
            PythonContextRegistry.unregisterContext(pooled);
            if (!engineClosed.get()) {
                engine.close(true);
            }
        }
    }

    @Test
    void closingThePoolBeforeThePrimaryContextKeepsTheStateOfTheRunningPrimaryContext() {
        Engine engine = Engine.create(PYTHON);
        Context primary = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        Context pooled = Context.newBuilder(PYTHON).engine(engine).allowAllAccess(true).build();
        try {
            PythonContextRegistry.registerContext(primary);

            PythonContextRegistry.closeWhenIdle(List.of(primary, pooled), () -> GraalPyContextFactory.closeContext(pooled));

            // the registered primary context is the application context's to close, and keeps its state until then
            assertNotNull(PythonContextRegistry.existingState(primary));
            assertNull(PythonContextRegistry.existingState(pooled));
            PythonContextRegistry.closeWhenIdle(primary, () -> GraalPyContextFactory.closeContext(primary));
            assertNull(PythonContextRegistry.existingState(primary));
        } finally {
            PythonContextRegistry.unregisterContext(primary);
            PythonContextRegistry.unregisterContext(pooled);
            engine.close(true);
        }
    }
}
