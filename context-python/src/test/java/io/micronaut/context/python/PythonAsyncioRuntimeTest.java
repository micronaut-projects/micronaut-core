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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.graceful.GracefulShutdownManager;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PythonAsyncioRuntimeTest {

    @Test
    void cancellationCallbackRunsWhenRegisteredBeforeCancel() {
        PythonAsyncioRuntime.PythonCompletableFuture future = new PythonAsyncioRuntime.PythonCompletableFuture();
        AtomicInteger calls = new AtomicInteger();

        future.setCancelCallback(calls::incrementAndGet);

        assertTrue(future.cancel(true));
        assertEquals(1, calls.get());
        future.cancel(true);
        assertEquals(1, calls.get());
    }

    @Test
    void cancellationCallbackRunsWhenRegisteredAfterCancel() {
        PythonAsyncioRuntime.PythonCompletableFuture future = new PythonAsyncioRuntime.PythonCompletableFuture();
        AtomicInteger calls = new AtomicInteger();

        assertTrue(future.cancel(true));
        future.setCancelCallback(calls::incrementAndGet);

        assertEquals(1, calls.get());
    }

    @Test
    void concurrentCancellationAndCallbackRegistrationRunsCallbackOnce() throws Exception {
        for (int i = 0; i < 1_000; i++) {
            PythonAsyncioRuntime.PythonCompletableFuture future = new PythonAsyncioRuntime.PythonCompletableFuture();
            AtomicInteger calls = new AtomicInteger();
            Thread register = new Thread(() -> future.setCancelCallback(calls::incrementAndGet));
            Thread cancel = new Thread(() -> future.cancel(true));

            register.start();
            cancel.start();
            register.join();
            cancel.join();

            assertEquals(1, calls.get());
        }
    }

    @Test
    void completesImmediatelyForNonAwaitableValue() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value value = context.eval(PYTHON, "'ok'");

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(value);

            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void completesCoroutineResult() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def message():
                    await asyncio.sleep(0)
                    return "ok"
                message()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void propagatesCoroutineFailure() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                async def fail():
                    raise RuntimeError("bad")
                fail()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            CompletionException exception = assertThrows(
                CompletionException.class,
                () -> stage.toCompletableFuture().join()
            );
            assertInstanceOf(RuntimeException.class, exception.getCause());
        }
    }

    @Test
    void schedulesCoroutineBridgeOnCurrentEventLoop() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                async def message():
                    return "ok"
                message()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertFalse(stage.toCompletableFuture().isDone());
            assertEquals(1, eventLoop.taskCount());

            eventLoop.runUntilComplete(stage);

            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void eventLoopsBeyondTheConfiguredCapGetNoDedicatedLoop() {
        RecordingEventLoop first = new RecordingEventLoop();
        RecordingEventLoop second = new RecordingEventLoop();
        AtomicReference<PythonEventLoop> current = new AtomicReference<>();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.ofNullable(current.get())));
        PythonAsyncioRuntime.setMaxEventLoops(1);
        try {
            current.set(first);
            assertSame(first, PythonAsyncioRuntime.currentEventLoopForContext());
            current.set(second);
            assertNull(PythonAsyncioRuntime.currentEventLoopForContext(), "the second loop is over the cap");
            current.set(first);
            assertSame(first, PythonAsyncioRuntime.currentEventLoopForContext(), "an admitted loop stays admitted");

            PythonAsyncioRuntime.setMaxEventLoops(0);
            current.set(second);
            assertSame(second, PythonAsyncioRuntime.currentEventLoopForContext(), "no cap admits every loop");
        } finally {
            PythonAsyncioRuntime.setMaxEventLoops(0);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void eventLoopContextsAreIsolatedAndDoNotBorrowFromBlockingPool() throws Exception {
        AtomicReference<PythonEventLoop> currentEventLoop = new AtomicReference<>();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.ofNullable(currentEventLoop.get())));
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context borrowed = pool.borrow();
            try {
                RecordingEventLoop first = new RecordingEventLoop();
                currentEventLoop.set(first);
                Value firstScript = PythonContextRuntime.findPooledScript(PYTHON, "Unnamed");
                firstScript.putMember("event_loop_marker", "first");
                assertEquals("first", firstScript.getMember("event_loop_marker").asString());

                RecordingEventLoop second = new RecordingEventLoop();
                currentEventLoop.set(second);
                Value secondScript = PythonContextRuntime.findPooledScript(PYTHON, "Unnamed");
                assertFalse(secondScript.hasMember("event_loop_marker"));
                secondScript.putMember("event_loop_marker", "second");

                currentEventLoop.set(first);
                firstScript = PythonContextRuntime.findPooledScript(PYTHON, "Unnamed");
                assertEquals("first", firstScript.getMember("event_loop_marker").asString());
            } finally {
                currentEventLoop.set(null);
                pool.release(borrowed);
            }
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void eventLoopPooledExecutionsRunInsideATrackedFrame() {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            // after startup: the application installs its own providers while it starts
            PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context eventLoopContext = pool.getEventLoopContext(eventLoop);
            assertFalse(PythonContextRegistry.inExecutionFrame());
            // the script load and the callback are one execution of the event-loop context
            boolean framed = PythonContextRuntime.withPooledScript(PYTHON, "Unnamed", script -> {
                assertEquals(eventLoopContext, script.getContext());
                return PythonContextRegistry.inExecutionFrame() && PythonContextRegistry.activeExecutions() == 1;
            });
            assertTrue(framed, "the event-loop execution ran outside a frame");
            boolean valueFramed = PythonContextRuntime.withPooledValue("1 + 1", value -> PythonContextRegistry.inExecutionFrame());
            assertTrue(valueFramed);
            assertEquals(0, PythonContextRegistry.activeExecutions());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void aCallerWithoutAContextGetsThePrimaryContextsScript() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            Context primary = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context borrowed = pool.borrow();
            try {
                // no event loop, no borrowed context of its own: the value must not come from the
                // pooled context another thread may be using
                Value script = PythonContextRuntime.findPooledScript(PYTHON, "Unnamed");
                assertEquals(primary, script.getContext());
                assertNotEquals(borrowed, script.getContext());
            } finally {
                pool.release(borrowed);
            }
        }
    }

    @Test
    void aScriptThatReentersThePoolForItselfWhileLoadingLoads() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            applicationContext.getBean(PythonPool.class);
            // the module body calls findPooledScript for its own module: a load inside a map
            // remapping function would fail with a recursive update
            Value script = PythonContextRuntime.findPooledScript(PYTHON, "reentrant");
            assertEquals(1, script.getMember("value").asInt());
            assertTrue(script.hasMember("again"));
        }
    }

    @Test
    void aNullableAsyncMemberIsCopiedIntoTheEventLoopContext() {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
            Context primary = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            String holder = "class Holder:\n    pass\n";
            primary.eval(PYTHON, holder);
            pool.getEventLoopContext(eventLoop).eval(PYTHON, holder);
            PythonContextRuntime.PythonClassReference reference = new PythonContextRuntime.PythonClassReference(
                PYTHON, "Holder", new String[0], "Holder", "class-instance:Holder");
            Value fallback = primary.eval(PYTHON, "Holder()");
            PythonContextRuntime.rememberAsyncMember(fallback, "client", null);
            PythonContextRuntime.rememberAsyncMember(fallback, "name", "x");

            Value target = PythonContextRuntime.asyncInstance(fallback, reference);

            assertEquals(pool.getEventLoopContext(eventLoop), target.getContext());
            assertTrue(target.getMember("client").isNull(), "a member remembered as null was not copied as None");
            assertEquals("x", target.getMember("name").asString());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void eventLoopContextAllocationDoesNotExhaustBlockingPool() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);

            PythonContextRuntime.findPooledScript(PYTHON, "Unnamed");

            Future<Boolean> borrowed = executorService.submit(() -> {
                Context context = pool.borrow();
                try {
                    return true;
                } finally {
                    pool.release(context);
                }
            });

            assertTrue(borrowed.get(5, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
        }
    }

    @Test
    void pooledValueIsEvaluatedOncePerPooledContext() {
        try (ApplicationContext ignored = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PooledValue render = PythonContextRuntime.withPooledValue("""
                count = 0
                def render(value):
                    global count
                    count += 1
                    return f"{value}:{count}"
                render
                """);

            assertEquals("first:1", render.executeAsString("first"));
            assertEquals("second:2", render.executeAsString("second"));
        }
    }

    @Test
    void poolStartupDoesNotCreatePooledContexts() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 2
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);

            assertEquals(0, pool.pooledContextCount());
            assertEquals(0, pool.availableContextCount());
        }
    }

    @Test
    void firstBorrowCreatesOnePooledContext() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 2
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);

            Context borrowed = pool.borrow();
            try {
                assertEquals(1, pool.pooledContextCount());
                assertEquals(0, pool.availableContextCount());
            } finally {
                pool.release(borrowed);
            }

            assertEquals(1, pool.pooledContextCount());
            assertEquals(1, pool.availableContextCount());
        }
    }

    @Test
    void disabledPoolUsesPrimaryContextWithoutCreatingPooledContexts() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", false
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context first = pool.withContext(context -> context);
            Context second = pool.withContext(context -> context);

            assertSame(PythonContextRuntime.getContext(), first);
            assertSame(first, second);
            assertEquals(0, pool.pooledContextCount());
            assertEquals(0, pool.availableContextCount());
        }
    }

    @Test
    void conversionFailureReleasesBorrowedContext() {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            PooledValueCoercible failing = new PooledValueCoercible() {
                @Override
                public Value asPolyglotValue() {
                    throw new AssertionError("Primary conversion should not be used");
                }

                @Override
                public Value asPolyglotValue(Context context) {
                    throw new IllegalArgumentException("conversion failed");
                }
            };

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                pool.withContext(context -> PythonCoercion.coerceToContext(failing, context))
            );

            assertEquals("conversion failed", exception.getMessage());
            assertEquals(1, pool.pooledContextCount());
            assertEquals(1, pool.availableContextCount());
        }
    }

    @Test
    void concurrentBorrowsGrowToConfiguredSizeAndThenWait() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 2
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context first = pool.borrow();
            Context second = pool.borrow();
            assertEquals(2, pool.pooledContextCount());

            Future<Context> waitingBorrow = executorService.submit(pool::borrow);
            assertThrows(TimeoutException.class, () -> waitingBorrow.get(200, TimeUnit.MILLISECONDS));

            pool.release(first);
            Context third = waitingBorrow.get(5, TimeUnit.SECONDS);
            try {
                assertEquals(first, third);
                assertEquals(2, pool.pooledContextCount());
            } finally {
                pool.release(second);
                pool.release(third);
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void waitingBorrowFailsWhenPoolCloses() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context borrowed = pool.borrow();
            Future<Context> waitingBorrow = executorService.submit(pool::borrow);
            assertThrows(TimeoutException.class, () -> waitingBorrow.get(100, TimeUnit.MILLISECONDS));

            // Graceful shutdown only waits for idle contexts; the pool closes with the application context.
            pool.shutdownGracefully().toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertThrows(TimeoutException.class, () -> waitingBorrow.get(100, TimeUnit.MILLISECONDS));
            applicationContext.close();

            ExecutionException failure = assertThrows(
                ExecutionException.class,
                () -> waitingBorrow.get(1, TimeUnit.SECONDS)
            );
            assertEquals("Pool closed", assertInstanceOf(IllegalStateException.class, failure.getCause()).getMessage());
            pool.release(borrowed);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void waitingBorrowRespondsToInterruption() throws Exception {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context borrowed = pool.borrow();
            Future<Context> waitingBorrow = executorService.submit(pool::borrow);
            assertThrows(TimeoutException.class, () -> waitingBorrow.get(100, TimeUnit.MILLISECONDS));

            assertTrue(waitingBorrow.cancel(true));
            assertTrue(executorService.submit(() -> true).get(1, TimeUnit.SECONDS));
            pool.release(borrowed);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void releasedContextCanBeBorrowedWhileAnotherContextIsBeingCreated() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 2
        ))) {
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            Context first = pool.borrow();
            Context expectedReuse = first;
            Context second = null;
            Context reused = null;
            BlockingGraalPyContextCustomizer.Gate gate = BlockingGraalPyContextCustomizer.blockNextContext();
            try {
                Future<Context> creatingBorrow = executorService.submit(pool::borrow);
                assertTrue(gate.entered.await(5, TimeUnit.SECONDS));

                Future<Context> waitingBorrow = executorService.submit(pool::borrow);
                assertThrows(TimeoutException.class, () -> waitingBorrow.get(100, TimeUnit.MILLISECONDS));

                pool.release(first);
                first = null;
                reused = waitingBorrow.get(1, TimeUnit.SECONDS);
                assertSame(expectedReuse, reused);

                gate.proceed.countDown();
                second = creatingBorrow.get(5, TimeUnit.SECONDS);
                assertEquals(2, pool.pooledContextCount());
            } finally {
                gate.proceed.countDown();
                if (first != null) {
                    pool.release(first);
                }
                if (second != null) {
                    pool.release(second);
                }
                if (reused != null) {
                    pool.release(reused);
                }
            }
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void currentEventLoopRunsAsyncioSleepWithoutBlockingCaller() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def message():
                    await asyncio.sleep(0.001)
                    return "ok"
                message()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertFalse(stage.toCompletableFuture().isDone());

            eventLoop.runUntilComplete(stage);

            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void injectionsAreAppliedByEachContextsOwnerNotBroadcastToBorrowedContexts() {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
            PythonPool pool = applicationContext.getBean(PythonPool.class);
            // a pooled context and the event-loop context cache the script first
            Context borrowed = pool.borrow();
            pool.getScript(borrowed, PYTHON, "Unnamed");
            pool.release(borrowed);
            Value eventLoopScript = PythonContextRuntime.withPooledScript(PYTHON, "Unnamed", script -> script);
            assertFalse(eventLoopScript.hasMember("injected"));

            PythonContextRuntime.injectPooledScript(PYTHON, "Unnamed", "injected", "value");

            // nothing was written into contexts the injecting thread does not own ...
            assertFalse(eventLoopScript.hasMember("injected"), "the injection was broadcast into the event-loop context");
            // ... each owner applies it when it asks for the script
            assertEquals("value", PythonContextRuntime.withPooledScript(PYTHON, "Unnamed", script -> script.getMember("injected").asString()));
            borrowed = pool.borrow();
            try {
                assertEquals("value", pool.getScript(borrowed, PYTHON, "Unnamed").getMember("injected").asString());
            } finally {
                pool.release(borrowed);
            }
            assertEquals("value", PythonContextRuntime.findPooledScript(PYTHON, "Unnamed").getMember("injected").asString());

            // the raw lookup applies a pending injection on the event-loop context by itself
            PythonContextRuntime.injectPooledScript(PYTHON, "Unnamed", "second", "later");
            assertEquals("later", PythonContextRuntime.findPooledScript(PYTHON, "Unnamed").getMember("second").asString());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void generatedEntryPointsRefuseAPrimaryContextSelectedForClosing() {
        try (ApplicationContext applicationContext = ApplicationContext.run()) {
            Context primary = applicationContext.getBean(Context.class, Qualifiers.byName(PYTHON));
            primary.eval(PYTHON, "class Holder:\n    pass\n");
            PythonContextRuntime.PythonClassReference reference = new PythonContextRuntime.PythonClassReference(
                PYTHON, "Holder", new String[0], "Holder", "class-instance:Holder");
            // no pool: every raw lookup and constructor falls back to the primary context
            assertEquals(primary, PythonContextRuntime.findPooledScript(PYTHON, "Unnamed").getContext());
            assertTrue(PythonContextRuntime.newInstance(reference).hasMembers());

            // once a close is selected for the primary context, the entry points refuse it rather
            // than racing the close (they count as executions, so an idle context is really idle)
            PythonContextRegistry.closeWhenIdle(primary, () -> { });
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.findPooledScript(PYTHON, "Unnamed"));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.findPooledScript(PYTHON, "Unnamed", primary));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.findPooledClass(reference));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.findClass(reference));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.newInstance(reference));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.newUninitializedInstance(reference));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.newIntroduction(reference));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.enumValue(reference, "X"));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.newFrozenDataclassInstance(reference, Map.of()));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.newUninitializedInstance(primary, reference, Map.of()));
            assertThrows(IllegalStateException.class, () -> PythonContextRuntime.findScript(PYTHON, "Unnamed"));
        }
    }

    @Test
    void aPooledContextStaysLeasedUntilTheCoroutineItRunsCompletes() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.python.pool.enabled", true,
            "micronaut.python.pool.size", 1
        ))) {
            assertTrue(applicationContext.containsBean(PythonPool.class));
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch gate = new CountDownLatch(1);
            ProxyExecutable block = arguments -> {
                started.countDown();
                try {
                    assertTrue(gate.await(2, TimeUnit.MINUTES));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return null;
            };
            ExecutorService threads = Executors.newFixedThreadPool(2);
            try {
                // no event loop: the coroutine is driven on the calling thread, blocked inside the gate
                Future<Object> coroutine = threads.submit(() -> PythonContextRuntime.invokePooledScriptAsync(PYTHON, "leased", "hold", block)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS));
                // creating the pooled context and importing asyncio take several seconds on a cold CI runner
                if (!started.await(2, TimeUnit.MINUTES)) {
                    // surface the coroutine's failure rather than a bare timeout
                    coroutine.get(30, TimeUnit.SECONDS);
                    throw new AssertionError("the coroutine did not reach the gate");
                }
                // the only pooled context is running the coroutine: a second bridge call must wait for it
                Future<String> second = threads.submit(() -> PythonContextRuntime.withPooledScript(PYTHON, "leased", value -> "borrowed"));
                assertThrows(TimeoutException.class, () -> second.get(500, TimeUnit.MILLISECONDS), "the context was lent out while its coroutine ran");
                gate.countDown();
                assertEquals("done", coroutine.get(1, TimeUnit.MINUTES));
                assertEquals("borrowed", second.get(1, TimeUnit.MINUTES));
            } finally {
                gate.countDown();
                threads.shutdownNow();
            }
        }
    }

    @Test
    void scheduledCallbacksRunInTheContextOfTheCodeThatScheduledThem() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import contextvars
                request = contextvars.ContextVar("request", default="none")
                async def run():
                    loop = asyncio.get_running_loop()
                    seen = []
                    token = request.set("first")
                    loop.call_soon(lambda: seen.append(request.get()))
                    loop.call_later(0.001, lambda: seen.append(request.get()))
                    request.reset(token)
                    explicit = contextvars.copy_context()
                    explicit.run(request.set, "explicit")
                    loop.call_soon(lambda: seen.append(request.get()), context=explicit)
                    await asyncio.sleep(0.01)
                    return ",".join(seen)
                run()
                """);
            CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runUntilComplete(stage);
            assertEquals("first,first,explicit", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void aHostDrivenCallLeavesNoLoopStateOnItsThread() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def message():
                    await asyncio.sleep(0.001)
                    return "ok"
                message()
                """);
            CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runUntilComplete(stage);
            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
            // the loop was current only for the duration of the calls: this thread keeps no loop state
            Value state = context.eval(PYTHON, """
                import asyncio
                (asyncio.events._get_running_loop() is None, asyncio.get_event_loop_policy()._local._loop is None)
                """);
            assertTrue(state.getArrayElement(0).asBoolean(), "the running loop stayed set on the calling thread");
            assertTrue(state.getArrayElement(1).asBoolean(), "the policy loop stayed set on the calling thread");
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void aClosedContextIsReleasedByTheThreadThatRanItsCoroutines() throws Exception {
        // asyncio's thread-local running loop, stored by GraalPy in a Java ThreadLocal of this
        // thread, used to keep every closed context's heap reachable for the life of the thread;
        // a host object stored in the context's globals stands in for that heap
        WeakReference<Object> heap = runACoroutineAndCloseTheContext();
        for (int attempt = 0; attempt < 100 && heap.get() != null; attempt++) {
            System.gc();
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertNull(heap.get(), "the closed context's heap stayed reachable from the thread that ran its coroutine");
    }

    private static WeakReference<Object> runACoroutineAndCloseTheContext() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Object marker = new Object();
        try {
            PythonContextRegistry.registerContext(context);
            context.getBindings(PYTHON).putMember("marker", marker);
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def message():
                    await asyncio.sleep(0.001)
                    return asyncio.get_running_loop() is not None
                message()
                """);
            CompletionStage<?> stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runUntilComplete(stage);
            assertEquals(true, stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            PythonContextRegistry.unregisterContext(context);
            context.close(true);
        }
        return new WeakReference<>(marker);
    }

    @Test
    void currentEventLoopPreservesCallSoonOrdering() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def ordered():
                    loop = asyncio.get_running_loop()
                    future = loop.create_future()
                    values = []
                    loop.call_soon(values.append, "first")
                    loop.call_soon(values.append, "second")
                    loop.call_soon(future.set_result, None)
                    await future
                    return ",".join(values)
                ordered()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("first,second", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopCancelsTimerHandles() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def cancel_timer():
                    loop = asyncio.get_running_loop()
                    future = loop.create_future()
                    values = []
                    handle = loop.call_later(0.001, values.append, "cancelled")
                    handle.cancel()
                    loop.call_soon(values.append, "kept")
                    loop.call_later(0.001, future.set_result, None)
                    await future
                    return ",".join(values)
                cancel_timer()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("kept", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRunsAsyncioGather() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def first():
                    await asyncio.sleep(0.001)
                    return "first"
                async def second():
                    await asyncio.sleep(0.001)
                    return "second"
                async def joined():
                    return ",".join(await asyncio.gather(first(), second()))
                joined()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("first,second", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRunsAsyncioTaskGroup() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def first():
                    await asyncio.sleep(0.001)
                    return "first"
                async def second():
                    await asyncio.sleep(0.001)
                    return "second"
                async def joined():
                    async with asyncio.TaskGroup() as task_group:
                        first_task = task_group.create_task(first())
                        second_task = task_group.create_task(second())
                    return f"{first_task.result()},{second_task.result()}"
                joined()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("first,second", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRejectsUnsupportedTaskOptions() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def unsupported_options():
                    loop = asyncio.get_running_loop()
                    eager = asyncio.sleep(0)
                    unknown = asyncio.sleep(0)
                    try:
                        loop.create_task(eager, eager_start=True)
                    except NotImplementedError as exc:
                        eager.close()
                        eager_message = str(exc)
                    try:
                        loop.create_task(unknown, priority=1)
                    except NotImplementedError as exc:
                        unknown.close()
                        unknown_message = str(exc)
                    return f"{eager_message}|{unknown_message}"
                unsupported_options()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            String result = stage.toCompletableFuture().get(1, TimeUnit.SECONDS).toString();
            assertTrue(result.contains("eager task execution is not supported"), result);
            assertTrue(result.contains("priority"), result);
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRejectsNestedAsyncioRun() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def nested_run():
                    try:
                        asyncio.run(asyncio.sleep(0))
                    except RuntimeError as exc:
                        return str(exc)
                    return "missing-error"
                nested_run()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertTrue(stage.toCompletableFuture().get(1, TimeUnit.SECONDS).toString().contains("asyncio.run() cannot be called from a running event loop"));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRunsDefaultExecutorCallbacksOnConfiguredExecutor() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def offload():
                    loop = asyncio.get_running_loop()
                    return await loop.run_in_executor(None, lambda: "ok")
                offload()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
        }
    }

    @Test
    void currentEventLoopRunsDnsLookupsOnConfiguredExecutor() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        PythonAsyncioRuntime.setExecutorService(executorService);
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import socket
                async def lookup():
                    loop = asyncio.get_running_loop()
                    infos = await loop.getaddrinfo("127.0.0.1", 80)
                    name = await loop.getnameinfo(("127.0.0.1", 80), socket.NI_NUMERICHOST | socket.NI_NUMERICSERV)
                    return f"{len(infos) > 0}:{name[0]}:{name[1]}"
                lookup()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("True:127.0.0.1:80", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setExecutorService(null);
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
            executorService.shutdownNow();
        }
    }

    @Test
    void currentEventLoopRunsSocketPairIoWithoutBlocking() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import socket
                async def socket_pair_io():
                    loop = asyncio.get_running_loop()
                    left, right = socket.socketpair()
                    left.setblocking(False)
                    right.setblocking(False)
                    try:
                        await loop.sock_sendall(left, b"ok")
                        first = await loop.sock_recv(right, 2)
                        buffer = bytearray(2)
                        await loop.sock_sendall(right, b"hi")
                        count = await loop.sock_recv_into(left, buffer)
                        return f"{first.decode()}:{count}:{buffer.decode()}"
                    finally:
                        left.close()
                        right.close()
                socket_pair_io()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("ok:2:hi", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRunsDatagramSocketIoWithoutBlocking() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import socket
                async def datagram_io():
                    loop = asyncio.get_running_loop()
                    receiver = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    sender = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                    receiver.bind(("127.0.0.1", 0))
                    receiver.setblocking(False)
                    sender.setblocking(False)
                    try:
                        await loop.sock_sendto(sender, b"ok", receiver.getsockname())
                        first, _ = await loop.sock_recvfrom(receiver, 2)
                        buffer = bytearray(2)
                        await loop.sock_sendto(sender, b"hi", receiver.getsockname())
                        count, _ = await loop.sock_recvfrom_into(receiver, buffer)
                        return f"{first.decode()}:{count}:{buffer.decode()}"
                    finally:
                        receiver.close()
                        sender.close()
                datagram_io()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("ok:2:hi", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void aLoopWithoutNettyFactoriesRejectsConnectionApis() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import socket
                async def unsupported():
                    loop = asyncio.get_running_loop()
                    calls = (
                        ("create_connection", lambda: loop.create_connection(asyncio.Protocol, "127.0.0.1", 1)),
                        ("create_server", lambda: loop.create_server(asyncio.Protocol, "127.0.0.1", 0)),
                        ("create_datagram_endpoint", lambda: loop.create_datagram_endpoint(asyncio.DatagramProtocol, local_addr=("127.0.0.1", 0))),
                        ("connect_accepted_socket", lambda: loop.connect_accepted_socket(asyncio.Protocol, socket.socket())),
                    )
                    messages = []
                    for name, call in calls:
                        try:
                            await call()
                        except NotImplementedError as exc:
                            messages.append(name + "=" + str(exc))
                    return "|".join(messages)
                unsupported()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runUntilComplete(stage);
            String messages = (String) stage.toCompletableFuture().get(1, TimeUnit.SECONDS);

            for (String api : List.of("create_connection", "create_server", "create_datagram_endpoint", "connect_accepted_socket")) {
                assertTrue(messages.contains(api + "=asyncio event-loop API [" + api), messages);
            }
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopRunsSocketConnectAndAcceptWithoutBlocking() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import socket
                async def connect_and_accept():
                    loop = asyncio.get_running_loop()
                    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                    server.bind(("127.0.0.1", 0))
                    server.listen()
                    server.setblocking(False)
                    client.setblocking(False)
                    accepted = None
                    try:
                        accept_task = loop.create_task(loop.sock_accept(server))
                        connect_task = loop.create_task(loop.sock_connect(client, server.getsockname()))
                        accepted, _ = await accept_task
                        await connect_task
                        await loop.sock_sendall(client, b"x")
                        return (await loop.sock_recv(accepted, 1)).decode()
                    finally:
                        if accepted is not None:
                            accepted.close()
                        client.close()
                        server.close()
                connect_and_accept()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("x", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void currentEventLoopReportsDeterministicUnsupportedApis() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                import inspect
                async def unsupported():
                    loop = asyncio.get_running_loop()
                    calls = (
                        ("run_in_executor with a custom executor", lambda: loop.run_in_executor(object(), lambda: "ignored")),
                        ("create_unix_connection", lambda: loop.create_unix_connection(lambda: None, "/tmp/socket")),
                        ("create_unix_server", lambda: loop.create_unix_server(lambda: None, "/tmp/socket")),
                        ("sendfile", lambda: loop.sendfile(object(), object())),
                        ("start_tls", lambda: loop.start_tls(object(), object(), object())),
                        ("add_reader", lambda: loop.add_reader(0, lambda: None)),
                        ("remove_reader", lambda: loop.remove_reader(0)),
                        ("add_writer", lambda: loop.add_writer(0, lambda: None)),
                        ("remove_writer", lambda: loop.remove_writer(0)),
                        ("subprocess_exec", lambda: loop.subprocess_exec(lambda: None, "echo")),
                        ("subprocess_shell", lambda: loop.subprocess_shell(lambda: None, "echo ok")),
                    )
                    messages = []
                    for name, call in calls:
                        try:
                            result = call()
                            if inspect.isawaitable(result):
                                await result
                        except NotImplementedError as exc:
                            messages.append(name + "=" + str(exc))
                    return "|".join(messages)
                unsupported()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            String message = stage.toCompletableFuture().get(1, TimeUnit.SECONDS).toString();
            for (String name : List.of(
                "run_in_executor with a custom executor",
                "create_unix_connection",
                "create_unix_server",
                "sendfile",
                "start_tls",
                "add_reader",
                "remove_reader",
                "add_writer",
                "remove_writer",
                "subprocess_exec",
                "subprocess_shell"
            )) {
                assertTrue(message.contains(name), name);
            }
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void asyncMemberValueAdaptsCompletionStageMethodResults() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new AsyncClient()));
            Value coroutine = context.eval(PYTHON, """
                async def message(target):
                    return "demo:" + await target.client.message()
                message
                """).execute(target);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("demo:backend", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void asyncMemberValueReconstructsPythonWrapperInTargetContext() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, "type('Target', (), {})()");
            PooledValueCoercible client = new PooledValueCoercible() {
                @Override
                public Value asPolyglotValue() {
                    throw new AssertionError("Primary conversion should not be used");
                }

                @Override
                public Value asPolyglotValue(Context targetContext) {
                    assertEquals(context, targetContext);
                    return targetContext.eval(PYTHON, "type('Client', (), {'name': 'python-client'})()");
                }
            };

            Object adapted = PythonCoercion.asyncMemberValue(target, client);
            PythonCoercion.putMember(target, "client", adapted);

            assertEquals("python-client", target.getMember("client").getMember("name").asString());
            assertEquals(context, assertInstanceOf(Value.class, adapted).getContext());
        }
    }

    @Test
    void asyncMemberValueAdaptsPublisherMethodResultsAsScalarAwaitables() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new ReactiveClient()));
            Value coroutine = context.eval(PYTHON, """
                async def values(target):
                    first = await target.client.first()
                    empty = await target.client.empty()
                    many = await target.client.many()
                    return first + "|" + str(empty is None) + "|" + many
                values
                """).execute(target);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals("first|True|one", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    @Test
    void asyncMemberValuePropagatesPublisherErrors() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new ReactiveClient()));
            Value coroutine = context.eval(PYTHON, """
                async def fail(target):
                    try:
                        await target.client.error()
                    except RuntimeError as exc:
                        return str(exc)
                    return "missing-error"
                fail
                """).execute(target);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertTrue(stage.toCompletableFuture().get(1, TimeUnit.SECONDS).toString().contains("reactive failure"));
        }
    }

    @Test
    void cancellingPublisherAwaitCancelsSubscription() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        NeverPublisher publisher = new NeverPublisher();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new ReactiveClient(publisher)));
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def cancel(target):
                    task = asyncio.ensure_future(target.client.never())
                    await asyncio.sleep(0.001)
                    task.cancel()
                    try:
                        await task
                    except asyncio.CancelledError:
                        return "cancelled"
                    return "missing-cancel"
                cancel
                """).execute(target);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            eventLoop.runUntilComplete(stage);

            assertEquals("cancelled", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertTrue(publisher.cancelled.get());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void javaCancellationCancelsPythonTask() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                cancelled = False
                async def wait_forever():
                    global cancelled
                    try:
                        await asyncio.get_running_loop().create_future()
                    except asyncio.CancelledError:
                        cancelled = True
                        raise
                wait_forever()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runNext();

            assertTrue(stage.toCompletableFuture().cancel(true));

            eventLoop.drainTasks(8);

            assertTrue(context.getBindings(PYTHON).getMember("cancelled").asBoolean());
            assertTrue(stage.toCompletableFuture().isCancelled());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void coroutineBridgeReportsActiveExecutionUntilCompletion() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                async def message():
                    return "ok"
                message()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            assertEquals(1, PythonContextRegistry.activeExecutions());
            eventLoop.runUntilComplete(stage);
            assertEquals("ok", stage.toCompletableFuture().get(1, TimeUnit.SECONDS));
            assertEquals(0, PythonContextRegistry.activeExecutions());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void gracefulShutdownManagerWaitsForActivePythonExecution() throws Exception {
        try (ApplicationContext applicationContext = ApplicationContext.run(Map.of(
            "micronaut.lifecycle.graceful-shutdown.enabled", true,
            "micronaut.python.pool.enabled", false
        ))) {
            GracefulShutdownManager manager = applicationContext.getBean(GracefulShutdownManager.class);
            PythonContextRegistry.enterExecution(PythonContextRuntime.getContext());
            CompletionStage<?> shutdown;
            try {
                assertEquals(1, manager.reportActiveTasks().orElseThrow());
                shutdown = manager.shutdownGracefully();
                assertFalse(shutdown.toCompletableFuture().isDone());
            } finally {
                PythonContextRegistry.exitExecution(PythonContextRuntime.getContext());
            }

            shutdown.toCompletableFuture().get(1, TimeUnit.SECONDS);
            assertEquals(0, manager.reportActiveTasks().orElseThrow());
        }
    }

    public static final class AsyncClient {
        public CompletionStage<String> message() {
            return CompletableFuture.completedFuture("backend");
        }
    }

    public static final class ReactiveClient {
        private final Publisher<String> never;

        ReactiveClient() {
            this(new NeverPublisher());
        }

        ReactiveClient(Publisher<String> never) {
            this.never = never;
        }

        public Publisher<String> first() {
            return Publishers.just("first");
        }

        public Publisher<String> empty() {
            return Publishers.empty();
        }

        public Publisher<String> error() {
            return Publishers.just(new IllegalStateException("reactive failure"));
        }

        public Publisher<String> many() {
            return subscriber -> subscriber.onSubscribe(new Subscription() {
                boolean cancelled;

                @Override
                public void request(long n) {
                    if (cancelled || n < 1) {
                        return;
                    }
                    subscriber.onNext("one");
                    if (!cancelled) {
                        subscriber.onNext("two");
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                    cancelled = true;
                }
            });
        }

        public Publisher<String> never() {
            return never;
        }
    }

    private static final class NeverPublisher implements Publisher<String> {
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void subscribe(Subscriber<? super String> subscriber) {
            subscriber.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                }

                @Override
                public void cancel() {
                    cancelled.set(true);
                }
            });
        }
    }

    @Test
    void wrapsCompletionStageAsPythonAwaitable() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            CompletableFuture<String> javaFuture = new CompletableFuture<>();
            Value awaitable = PythonAsyncioRuntime.toAwaitable(context, javaFuture);
            Value run = context.eval(PYTHON, """
                import asyncio
                def run(awaitable):
                    return asyncio.get_event_loop().run_until_complete(awaitable)
                run
                """);

            javaFuture.complete("ok");

            assertEquals("ok", run.execute(awaitable).asString());
        }
    }

    @Test
    void configurerDoesNotMaterializeIoExecutorOnContextStart() {
        AtomicBoolean resolved = new AtomicBoolean();
        AtomicReference<ExecutorService> executorService = new AtomicReference<>();
        BeanProvider<ExecutorService> executorServiceProvider = new BeanProvider<>() {
            @Override
            public ExecutorService get() {
                resolved.set(true);
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executorService.set(executor);
                return executor;
            }

            @Override
            public boolean isResolvable() {
                return true;
            }
        };
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonApplicationRuntime runtime = new PythonApplicationRuntime(context, null);
            PythonAsyncioRuntimeConfigurer configurer = new PythonAsyncioRuntimeConfigurer(new PythonAsyncioConfiguration(true), new PythonPoolConfiguration(true, 0, null, 0), runtime, List.of(), executorServiceProvider);
            try {
                assertFalse(resolved.get());
                assertSame(executorServiceProvider, runtime.pooledExecutorServiceProvider());
            } finally {
                configurer.reset();
                assertNull(runtime.pooledExecutorServiceProvider());
                ExecutorService executor = executorService.get();
                if (executor != null) {
                    executor.shutdownNow();
                }
            }
        }
    }

    @Test
    void disabledConfigurationRejectsCoroutineBridge() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonAsyncioRuntimeConfigurer configurer = new PythonAsyncioRuntimeConfigurer(new PythonAsyncioConfiguration(false), new PythonPoolConfiguration(true, 0, null, 0), new PythonApplicationRuntime(context, null), List.of(), null);
            Value coroutine = context.eval(PYTHON, """
                async def message():
                    return "ok"
                message()
                """);

            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PythonAsyncioRuntime.toCompletionStage(coroutine)
            );

            assertFalse(exception.getMessage().isBlank());
            configurer.reset();
        }
    }

    @Test
    void coroutineFailureKeepsHostExceptionTypeWithoutEventLoop() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("boom", (ProxyExecutable) args -> {
                throw new IllegalArgumentException("boom");
            });
            Value coroutine = context.eval(PYTHON, """
                async def fail():
                    boom()
                fail()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            CompletableFuture<?> joined = stage.toCompletableFuture();
            CompletionException exception = assertThrows(CompletionException.class, joined::join);
            assertEquals("boom", assertInstanceOf(IllegalArgumentException.class, exception.getCause()).getMessage());
        }
    }

    @Test
    void coroutineFailureKeepsHostExceptionTypeOnEventLoop() throws Exception {
        RecordingEventLoop eventLoop = new RecordingEventLoop();
        PythonAsyncioRuntime.setEventLoopProviders(List.of(() -> Optional.of(eventLoop)));
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            context.getBindings(PYTHON).putMember("boom", (ProxyExecutable) args -> {
                throw new IllegalArgumentException("boom");
            });
            Value coroutine = context.eval(PYTHON, """
                import asyncio
                async def fail():
                    await asyncio.sleep(0)
                    boom()
                fail()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);
            eventLoop.runUntilComplete(stage);

            CompletableFuture<?> joined = stage.toCompletableFuture();
            CompletionException exception = assertThrows(CompletionException.class, joined::join);
            assertEquals("boom", assertInstanceOf(IllegalArgumentException.class, exception.getCause()).getMessage());
        } finally {
            PythonAsyncioRuntime.setEventLoopProviders(List.of());
        }
    }

    @Test
    void pythonCoroutineFailureIsReportedAsPolyglotException() {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value coroutine = context.eval(PYTHON, """
                async def fail():
                    raise ValueError("bad value")
                fail()
                """);

            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(coroutine);

            CompletableFuture<?> joined = stage.toCompletableFuture();
            CompletionException exception = assertThrows(CompletionException.class, joined::join);
            PolyglotException cause = assertInstanceOf(PolyglotException.class, exception.getCause());
            assertTrue(cause.isGuestException());
            assertTrue(cause.getMessage().contains("bad value"), cause.getMessage());
        }
    }

    @Test
    void awaitedJavaFailureKeepsHostExceptionType() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value target = context.eval(PYTHON, """
                class Target:
                    pass
                Target()
                """);
            PythonCoercion.putMember(target, "client", PythonCoercion.asyncMemberValue(target, new FailingAsyncClient()));

            Value propagating = context.eval(PYTHON, """
                async def call(target):
                    return await target.client.message()
                call
                """).execute(target);
            CompletionStage stage = PythonAsyncioRuntime.toCompletionStage(propagating);
            CompletableFuture<?> joined = stage.toCompletableFuture();
            CompletionException exception = assertThrows(CompletionException.class, joined::join);
            assertEquals("backend down", assertInstanceOf(IllegalStateException.class, exception.getCause()).getMessage());

            Value catching = context.eval(PYTHON, """
                async def call(target):
                    try:
                        return await target.client.message()
                    except Exception as e:
                        return type(e).__name__ + ":" + str(e.java_exception.getMessage())
                call
                """).execute(target);
            CompletionStage caught = PythonAsyncioRuntime.toCompletionStage(catching);
            assertEquals("MicronautJavaException:backend down", caught.toCompletableFuture().get(1, TimeUnit.SECONDS));
        }
    }

    public static final class FailingAsyncClient {
        public CompletionStage<String> message() {
            return CompletableFuture.failedFuture(new IllegalStateException("backend down"));
        }
    }

    private static final class RecordingEventLoop implements PythonEventLoop {
        private final BlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
        private boolean inEventLoop;

        @Override
        public boolean inEventLoop() {
            return inEventLoop;
        }

        @Override
        public void execute(Runnable runnable) {
            tasks.add(runnable);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit) {
            TestScheduledFuture future = new TestScheduledFuture(runnable);
            tasks.add(future);
            return future;
        }

        @Override
        public double time() {
            return System.nanoTime() / 1_000_000_000.0d;
        }

        int taskCount() {
            return tasks.size();
        }

        void runNext() throws InterruptedException {
            Runnable runnable = tasks.poll(1, TimeUnit.SECONDS);
            assertTrue(runnable != null);
            run(runnable);
        }

        void runUntilComplete(CompletionStage<?> stage) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            while (!stage.toCompletableFuture().isDone() && System.nanoTime() < deadline) {
                Runnable runnable = tasks.poll(100, TimeUnit.MILLISECONDS);
                if (runnable != null) {
                    run(runnable);
                }
            }
            if (!stage.toCompletableFuture().isDone()) {
                throw new TimeoutException("CompletionStage did not complete");
            }
        }

        void drainTasks(int maxTasks) throws InterruptedException {
            for (int i = 0; i < maxTasks && taskCount() > 0; i++) {
                runNext();
            }
        }

        private void run(Runnable runnable) {
            inEventLoop = true;
            try {
                runnable.run();
            } finally {
                inEventLoop = false;
            }
        }
    }

    private static final class TestScheduledFuture implements ScheduledFuture<Object>, Runnable {
        private final Runnable runnable;
        private boolean cancelled;
        private boolean done;

        TestScheduledFuture(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (done) {
                return false;
            }
            cancelled = true;
            done = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return done;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }

        @Override
        public void run() {
            if (!cancelled) {
                try {
                    runnable.run();
                } finally {
                    done = true;
                }
            }
        }
    }
}
