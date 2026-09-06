/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ScopedValue.CallableOp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The JVM-wide registry of GraalPy contexts known to the Python runtime.
 * <p>
 * Every context that runs host-initiated Python code has one {@link ContextState}: the per-context
 * monitor, the caches of helper functions and resolved classes, the number of executions in flight and
 * the listeners waiting for the context to become idle or to be closed. The registry is keyed by
 * {@link Context#equals(Object)} so the creator instance and the views returned by
 * {@link Value#getContext()} share one entry. It is deliberately static: contexts are JVM objects and
 * the shutdown gates must see every execution, whichever application context started it.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
final class PythonContextRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PythonContextRegistry.class);
    private static final AtomicInteger ACTIVE_EXECUTIONS = new AtomicInteger();
    private static final Object LOCK = new Object();
    private static final HashMap<Context, ContextState> CONTEXT_STATES = new HashMap<>();
    private static final ScopedValue<ExecutionFrame> CURRENT_EXECUTION = ScopedValue.newInstance();

    private PythonContextRegistry() {
    }

    /**
     * The state of a context, created on first use.
     *
     * @param context The context
     * @return The state
     */
    static ContextState state(Context context) {
        ContextState existing;
        synchronized (LOCK) {
            existing = CONTEXT_STATES.get(context);
        }
        if (existing != null) {
            return existing;
        }
        // a context seen for the first time: the moment to drop the states of contexts that were
        // closed without being unregistered, so an embedder's closed contexts are released too
        forgetClosedContexts();
        synchronized (LOCK) {
            return CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
        }
    }

    /**
     * The state of a context when one exists.
     *
     * @param context The context
     * @return The state, or {@code null} when the context is unknown
     */
    static @Nullable ContextState existingState(Context context) {
        synchronized (LOCK) {
            return CONTEXT_STATES.get(context);
        }
    }

    /**
     * Register a context for execution and shared-engine shutdown tracking.
     *
     * @param context The GraalPy context being tracked
     */
    static void registerContext(Context context) {
        state(context).enterable.set(context);
    }

    /**
     * Remove a context from execution tracking and notify listeners waiting for its removal.
     * <p>
     * This is part of shutdown coordination; it clears cached helper/member state before the
     * context can be closed and releases shared-engine shutdown gates that include this context.
     *
     * @param context The GraalPy context being removed
     */
    static void unregisterContext(Context context) {
        List<Runnable> listeners;
        synchronized (LOCK) {
            ContextState contextState = CONTEXT_STATES.remove(context);
            if (contextState != null) {
                listeners = List.copyOf(contextState.noContextListeners);
                // executions still in flight leave the aggregate now; their exit finds no state
                ACTIVE_EXECUTIONS.addAndGet(-contextState.activeExecutions);
                contextState.clear();
            } else {
                listeners = List.of();
            }
        }
        runNoActiveExecutionsListeners(listeners);
    }

    /**
     * Drop the state of a context without running its listeners.
     * <p>
     * Used when the primary context of an application is reset: its executions leave the aggregate
     * count, while pooled and event-loop contexts stay tracked until they are closed and unregistered,
     * so shutdown gates waiting for their executions keep waiting.
     *
     * @param context The context to forget
     */
    static void forgetContext(Context context) {
        synchronized (LOCK) {
            ContextState state = CONTEXT_STATES.remove(context);
            if (state != null) {
                ACTIVE_EXECUTIONS.addAndGet(-state.activeExecutions);
                state.clear();
            }
        }
    }

    /**
     * Mark a context as actively executing host-initiated Python code.
     * <p>
     * Callers must pair this with {@link #exitExecution(Context)} unless they use
     * {@link #withExecutionFrame(Context, CallableOp)}.
     *
     * @param context The context entering Python execution
     */
    static void enterExecution(Context context) {
        if (!tryEnterExecution(context, false, true)) {
            throw new IllegalStateException("The Python context is closing");
        }
    }

    /**
     * Count an execution under the registry lock, where the closing state is decided too.
     *
     * @param context The context
     * @param nested Whether the calling frame already executes in this context: nested entries of
     * an execution that started before the close are allowed to finish
     * @param createState Whether a context seen for the first time gets a state; the Netty callback
     * path passes {@code false} so an unregistered (closed) context is not revived
     * @return {@code false} when the context is closing, or untracked and {@code createState} is false
     */
    private static boolean tryEnterExecution(Context context, boolean nested, boolean createState) {
        synchronized (LOCK) {
            ContextState state = createState
                ? CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState())
                : CONTEXT_STATES.get(context);
            if (state == null || (state.closing && !nested)) {
                return false;
            }
            state.activeExecutions++;
            ACTIVE_EXECUTIONS.incrementAndGet();
            return true;
        }
    }

    /**
     * Run an operation inside the current lexical execution frame, creating one when needed.
     * <p>
     * The frame lets nested generated bridge calls defer shutdown listeners until the outermost
     * Python execution has unwound, while still decrementing per-context counters for each entry.
     *
     * @param ctx The context used by this execution
     * @param operation The operation to run
     * @param <T> The operation result type
     * @param <X> The checked exception type the operation may throw
     * @return The operation result
     * @throws X When the operation throws
     */
    static <T, X extends Throwable> T withExecutionFrame(Context ctx, CallableOp<T, X> operation) throws X {
        if (CURRENT_EXECUTION.isBound()) {
            return runWithExecutionFrame(ctx, CURRENT_EXECUTION.get(), operation);
        }
        return ScopedValue.where(CURRENT_EXECUTION, new ExecutionFrame()).call(() -> runWithExecutionFrame(ctx, CURRENT_EXECUTION.get(), operation));
    }

    /**
     * Run a callback inside an execution frame only if its context is still tracked and not closing.
     * <p>
     * The check and the execution count are one step under the registry lock, so a close that is
     * selected once the context is idle cannot slip between them.
     *
     * @param ctx The context of the callback
     * @param operation The callback
     * @return {@code false} when the callback was skipped because the context is closing or closed
     */
    static boolean tryWithExecutionFrame(Context ctx, Runnable operation) {
        CallableOp<Boolean, RuntimeException> run = () -> {
            ExecutionFrame frame = CURRENT_EXECUTION.get();
            boolean nested = frame.contexts.contains(ctx);
            if (!tryEnterExecution(ctx, nested, false)) {
                return false;
            }
            runEnteredExecutionFrame(ctx, frame, () -> {
                operation.run();
                return null;
            });
            return true;
        };
        if (CURRENT_EXECUTION.isBound()) {
            return run.call();
        }
        return ScopedValue.where(CURRENT_EXECUTION, new ExecutionFrame()).call(run);
    }

    private static <T, X extends Throwable> T runWithExecutionFrame(Context ctx, ExecutionFrame frame, CallableOp<T, X> operation) throws X {
        boolean nested = frame.contexts.contains(ctx);
        if (!tryEnterExecution(ctx, nested, true)) {
            throw new IllegalStateException("The Python context is closing");
        }
        return runEnteredExecutionFrame(ctx, frame, operation);
    }

    /**
     * {@link #withExecutionFrame} for a context that must still be tracked: an unregistered (closed)
     * context is refused instead of getting a new state.
     *
     * @param ctx The context
     * @param operation The operation
     * @param <T> The result type
     * @param <X> The exception type
     * @return The result
     * @throws X When the operation throws
     */
    static <T, X extends Throwable> T withTrackedExecutionFrame(Context ctx, CallableOp<T, X> operation) throws X {
        CallableOp<T, X> run = () -> {
            ExecutionFrame frame = CURRENT_EXECUTION.get();
            if (!tryEnterExecution(ctx, frame.contexts.contains(ctx), false)) {
                throw new IllegalStateException("The Python context is closing or closed");
            }
            return runEnteredExecutionFrame(ctx, frame, operation);
        };
        if (CURRENT_EXECUTION.isBound()) {
            return run.call();
        }
        return ScopedValue.where(CURRENT_EXECUTION, new ExecutionFrame()).call(run);
    }

    private static <T, X extends Throwable> T runEnteredExecutionFrame(Context ctx, ExecutionFrame frame, CallableOp<T, X> operation) throws X {
        frame.contexts.add(ctx);
        Context entered = null;
        try {
            // a closed or cancelled context throws on enter: the frame and the counters must still unwind
            entered = enterIfPossible(ctx);
            return operation.call();
        } finally {
            if (entered != null) {
                entered.leave();
            }
            exitExecutionFrame(ctx, frame);
        }
    }

    /**
     * Enter the context for the duration of an execution frame when an enterable instance is known.
     * <p>
     * {@link Value#getContext()} returns a view that cannot be entered; the creator instance recorded by
     * {@link #registerContext(Context)} is used instead. Contexts that were never registered are probed
     * once and the outcome is remembered, so no exception is created per bridge call.
     *
     * @param ctx The context of the execution
     * @return The entered context instance, or {@code null} when the context could not be entered
     */
    private static @Nullable Context enterIfPossible(Context ctx) {
        ContextState state = state(ctx);
        Context enterable = state.enterable.get();
        if (enterable == null) {
            if (state.enterUnsupported) {
                return null;
            }
            try {
                ctx.enter();
                state.enterable.set(ctx);
                return ctx;
            } catch (IllegalStateException e) {
                state.enterUnsupported = true;
                return null;
            }
        }
        enterable.enter();
        return enterable;
    }

    /**
     * Mark a context execution as complete and run any listeners whose context no longer has
     * active executions.
     *
     * @param context The context leaving Python execution
     */
    static void exitExecution(Context context) {
        List<Runnable> listeners = new ArrayList<>();
        synchronized (LOCK) {
            ContextState state = CONTEXT_STATES.get(context);
            // an execution whose context was forgotten or unregistered left the aggregate at that time
            if (state != null && state.activeExecutions > 0) {
                state.activeExecutions--;
                ACTIVE_EXECUTIONS.updateAndGet(value -> Math.max(0, value - 1));
                if (state.activeExecutions == 0 && !state.noActiveExecutionsListeners.isEmpty()) {
                    listeners.addAll(state.noActiveExecutionsListeners);
                    state.noActiveExecutionsListeners.clear();
                }
            }
        }
        runNoActiveExecutionsListeners(listeners);
    }

    @SuppressWarnings("ReferenceEquality")
    private static void exitExecutionFrame(Context ctx, ExecutionFrame frame) {
        List<Context> contexts = frame.contexts;
        if (contexts.isEmpty()) {
            exitExecution(ctx);
            return;
        }
        Context removed = contexts.removeLast();
        if (removed != ctx) {
            contexts.remove(ctx);
        }
        exitExecution(ctx);
        if (contexts.isEmpty()) {
            runExecutionExitListeners(frame);
        }
    }

    private static void runExecutionExitListeners(ExecutionFrame frame) {
        List<Runnable> listeners = frame.exitListeners;
        if (listeners.isEmpty()) {
            return;
        }
        List<Runnable> snapshot = List.copyOf(listeners);
        listeners.clear();
        runNoActiveExecutionsListeners(snapshot);
    }

    /**
     * Return the aggregate number of active Python executions across all registered contexts.
     * <p>
     * This counter is incremented by {@link #enterExecution(Context)} and decremented by
     * {@link #exitExecution(Context)} for every generated bridge entry into Python. It is deliberately
     * aggregate-only: context-local counts are stored in {@link ContextState} for shutdown gates.
     * <p>
     * Maintainers should treat this method as an observability hook for tests and diagnostics, not
     * as a synchronization primitive. Use one of the {@code onNoActiveExecutions} registration methods
     * when cleanup must wait for a safe idle point.
     *
     * @return The number of active Python executions known to the runtime
     */
    /**
     * Drop the states of contexts that were closed without being unregistered: contexts the runtime
     * did not create (tests, embedders) get a state on first use and nothing else removes it, and a
     * state keeps the closed context's helper values, and with them its heap, alive.
     *
     * @return The number of states dropped
     */
    static int forgetClosedContexts() {
        // the probe is a context operation: never under the registry lock
        Map<Context, ContextState> snapshot;
        synchronized (LOCK) {
            snapshot = new HashMap<>(CONTEXT_STATES);
        }
        List<Context> closed = new ArrayList<>();
        for (Context context : snapshot.keySet()) {
            if (isClosed(context)) {
                closed.add(context);
            }
        }
        int dropped = 0;
        synchronized (LOCK) {
            for (Context context : closed) {
                ContextState state = CONTEXT_STATES.get(context);
                // only the state probed, not one recreated meanwhile
                if (state != null && state == snapshot.get(context)) {
                    CONTEXT_STATES.remove(context);
                    ACTIVE_EXECUTIONS.addAndGet(-state.activeExecutions);
                    state.clear();
                    dropped++;
                }
            }
        }
        return dropped;
    }

    private static boolean isClosed(Context context) {
        try {
            context.getBindings(PythonContextRuntime.PYTHON);
            return false;
        } catch (IllegalStateException e) {
            // "The Context is already closed"; a context in use from another thread is not closed
            return e.getMessage() != null && e.getMessage().contains("closed");
        } catch (PolyglotException e) {
            // a context closed with cancellation reports "Context execution was cancelled"
            return e.isCancelled();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Run every action even when one fails: the remaining actions run before the failure
     * propagates with its own type, whatever it is, so one failing close never keeps the next
     * gate waiting. When several fail, the last failure is the one seen.
     *
     * @param actions The actions
     */
    static void runEach(List<Runnable> actions) {
        runFrom(actions, 0);
    }

    private static void runFrom(List<Runnable> actions, int index) {
        if (index >= actions.size()) {
            return;
        }
        try {
            actions.get(index).run();
        } finally {
            // the failure, whatever its type, propagates once the rest have run
            runFrom(actions, index + 1);
        }
    }

    /**
     * Whether the calling thread is inside an execution frame; Netty callbacks must be.
     *
     * @return {@code true} when a frame with at least one context is bound
     */
    static boolean inExecutionFrame() {
        return CURRENT_EXECUTION.isBound() && !CURRENT_EXECUTION.get().contexts.isEmpty();
    }

    static int activeExecutions() {
        return ACTIVE_EXECUTIONS.get();
    }

    /**
     * Register a listener to run when the supplied context has no active Python executions.
     * <p>
     * The listener runs synchronously when the context is already idle. Otherwise it is stored on
     * the context state and drained by the {@link #exitExecution(Context)} call that decrements the
     * context-local execution count to zero. This is the context-scoped shutdown gate used before a
     * GraalPy context can be closed.
     * <p>
     * Listener registration and counter checks are performed under the registry lock so a new
     * listener cannot miss the transition from active to idle.
     *
     * @param context The context to observe
     * @param listener The listener to run when the context is idle
     */
    static void onNoActiveExecutions(Context context, Runnable listener) {
        onNoActiveExecutions(context, listener, false);
    }

    /**
     * Register the listener that closes the context once it is idle.
     * <p>
     * The context is marked closing under the registry lock together with the idle check, so no new
     * outermost execution can start between the check and the close; entries nested in an execution
     * that started earlier still complete.
     *
     * @param context The context to close
     * @param close The listener that closes it
     */
    static void closeWhenIdle(Context context, Runnable close) {
        onNoActiveExecutions(context, close, true);
    }

    private static void onNoActiveExecutions(Context context, Runnable listener, boolean closing) {
        boolean runNow;
        synchronized (LOCK) {
            ContextState state = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
            if (closing) {
                state.closing = true;
            }
            runNow = state.activeExecutions == 0;
            if (!runNow) {
                state.noActiveExecutionsListeners.add(listener);
            }
        }
        if (runNow) {
            listener.run();
        }
    }

    /**
     * Register a listener to run after the current execution frame exits and the context is idle.
     * <p>
     * This is used during bean/context destruction so cleanup cannot run in the middle of a nested
     * generated bridge invocation that is still unwinding. When a scoped execution frame is active,
     * the listener is first attached to that frame and only then registered with
     * {@link #onNoActiveExecutions(Context, Runnable)}. Without that two-step handoff, a nested call
     * could make the context appear idle before the outer bridge call has restored its Java-side state.
     *
     * @param context The context to observe
     * @param listener The listener to run after the current frame and active executions complete
     */
    static void onNoActiveExecutionsAfterCurrentFrame(Context context, Runnable listener) {
        onNoActiveExecutionsAfterCurrentFrame(context, listener, false);
    }

    /**
     * {@link #closeWhenIdle(Context, Runnable)} deferred past the current execution frame.
     *
     * @param context The context to close
     * @param close The listener that closes it
     */
    static void closeWhenIdleAfterCurrentFrame(Context context, Runnable close) {
        onNoActiveExecutionsAfterCurrentFrame(context, close, true);
    }

    private static void onNoActiveExecutionsAfterCurrentFrame(Context context, Runnable listener, boolean closing) {
        if (CURRENT_EXECUTION.isBound() && !CURRENT_EXECUTION.get().contexts.isEmpty()) {
            CURRENT_EXECUTION.get().exitListeners.add(() -> onNoActiveExecutions(context, listener, closing));
            return;
        }
        onNoActiveExecutions(context, listener, closing);
    }

    /**
     * Register a listener to run once every distinct context in the collection is idle.
     * <p>
     * Duplicate contexts are collapsed so a pooled context cannot make the listener wait
     * for the same active execution more than once. For active contexts, this method installs a small
     * gate listener on each context; the original listener runs only after the final active context
     * reaches zero executions. If no supplied context is active, the listener runs immediately.
     * <p>
     * This overload is used by pooled shutdown because the pool owns multiple contexts and all of
     * them must be idle before cached values and GraalPy contexts are discarded.
     *
     * @param contexts The contexts to observe
     * @param listener The listener to run when every observed context is idle
     */
    static void onNoActiveExecutions(Collection<Context> contexts, Runnable listener) {
        onNoActiveExecutions(contexts, listener, false);
    }

    /**
     * Register the listener that closes every context in the collection once all are idle; each
     * context is marked closing so no new outermost execution starts on it in the meantime.
     *
     * @param contexts The contexts to close
     * @param close The listener that closes them
     */
    static void closeWhenIdle(Collection<Context> contexts, Runnable close) {
        onNoActiveExecutions(contexts, close, true);
    }

    private static void onNoActiveExecutions(Collection<Context> contexts, Runnable listener, boolean closing) {
        List<Context> activeContexts;
        synchronized (LOCK) {
            HashSet<Context> seen = new HashSet<>();
            if (closing) {
                for (Context context : contexts) {
                    CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState()).closing = true;
                }
            }
            activeContexts = contexts.stream()
                .filter(seen::add)
                .filter(ctx -> {
                    ContextState state = CONTEXT_STATES.get(ctx);
                    return state != null && state.activeExecutions > 0;
                })
                .toList();
            if (activeContexts.isEmpty()) {
                activeContexts = List.of();
            } else {
                AtomicInteger remaining = new AtomicInteger(activeContexts.size());
                Runnable gate = () -> {
                    if (remaining.decrementAndGet() == 0) {
                        listener.run();
                    }
                };
                for (Context activeContext : activeContexts) {
                    CONTEXT_STATES.computeIfAbsent(activeContext, ignored -> new ContextState()).noActiveExecutionsListeners.add(gate);
                }
            }
        }
        if (activeContexts.isEmpty()) {
            listener.run();
        }
    }

    /**
     * Register a listener to run when no registered contexts remain for the engine.
     * <p>
     * Context ownership is derived from {@link Context#getEngine()}; each currently matching context
     * receives a shared removal gate. Context shutdown waits for active executions before unregistering,
     * so this is also the safe hook for engine cleanup.
     * <p>
     * The listener runs immediately when no registered context uses the engine. Registration and gate
     * installation happen under the registry lock so a concurrent unregister cannot miss the listener.
     *
     * @param engine The engine to observe
     * @param listener The listener to run when the engine no longer owns contexts
     */
    static void onNoContexts(Engine engine, Runnable listener) {
        boolean runNow;
        synchronized (LOCK) {
            List<ContextState> states = CONTEXT_STATES.entrySet().stream()
                .filter(entry -> entry.getKey().getEngine().equals(engine))
                .map(Map.Entry::getValue)
                .toList();
            runNow = states.isEmpty();
            if (!runNow) {
                AtomicInteger remaining = new AtomicInteger(states.size());
                Runnable gate = () -> {
                    if (remaining.decrementAndGet() == 0) {
                        listener.run();
                    }
                };
                states.forEach(state -> state.noContextListeners.add(gate));
            }
        }
        if (runNow) {
            listener.run();
        }
    }

    private static void runNoActiveExecutionsListeners(List<Runnable> listeners) {
        if (listeners.isEmpty()) {
            return;
        }
        // every listener runs: one failing close must not keep the next context or engine gate waiting
        deferNoActiveExecutionListener(() -> runEach(listeners));
    }

    /**
     * Dispatch a no-active-executions listener.
     * <p>
     * The default implementation runs inline to preserve ordering with context close operations.
     * The package-private boundary keeps a single future extension point if listener dispatch needs
     * to move to an executor.
     *
     * @param listener The listener to dispatch
     */
    static void deferNoActiveExecutionListener(Runnable listener) {
        listener.run();
    }

    /**
     * Run an action while holding the per-context monitor used for helper initialization and other
     * context-local mutable runtime state.
     *
     * @param context The context whose state should be locked
     * @param action The action to run while holding the lock
     * @param <T> The action result type
     * @return The action result
     */
    static <T> T withContextLock(Context context, Supplier<T> action) {
        synchronized (state(context).lock) {
            return action.get();
        }
    }

    /**
     * Run a void action while holding the per-context monitor.
     * <p>
     * This overload exists for call sites that need the same locking discipline as
     * {@link #withContextLock(Context, Supplier)} but do not produce a value.
     *
     * @param context The context whose state should be locked
     * @param action The action to run while holding the lock
     */
    static void withContextLock(Context context, Runnable action) {
        withContextLock(context, () -> {
            action.run();
            return null;
        });
    }

    /**
     * The runtime state of one GraalPy context.
     */
    static final class ContextState {
        final Object lock = new Object();
        /** The enterable creator instance of this context, when known. */
        final AtomicReference<@Nullable Context> enterable = new AtomicReference<>();
        /** Whether entering was probed on an instance that cannot be entered. */
        volatile boolean enterUnsupported;
        /** Host members assigned to startup-context objects, mirrored into event-loop contexts. */
        final IdentityHashMap<Value, Map<String, Object>> asyncMembers = new IdentityHashMap<>();
        /** Helper functions and cached pooled values, keyed by name or expression. */
        final Map<String, Value> helpers = new ConcurrentHashMap<>();
        /** The micronaut_runtime module imported into this context, once resolved. */
        final AtomicReference<@Nullable Value> runtimeModule = new AtomicReference<>();
        /** Python classes resolved in this context, keyed by their qualified name. */
        final Map<String, Value> classes = new ConcurrentHashMap<>();
        private final List<Runnable> noActiveExecutionsListeners = new ArrayList<>();
        private final List<Runnable> noContextListeners = new ArrayList<>();
        private int activeExecutions;
        /** Set once a close listener is registered: no new outermost execution may start. */
        private boolean closing;

        private void clear() {
            asyncMembers.clear();
            helpers.clear();
            classes.clear();
            runtimeModule.set(null);
            noActiveExecutionsListeners.clear();
            noContextListeners.clear();
            activeExecutions = 0;
            closing = false;
        }
    }

    private static final class ExecutionFrame {
        private final List<Context> contexts = new ArrayList<>();
        private final List<Runnable> exitListeners = new ArrayList<>();
    }
}
