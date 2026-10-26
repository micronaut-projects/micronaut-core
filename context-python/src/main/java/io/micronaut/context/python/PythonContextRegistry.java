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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
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
 * <p>
 * Entering and leaving an execution, which every call into Python does, takes no lock: the states are
 * looked up in a concurrent map, and each state counts its executions in one atomic word together with
 * whether the context is closing, whether a listener waits for it to become idle and whether the state
 * was dropped. Marking a context closing and counting an execution in are therefore one atomic step
 * each, so an outermost execution either counts before the close sees the context idle or is refused.
 * The registry lock guards the listeners, the creation and removal of states, and the transition to
 * idle when a listener waits for it.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
final class PythonContextRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(PythonContextRegistry.class);
    /** The executions of every context; a sum, never below the executions the states count. */
    private static final LongAdder ACTIVE_EXECUTIONS = new LongAdder();
    private static final Object LOCK = new Object();
    /** The states, read without the lock; created and removed under {@link #LOCK}. */
    private static final ConcurrentHashMap<Context, ContextState> CONTEXT_STATES = new ConcurrentHashMap<>();
    /** The contexts of each engine that are being built and not registered yet; guarded by {@link #LOCK}. */
    private static final HashMap<Engine, Integer> PENDING_BUILDS = new HashMap<>();
    /** The {@link #onNoContexts} listeners waiting for the builds of an engine to end; guarded by {@link #LOCK}. */
    private static final HashMap<Engine, List<Runnable>> AFTER_PENDING_BUILDS = new HashMap<>();
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
        ContextState existing = CONTEXT_STATES.get(context);
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
        return CONTEXT_STATES.get(context);
    }

    /**
     * Register a context for execution and shared-engine shutdown tracking.
     *
     * @param context The GraalPy context being tracked
     */
    static void registerContext(Context context) {
        ContextState state = state(context);
        state.enterable.set(context);
        state.registered = true;
    }

    /**
     * Record that a context of the engine is being built.
     * <p>
     * A context exists for the engine before {@link #registerContext} can see it: an engine closed in
     * between would close under the build. {@link #onNoContexts} therefore waits for the builds of the
     * engine to end, which callers mark with {@link #endContextBuild} once the context is registered or
     * the build failed.
     *
     * @param engine The engine the context is built on
     */
    static void beginContextBuild(Engine engine) {
        synchronized (LOCK) {
            PENDING_BUILDS.merge(engine, 1, Integer::sum);
        }
    }

    /**
     * Record that a build marked by {@link #beginContextBuild} ended, registered or failed.
     *
     * @param engine The engine the context was built on
     */
    static void endContextBuild(Engine engine) {
        List<Runnable> deferred;
        synchronized (LOCK) {
            Integer pending = PENDING_BUILDS.get(engine);
            if (pending == null) {
                return;
            }
            if (pending > 1) {
                PENDING_BUILDS.put(engine, pending - 1);
                return;
            }
            PENDING_BUILDS.remove(engine);
            deferred = AFTER_PENDING_BUILDS.remove(engine);
        }
        if (deferred != null) {
            // gate again, now on the contexts the builds registered
            deferred.forEach(listener -> onNoContexts(engine, listener));
        }
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
                // executions still in flight leave the aggregate now; their exit finds the state dropped
                ACTIVE_EXECUTIONS.add(-contextState.detach());
                contextState.clear(context);
            } else {
                listeners = List.of();
            }
        }
        runNoActiveExecutionsListeners(listeners);
    }

    /**
     * Drop the Python scoped proxies created in a context for the beans of an application that shuts
     * down while the context lives on (a reused context): the proxies, and the bean context they
     * resolve their targets through, are then no longer reachable from the context state. A proxy of
     * an application still running gets a new Python scoped proxy on its next use.
     *
     * @param context The context the application used
     */
    static void forgetScopedProxies(Context context) {
        ContextState state = CONTEXT_STATES.get(context);
        if (state != null) {
            synchronized (state.scopedProxies) {
                state.scopedProxies.clear();
            }
        }
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
                ACTIVE_EXECUTIONS.add(-state.detach());
                state.clear(context);
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
        if (tryEnterExecution(context, false, true) == null) {
            throw new IllegalStateException("The Python context is closing");
        }
    }

    /**
     * Count an execution in the state of its context, in the one atomic step that also reads whether
     * the context is closing (see {@link ContextState#tryEnter}). No lock is taken unless the context
     * is seen for the first time.
     *
     * @param context The context
     * @param nested Whether the calling frame already executes in this context: nested entries of
     * an execution that started before the close are allowed to finish
     * @param createState Whether a context seen for the first time gets a state; the Netty callback
     * path passes {@code false} so an unregistered (closed) context is not revived
     * @return The state the execution is counted in, or {@code null} when the context is closing, or
     * untracked and {@code createState} is false
     */
    private static @Nullable ContextState tryEnterExecution(Context context, boolean nested, boolean createState) {
        while (true) {
            ContextState state = CONTEXT_STATES.get(context);
            if (state == null) {
                if (!createState) {
                    return null;
                }
                synchronized (LOCK) {
                    state = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
                }
            }
            // counted in the aggregate first: it never reads below what the states count
            ACTIVE_EXECUTIONS.increment();
            int entered = state.tryEnter(nested);
            if (entered == ContextState.ENTERED) {
                return state;
            }
            ACTIVE_EXECUTIONS.decrement();
            if (entered == ContextState.REFUSED) {
                return null;
            }
            // the state was dropped meanwhile: count the execution in the state the context has now
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
            ContextState state = tryEnterExecution(ctx, nested, false);
            if (state == null) {
                return false;
            }
            runEnteredExecutionFrame(ctx, state, frame, () -> {
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
        ContextState state = tryEnterExecution(ctx, nested, true);
        if (state == null) {
            throw new IllegalStateException("The Python context is closing");
        }
        return runEnteredExecutionFrame(ctx, state, frame, operation);
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
            ContextState state = tryEnterExecution(ctx, frame.contexts.contains(ctx), false);
            if (state == null) {
                throw new IllegalStateException("The Python context is closing or closed");
            }
            return runEnteredExecutionFrame(ctx, state, frame, operation);
        };
        if (CURRENT_EXECUTION.isBound()) {
            return run.call();
        }
        return ScopedValue.where(CURRENT_EXECUTION, new ExecutionFrame()).call(run);
    }

    /**
     * The context this thread is already executing in, innermost first, or {@code null}.
     *
     * <p>A value belongs to the context it was created in, so host code reached from guest code has
     * to resolve a value in the context that called it rather than choose one. Choosing is not merely
     * wasteful: taking a second context from the pool while holding one deadlocks, because the
     * borrow waits for a context that the waiting threads are themselves holding.
     *
     * @return The innermost entered context, or {@code null} when this thread is not in one
     */
    static @Nullable Context currentContext() {
        if (!CURRENT_EXECUTION.isBound()) {
            return null;
        }
        List<Context> contexts = CURRENT_EXECUTION.get().contexts;
        return contexts.isEmpty() ? null : contexts.get(contexts.size() - 1);
    }

    private static <T, X extends Throwable> T runEnteredExecutionFrame(Context ctx, ContextState state, ExecutionFrame frame, CallableOp<T, X> operation) throws X {
        frame.contexts.add(ctx);
        Context entered = null;
        try {
            // a closed or cancelled context throws on enter: the frame and the counters must still unwind
            entered = enterIfPossible(ctx, state);
            return operation.call();
        } finally {
            if (entered != null) {
                entered.leave();
            }
            exitExecutionFrame(ctx, state, frame);
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
     * @param state The state the execution is counted in
     * @return The entered context instance, or {@code null} when the context could not be entered
     */
    private static @Nullable Context enterIfPossible(Context ctx, ContextState state) {
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
        ContextState state = CONTEXT_STATES.get(context);
        if (state != null) {
            exitExecution(state);
        }
    }

    /**
     * Count an execution out of the state it was counted in. The context becoming idle while a
     * listener waits for it is the only transition that takes the registry lock.
     *
     * @param state The state
     */
    private static void exitExecution(ContextState state) {
        long left = state.tryExit();
        if (left < 0) {
            // an execution whose state was dropped (the context forgotten or unregistered) left the aggregate then
            return;
        }
        ACTIVE_EXECUTIONS.decrement();
        if (ContextState.idleAndAwaited(left)) {
            runNoActiveExecutionsListeners(drainIdle(state));
        }
    }

    /**
     * The listeners waiting for a context to become idle, taken under the registry lock when the
     * context is idle still: an execution that started since the last one ended leaves them to the
     * exit that makes the context idle again.
     */
    private static List<Runnable> drainIdle(ContextState state) {
        synchronized (LOCK) {
            if (!state.isIdle()) {
                return List.of();
            }
            state.stopAwaiting();
            if (state.noActiveExecutionsListeners.isEmpty()) {
                return List.of();
            }
            List<Runnable> listeners = List.copyOf(state.noActiveExecutionsListeners);
            state.noActiveExecutionsListeners.clear();
            return listeners;
        }
    }

    @SuppressWarnings("ReferenceEquality")
    private static void exitExecutionFrame(Context ctx, ContextState state, ExecutionFrame frame) {
        List<Context> contexts = frame.contexts;
        if (contexts.isEmpty()) {
            exitExecution(state);
            return;
        }
        Context removed = contexts.removeLast();
        if (removed != ctx) {
            contexts.remove(ctx);
        }
        exitExecution(state);
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
        Map<Context, ContextState> snapshot = new HashMap<>();
        synchronized (LOCK) {
            CONTEXT_STATES.forEach((context, state) -> {
                if (isProbeable(state)) {
                    snapshot.put(context, state);
                }
            });
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
                    ACTIVE_EXECUTIONS.add(-state.detach());
                    state.clear(context);
                    dropped++;
                }
            }
        }
        return dropped;
    }

    /**
     * Whether the sweep may probe the context of a state. The probe enters the context on the
     * sweeping thread, which is not the thread that owns it, and entering a context that is closing
     * fails its close. Registered contexts are released by {@link #unregisterContext(Context)} and
     * are never probed; neither are states with executions or a close in progress.
     *
     * @param state The state, read under the registry lock
     * @return Whether the context of the state may be probed
     */
    private static boolean isProbeable(ContextState state) {
        return !state.registered && state.isIdleAndOpen();
    }

    private static boolean isClosed(Context context) {
        try {
            // not getBindings: that initializes Python in a context its owner may be initializing,
            // and the owner's evaluations then miss the main module the bindings expose
            context.getPolyglotBindings();
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

    /**
     * The number of active executions of one context.
     *
     * @param context The context
     * @return The count, or 0 for an unknown context
     */
    static int activeExecutions(Context context) {
        ContextState state = CONTEXT_STATES.get(context);
        return state == null ? 0 : state.activeExecutions();
    }

    static int activeExecutions() {
        return (int) Math.max(0, ACTIVE_EXECUTIONS.sum());
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
            // the listener is in place before the count is read: an exit that makes the context idle
            // afterwards finds it, and one that made it idle before leaves the count at zero here
            state.noActiveExecutionsListeners.add(listener);
            runNow = state.await(closing);
            if (runNow) {
                state.noActiveExecutionsListeners.remove(state.noActiveExecutionsListeners.size() - 1);
                if (state.noActiveExecutionsListeners.isEmpty()) {
                    state.stopAwaiting();
                }
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
        // one count for each active context, and one this registration drops once the gates are in place
        AtomicInteger remaining = new AtomicInteger(1);
        Runnable gate = () -> {
            if (remaining.decrementAndGet() == 0) {
                listener.run();
            }
        };
        synchronized (LOCK) {
            HashSet<Context> seen = new HashSet<>();
            for (Context context : contexts) {
                if (!seen.add(context)) {
                    continue;
                }
                ContextState state = closing
                    ? CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState())
                    : CONTEXT_STATES.get(context);
                if (state == null) {
                    continue;
                }
                remaining.incrementAndGet();
                state.noActiveExecutionsListeners.add(gate);
                if (state.await(closing)) {
                    // idle: no exit will run the gate
                    state.noActiveExecutionsListeners.remove(state.noActiveExecutionsListeners.size() - 1);
                    if (state.noActiveExecutionsListeners.isEmpty()) {
                        state.stopAwaiting();
                    }
                    remaining.decrementAndGet();
                }
            }
        }
        gate.run();
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
     * While a context of the engine is being built (see {@link #beginContextBuild}) the gate is installed
     * once the build ends, so it includes the context the build registers.
     *
     * @param engine The engine to observe
     * @param listener The listener to run when the engine no longer owns contexts
     */
    static void onNoContexts(Engine engine, Runnable listener) {
        boolean runNow;
        synchronized (LOCK) {
            if (PENDING_BUILDS.containsKey(engine)) {
                AFTER_PENDING_BUILDS.computeIfAbsent(engine, ignored -> new ArrayList<>()).add(listener);
                return;
            }
            // registered contexts only: a state that a late lookup recreated for a context already
            // closed and unregistered is never unregistered again, and must not hold the engine open
            List<ContextState> states = CONTEXT_STATES.entrySet().stream()
                .filter(entry -> entry.getValue().registered && entry.getKey().getEngine().equals(engine))
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
        /** {@link #tryEnter} counted the execution. */
        static final int ENTERED = 0;
        /** {@link #tryEnter} refused the execution: the context is closing. */
        static final int REFUSED = 1;
        /** {@link #tryEnter} found the state dropped: the context has another state, or none. */
        static final int DROPPED = 2;
        /** Set once a close listener is registered: no new outermost execution may start. */
        private static final long CLOSING = 1L << 62;
        /** Set while a listener waits for the context to become idle: the exit to idle takes the lock. */
        private static final long AWAITED = 1L << 61;
        /** Set when the state is dropped from the registry: its executions left the aggregate then. */
        private static final long DROPPED_STATE = 1L << 60;
        /** The executions in flight. */
        private static final long COUNT = DROPPED_STATE - 1;

        final Object lock = new Object();
        /** The enterable creator instance of this context, when known. */
        final AtomicReference<@Nullable Context> enterable = new AtomicReference<>();
        /** Whether the runtime created the context and unregisters it when closing it. */
        volatile boolean registered;
        /** Whether entering was probed on an instance that cannot be entered. */
        volatile boolean enterUnsupported;
        /** Whether a Python class declares coroutine methods, keyed by its class cache key. */
        final Map<String, Boolean> coroutineClasses = new ConcurrentHashMap<>();
        /** Class-bound allocators, keyed by the precomputed class reference cache key. */
        final Map<String, Value> uninitializedInstanceFactories = new ConcurrentHashMap<>();
        /** Successfully prepared introduction classes, keyed by the precomputed class reference cache key. */
        final Set<String> preparedIntroductionClasses = ConcurrentHashMap.newKeySet();
        /**
         * Whether a member of a Python class is an async function, by class and member name: read for every call
         * a caller in another context makes through a wrapper.
         */
        final Map<Value, Map<String, String>> asyncFunctionKinds = new ConcurrentHashMap<>();
        /**
         * The pooled beans that have an instance in this context, so that closing it can tell them to
         * drop it. The instances themselves live on the bean, which is what makes a collected bean take
         * its instances with it; this is the other half, and neither side can be weak on its own,
         * because a stored instance reaches its bean (through the host-object back-reference) and a
         * bean reaches the contexts it has served.
         *
         * <p>Weakly held, so a prototype bean collected while this context lives on is not kept
         * reachable by having been served here.
         */
        final Set<PythonPooledInstance> pooledHolders = Collections.newSetFromMap(new WeakHashMap<>());
        /** Helper functions and cached pooled values, keyed by name or expression. */
        final Map<String, Value> helpers = new ConcurrentHashMap<>();
        /** The micronaut_runtime module imported into this context, once resolved. */
        final AtomicReference<@Nullable Value> runtimeModule = new AtomicReference<>();
        /** Python classes resolved in this context, keyed by their qualified name. */
        final Map<String, Value> classes = new ConcurrentHashMap<>();
        /** The Python scoped proxies standing in for generated AOP proxies of Python classes, by proxy instance. */
        final IdentityHashMap<Object, Value> scopedProxies = new IdentityHashMap<>();
        /**
         * The object a wrapper of another context is seen as in this one, by the object the wrapper
         * holds. Keyed by identity, as {@link Value#equals(Object)} compares the guest objects, so one
         * Python object of the other context is one object here however often the view asks for it and
         * whichever wrapper asks: the view hashes, compares and prints the same object every time.
         * Weakly keyed: the key is the value the wrapper itself holds, so the entry lives exactly as
         * long as that wrapper and nothing here keeps it alive.
         */
        final Map<Value, Value> viewedValues = Collections.synchronizedMap(new WeakHashMap<>());
        /** Guarded by {@link #LOCK}. */
        private final List<Runnable> noActiveExecutionsListeners = new ArrayList<>();
        /** Guarded by {@link #LOCK}. */
        private final List<Runnable> noContextListeners = new ArrayList<>();
        /** The executions in flight, with the {@link #CLOSING}, {@link #AWAITED} and {@link #DROPPED_STATE} flags. */
        private final AtomicLong executions = new AtomicLong();

        /**
         * Count an execution in, unless the context is closing and the execution is an outermost one.
         *
         * @param nested Whether the execution is nested in one of the same context
         * @return {@link #ENTERED}, {@link #REFUSED} or {@link #DROPPED}
         */
        int tryEnter(boolean nested) {
            long current = executions.get();
            while (true) {
                if ((current & DROPPED_STATE) != 0) {
                    return DROPPED;
                }
                if ((current & CLOSING) != 0 && !nested) {
                    return REFUSED;
                }
                long witness = executions.compareAndExchange(current, current + 1);
                if (witness == current) {
                    return ENTERED;
                }
                current = witness;
            }
        }

        /**
         * Count an execution out.
         *
         * @return The word after the exit, or {@code -1} when the state was dropped or counts no execution
         */
        long tryExit() {
            long current = executions.get();
            while (true) {
                if ((current & DROPPED_STATE) != 0 || (current & COUNT) == 0) {
                    return -1;
                }
                long witness = executions.compareAndExchange(current, current - 1);
                if (witness == current) {
                    return current - 1;
                }
                current = witness;
            }
        }

        /**
         * @param word The word an exit left
         * @return Whether the exit made the context idle while a listener waits for it
         */
        static boolean idleAndAwaited(long word) {
            return (word & COUNT) == 0 && (word & AWAITED) != 0;
        }

        /**
         * Mark that a listener waits for the context to become idle, and that the context closes when
         * {@code closing}; in one step with reading the count. Called under {@link #LOCK}.
         *
         * @param closing Whether the listener closes the context
         * @return Whether the context is idle now
         */
        boolean await(boolean closing) {
            long flags = AWAITED | (closing ? CLOSING : 0);
            return (executions.updateAndGet(word -> word | flags) & COUNT) == 0;
        }

        /**
         * No listener waits any longer. Called under {@link #LOCK}.
         */
        void stopAwaiting() {
            executions.updateAndGet(word -> word & ~AWAITED);
        }

        /**
         * Drop the state from the registry. Called under {@link #LOCK}.
         *
         * @return The executions it counted, which leave the aggregate
         */
        long detach() {
            return executions.getAndSet(DROPPED_STATE) & COUNT;
        }

        boolean isIdle() {
            long word = executions.get();
            return (word & COUNT) == 0 && (word & DROPPED_STATE) == 0;
        }

        boolean isIdleAndOpen() {
            return (executions.get() & (COUNT | CLOSING)) == 0;
        }

        int activeExecutions() {
            long word = executions.get();
            return (word & DROPPED_STATE) != 0 ? 0 : (int) (word & COUNT);
        }

        private void clear(Context owner) {
            // tell each pooled bean to forget this context before the state goes. A holder's map is a
            // ConcurrentHashMap, so this takes no monitor: clear() runs under the registry lock, and
            // taking a bean's lock here while a thread holding that bean's lock waits for the registry
            // lock is the deadlock this ordering avoids.
            for (PythonPooledInstance holder : List.copyOf(pooledHolders)) {
                holder.forget(owner);
            }
            pooledHolders.clear();
            coroutineClasses.clear();
            uninitializedInstanceFactories.clear();
            preparedIntroductionClasses.clear();
            asyncFunctionKinds.clear();
            helpers.clear();
            classes.clear();
            scopedProxies.clear();
            viewedValues.clear();
            runtimeModule.set(null);
            registered = false;
            noActiveExecutionsListeners.clear();
            noContextListeners.clear();
        }
    }

    private static final class ExecutionFrame {
        private final List<Context> contexts = new ArrayList<>();
        private final List<Runnable> exitListeners = new ArrayList<>();
    }
}
