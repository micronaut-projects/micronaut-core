package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link Value#getContext()} returns a view object that is equal to, but not the same as, the creator
 * {@link Context}. The runtime must treat both as one context.
 */
final class PythonContextRuntimeStateTest {

    @Test
    void contextLockIsSharedBetweenCreatorContextAndValueContext() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            Value value = context.eval(PYTHON, "1");
            assertEquals(context, value.getContext());
            assertNotSame(context, value.getContext());

            CountDownLatch held = new CountDownLatch(1);
            Semaphore gate = new Semaphore(0);
            Thread holder = new Thread(() -> PythonContextRegistry.withContextLock(context, () -> {
                held.countDown();
                gate.acquireUninterruptibly();
                return null;
            }));
            holder.start();
            assertTrue(held.await(5, TimeUnit.SECONDS));

            AtomicBoolean acquired = new AtomicBoolean();
            Thread contender = new Thread(() -> PythonContextRegistry.withContextLock(value.getContext(), () -> {
                acquired.set(true);
                return null;
            }));
            contender.start();
            contender.join(500);
            boolean acquiredWhileHeld = acquired.get();
            gate.release();
            holder.join(5000);
            contender.join(5000);

            assertFalse(acquiredWhileHeld, "lock taken through Value.getContext() did not exclude the creator context lock");
            assertTrue(acquired.get());
            PythonContextRegistry.unregisterContext(context);
        }
    }

    @Test
    void shutdownGateWaitsForBridgeInvocationTrackedThroughValueContext() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            PythonContextRegistry.registerContext(context);
            Value receiver = context.eval(PYTHON, """
                class Slow:
                    def run(self, entered, gate):
                        entered.run()
                        gate.acquire()
                        return 1
                Slow()
                """);
            CountDownLatch entered = new CountDownLatch(1);
            Semaphore gate = new Semaphore(0);
            AtomicReference<Object> outcome = new AtomicReference<>();
            Thread bridge = new Thread(() -> {
                try {
                    outcome.set(PythonInvocation.invokePythonMethod(receiver, "run", new Object[] {(Runnable) entered::countDown, gate}).asInt());
                } catch (Throwable e) {
                    outcome.set(e);
                }
            });
            bridge.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(1, PythonContextRegistry.activeExecutions());

            AtomicBoolean gateRan = new AtomicBoolean();
            PythonContextRegistry.onNoActiveExecutions(context, () -> gateRan.set(true));
            boolean ranWhileInFlight = gateRan.get();
            gate.release();
            bridge.join(5000);

            assertEquals(1, outcome.get());
            assertFalse(ranWhileInFlight, "shutdown gate ran while a bridge invocation was in flight");
            assertTrue(gateRan.get(), "shutdown gate did not run after the bridge invocation completed");
            assertEquals(0, PythonContextRegistry.activeExecutions());
            PythonContextRegistry.unregisterContext(context);
        }
    }

    @Test
    void aCloseSelectedWhileIdleRefusesNewExecutionsButNotNestedOnes() throws Exception {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(context);
        try {
            CountDownLatch entered = new CountDownLatch(1);
            Semaphore gate = new Semaphore(0);
            AtomicReference<Object> nestedOutcome = new AtomicReference<>();
            Thread holder = new Thread(() -> PythonContextRegistry.withExecutionFrame(context, () -> {
                entered.countDown();
                gate.acquireUninterruptibly();
                try {
                    // an entry nested in an execution that started before the close still runs
                    nestedOutcome.set(PythonContextRegistry.withExecutionFrame(context, () -> "nested"));
                } catch (RuntimeException e) {
                    nestedOutcome.set(e);
                }
                return null;
            }));
            holder.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            AtomicBoolean closed = new AtomicBoolean();
            PythonContextRegistry.closeWhenIdle(context, () -> closed.set(true));
            assertFalse(closed.get(), "the close ran while an execution was in flight");
            assertThrows(IllegalStateException.class, () -> PythonContextRegistry.withExecutionFrame(context, () -> 1),
                "a new outermost execution started on a closing context");
            assertFalse(PythonContextRegistry.tryWithExecutionFrame(context, () -> { }), "a callback ran on a closing context");
            gate.release();
            holder.join(5000);

            assertEquals("nested", nestedOutcome.get());
            assertTrue(closed.get(), "the close did not run once the context was idle");
            assertEquals(0, PythonContextRegistry.activeExecutions());
            PythonContextRegistry.unregisterContext(context);
            assertFalse(PythonContextRegistry.tryWithExecutionFrame(context, () -> { }), "a callback revived an unregistered context");
            assertNull(PythonContextRegistry.existingState(context), "the callback attempt recreated the context state");
        } finally {
            context.close(true);
        }
    }

    @Test
    void anIdleListenerThatThrowsAnErrorDoesNotStopTheOthers() throws Exception {
        try (Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
            CountDownLatch entered = new CountDownLatch(1);
            Semaphore gate = new Semaphore(0);
            AtomicReference<Throwable> outcome = new AtomicReference<>();
            Thread holder = new Thread(() -> {
                try {
                    PythonContextRegistry.withExecutionFrame(context, () -> {
                        entered.countDown();
                        gate.acquireUninterruptibly();
                        return null;
                    });
                } catch (Throwable e) {
                    outcome.set(e);
                }
            });
            holder.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            AtomicBoolean second = new AtomicBoolean();
            PythonContextRegistry.onNoActiveExecutions(context, () -> {
                throw new AssertionError("first listener");
            });
            PythonContextRegistry.onNoActiveExecutions(context, () -> second.set(true));
            gate.release();
            holder.join(5000);

            // the second listener ran although the first threw an Error, and the Error kept its type
            assertTrue(second.get());
            assertInstanceOf(AssertionError.class, outcome.get());
        }
    }

    @Test
    void aContextWhoseMainModuleFailsIsUnregisteredAndClosed() {
        try (Engine engine = GraalPyEngineFactory.buildPythonEngine()) {
            assertThrows(RuntimeException.class, () -> GraalPyContextFactory.buildContext(
                HostAccess.ALL, engine, getClass().getClassLoader(), new GraalPyContextConfiguration(), "failing_main.py"));
            // nothing registered for the engine: the failed context is gone, so the engine can close
            AtomicBoolean noContexts = new AtomicBoolean();
            PythonContextRegistry.onNoContexts(engine, () -> noContexts.set(true));
            assertTrue(noContexts.get(), "the failed context stayed registered");
        }
    }

    @Test
    void forgettingAContextLeavesTheOtherContextsExecutionsCounted() throws Exception {
        Context forgotten = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Context kept = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(forgotten);
        PythonContextRegistry.registerContext(kept);
        Semaphore gate = new Semaphore(0);
        CountDownLatch entered = new CountDownLatch(2);
        try {
            Thread first = new Thread(() -> PythonContextRegistry.withExecutionFrame(forgotten, () -> {
                entered.countDown();
                gate.acquireUninterruptibly();
                return null;
            }));
            Thread second = new Thread(() -> PythonContextRegistry.withExecutionFrame(kept, () -> {
                entered.countDown();
                gate.acquireUninterruptibly();
                return null;
            }));
            first.start();
            second.start();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertEquals(2, PythonContextRegistry.activeExecutions());

            PythonContextRegistry.forgetContext(forgotten);
            assertEquals(1, PythonContextRegistry.activeExecutions(), "forgetting a context did not remove its executions");
            gate.release(2);
            first.join(5000);
            second.join(5000);
            // the forgotten execution's exit must not be subtracted a second time
            assertEquals(0, PythonContextRegistry.activeExecutions());
        } finally {
            PythonContextRegistry.unregisterContext(kept);
            forgotten.close(true);
            kept.close(true);
        }
    }

    @Test
    void statesOfContextsClosedWithoutUnregisteringAreDropped() {
        Context plain = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Context cancelled = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        Context open = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        try {
            for (Context context : List.of(plain, cancelled, open)) {
                PythonContextRuntime.helper(context, "__micronaut_inspect_isclass");
                assertNotNull(PythonContextRegistry.existingState(context));
            }
            plain.close();
            cancelled.close(true);

            assertEquals(2, PythonContextRegistry.forgetClosedContexts());
            assertNull(PythonContextRegistry.existingState(plain), "a closed context kept its state");
            assertNull(PythonContextRegistry.existingState(cancelled), "a cancel-closed context kept its state");
            assertNotNull(PythonContextRegistry.existingState(open), "an open context lost its state");

            // the sweep also runs by itself when a context is seen for the first time
            open.close();
            try (Context another = Context.newBuilder(PYTHON).allowAllAccess(true).build()) {
                PythonContextRegistry.state(another);
                assertNull(PythonContextRegistry.existingState(open), "a new context did not trigger the sweep");
                PythonContextRegistry.unregisterContext(another);
            }
        } finally {
            for (Context context : List.of(plain, cancelled, open)) {
                PythonContextRegistry.unregisterContext(context);
                context.close(true);
            }
        }
    }

    @Test
    void executionFrameUnwindsWhenTheContextCannotBeEntered() {
        Context context = Context.newBuilder(PYTHON).allowAllAccess(true).build();
        PythonContextRegistry.registerContext(context);
        try {
            assertEquals(1, PythonContextRegistry.withExecutionFrame(context, () -> 1));
            context.close();
            // entering a closed context fails: the counters must not keep the failed frame
            assertThrows(IllegalStateException.class, () -> PythonContextRegistry.withExecutionFrame(context, () -> 1));
            assertEquals(0, PythonContextRegistry.activeExecutions());
        } finally {
            PythonContextRegistry.unregisterContext(context);
        }
    }
}
