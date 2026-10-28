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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Executions enter and leave a context without the registry lock; a close still never runs while
 * an execution of the context is in flight, and no execution starts once the close is registered.
 */
final class PythonContextRegistryConcurrencyTest {

    private static final int THREADS = 8;

    @RepeatedTest(5)
    void aContextClosesOnlyOnceNoExecutionRunsAndRefusesExecutionsAfterwards() throws Exception {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        int before = PythonContextRegistry.activeExecutions();
        PythonContextRegistry.registerContext(context);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        AtomicBoolean closed = new AtomicBoolean();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch started = new CountDownLatch(THREADS);
            List<Future<Integer>> workers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                workers.add(executor.submit(() -> {
                    started.countDown();
                    int executions = 0;
                    while (true) {
                        try {
                            PythonContextRegistry.withExecutionFrame(context, () -> {
                                running.incrementAndGet();
                                if (closed.get()) {
                                    violations.incrementAndGet();
                                }
                                // a nested entry of a running execution is allowed while the context closes
                                PythonContextRegistry.withExecutionFrame(context, () -> null);
                                running.decrementAndGet();
                                return null;
                            });
                            executions++;
                        } catch (IllegalStateException e) {
                            return executions;
                        }
                    }
                }));
            }
            started.await();
            Thread.sleep(20);
            PythonContextRegistry.closeWhenIdle(context, () -> {
                closes.incrementAndGet();
                if (running.get() != 0) {
                    violations.incrementAndGet();
                }
                closed.set(true);
            });
            int executions = 0;
            for (Future<Integer> worker : workers) {
                executions += worker.get(1, TimeUnit.MINUTES);
            }

            assertTrue(executions > 0, "no execution ran before the close");
            assertEquals(0, violations.get(), "the context closed under an execution, or an execution started after the close");
            assertEquals(1, closes.get());
            assertEquals(0, PythonContextRegistry.activeExecutions(context));
            assertThrows(IllegalStateException.class, () -> PythonContextRegistry.withExecutionFrame(context, () -> null));
            assertEquals(before, PythonContextRegistry.activeExecutions());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
            PythonContextRegistry.unregisterContext(context);
            context.close(true);
        }
    }

    @Test
    void listenersWaitingForIdleRunOnceEachWhileExecutionsComeAndGo() throws Exception {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(context);
        AtomicBoolean stop = new AtomicBoolean();
        AtomicInteger ran = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        int listeners = 2_000;
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                workers.add(executor.submit(() -> {
                    while (!stop.get()) {
                        PythonContextRegistry.withExecutionFrame(context, () -> null);
                    }
                    return null;
                }));
            }
            for (int i = 0; i < listeners; i++) {
                PythonContextRegistry.onNoActiveExecutions(context, ran::incrementAndGet);
            }
            stop.set(true);
            for (Future<?> worker : workers) {
                worker.get(1, TimeUnit.MINUTES);
            }
            // the last exit made the context idle: every listener has run, none twice
            assertEquals(listeners, ran.get());
            assertEquals(0, PythonContextRegistry.activeExecutions(context));
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
            PythonContextRegistry.unregisterContext(context);
            context.close(true);
        }
    }

    @Test
    void contextsClosedTogetherCloseOnceAllAreIdle() throws Exception {
        Context first = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Context second = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(first);
        PythonContextRegistry.registerContext(second);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger violations = new AtomicInteger();
        AtomicInteger closes = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            CountDownLatch started = new CountDownLatch(THREADS);
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                Context context = i % 2 == 0 ? first : second;
                workers.add(executor.submit(() -> {
                    started.countDown();
                    while (true) {
                        try {
                            PythonContextRegistry.withExecutionFrame(context, () -> {
                                running.incrementAndGet();
                                Thread.onSpinWait();
                                running.decrementAndGet();
                                return null;
                            });
                        } catch (IllegalStateException e) {
                            return null;
                        }
                    }
                }));
            }
            started.await();
            Thread.sleep(20);
            PythonContextRegistry.closeWhenIdle(List.of(first, second, first), () -> {
                closes.incrementAndGet();
                if (running.get() != 0) {
                    violations.incrementAndGet();
                }
            });
            for (Future<?> worker : workers) {
                worker.get(1, TimeUnit.MINUTES);
            }

            assertEquals(0, violations.get(), "the contexts closed under an execution");
            assertEquals(1, closes.get());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
            for (Context context : List.of(first, second)) {
                PythonContextRegistry.unregisterContext(context);
                context.close(true);
            }
        }
    }

    @Test
    void executionsOfAnUnregisteredContextLeaveTheAggregateOnce() throws Exception {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        int before = PythonContextRegistry.activeExecutions();
        PythonContextRegistry.registerContext(context);
        CountDownLatch inside = new CountDownLatch(THREADS);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            List<Future<?>> workers = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                workers.add(executor.submit(() -> PythonContextRegistry.withExecutionFrame(context, () -> {
                    inside.countDown();
                    release.await();
                    return null;
                })));
            }
            inside.await();
            assertEquals(before + THREADS, PythonContextRegistry.activeExecutions());
            // the executions in flight leave the aggregate with the state; their exits count nothing out
            PythonContextRegistry.unregisterContext(context);
            assertEquals(before, PythonContextRegistry.activeExecutions());
            release.countDown();
            for (Future<?> worker : workers) {
                worker.get(1, TimeUnit.MINUTES);
            }
            assertEquals(before, PythonContextRegistry.activeExecutions());
        } finally {
            release.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
            PythonContextRegistry.unregisterContext(context);
            context.close(true);
        }
    }
}
