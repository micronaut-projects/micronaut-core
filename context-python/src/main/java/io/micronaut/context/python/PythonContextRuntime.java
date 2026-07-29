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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import io.micronaut.scheduling.LoomSupport;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ScopedValue.CallableOp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Runtime coordination point for generated Python bridge classes.
 * <p>
 * This type owns access to the primary GraalPy context, resolves classes and scripts from the
 * primary context or a {@link PythonPool}, mirrors host members into event-loop contexts, executes
 * shared Python helper functions, and tracks active executions so contexts and engines can be
 * closed safely.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonContextRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(PythonContextRuntime.class);

    private static final String NEW_UNINITIALIZED_INSTANCE = "__micronaut_new_uninitialized_instance";
    private static final String SET_INSTANCE_PROPERTY = "__micronaut_set_instance_property";
    private static final Source NEW_UNINITIALIZED_INSTANCE_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_new_uninitialized_instance(cls):
            return object.__new__(cls)
        """, "micronaut-new-uninitialized-instance.py").cached(true).buildLiteral();
    private static final Source SET_INSTANCE_PROPERTY_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_set_instance_property(instance, name, value):
            object.__setattr__(instance, name, value)
            return instance
        """, "micronaut-set-instance-property.py").cached(true).buildLiteral();
    private static final Source FIND_CLASS_IN_PACKAGE_MODULES_SOURCE = Source.newBuilder(PYTHON, """
        import importlib
        import inspect
        import pkgutil

        def __micronaut_find_class_in_package_modules(package_name, class_name):
            package = importlib.import_module(package_name)
            package_path = getattr(package, "__path__", None)
            if package_path is None:
                return None
            for module_info in pkgutil.iter_modules(package_path):
                try:
                    module = importlib.import_module(package_name + "." + module_info.name)
                except Exception:
                    continue
                member = getattr(module, class_name, None)
                if inspect.isclass(member):
                    return member
            return None
        """, "micronaut-find-class-in-package-modules.py").cached(true).buildLiteral();
    private static final Source INSPECT_IS_CLASS_SOURCE = Source.newBuilder(PYTHON, """
        import inspect
        __micronaut_inspect_isclass = inspect.isclass
        """, "micronaut-inspect-is-class.py").cached(true).buildLiteral();
    private static final Source IMPORT_MODULE_SOURCE = Source.newBuilder(PYTHON, """
        import importlib
        __micronaut_import_module = importlib.import_module
        """, "micronaut-import-module.py").cached(true).buildLiteral();
    private static final Source RELOAD_MODULES_SOURCE = Source.newBuilder(PYTHON, """
        import importlib
        import sys
        for module in sys.modules.values():
            try:
                importlib.reload(module)
            except:
                pass
        """, "micronaut-reload-modules.py").cached(true).buildLiteral();

    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();
    private static volatile @Nullable Context context;
    private static volatile @Nullable ClassLoader contextClassLoader;
    private static volatile @Nullable PythonPool pythonPool;
    private static volatile @Nullable BeanProvider<ExecutorService> pooledExecutorServiceProvider;
    private static final AtomicInteger ACTIVE_EXECUTIONS = new AtomicInteger();
    private static final Object ACTIVE_EXECUTIONS_LOCK = new Object();
    private static final IdentityHashMap<Context, ContextState> CONTEXT_STATES = new IdentityHashMap<>();
    private static final ScopedValue<ExecutionFrame> CURRENT_EXECUTION = ScopedValue.newInstance();

    private PythonContextRuntime() {
    }

    /**
     * Pre-split Python class identity for generated bridge code.
     *
     * @param packageName The Python package, or {@code null}/{@code python} for top-level classes
     * @param rootName The top-level Python import/member name
     * @param nestedMemberNames The nested member names below the root class
     * @param displayName The class display name used in diagnostics
     * @param cacheKey The stable class cache key used by pooled contexts
     * @since 5.2.0
     */
    private static ContextState contextState(Context context) {
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            return CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
        }
    }

    private static @Nullable ContextState existingContextState(Context context) {
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            return CONTEXT_STATES.get(context);
        }
    }

    /**
     * Register the active Python pool after the pool bean decides whether pooling is enabled.
     * <p>
     * A {@code null} pool intentionally routes generated bridge calls back to the primary context.
     * This method stays package-private so only the pool lifecycle code can change global routing.
     *
     * @param pool The Python pool
     */
    static void setPythonPool(@Nullable PythonPool pool) {
        PythonContextRuntime.pythonPool = pool;
    }

    /**
     * Register the executor used when pooled Python execution is requested from a virtual thread.
     *
     * @param executorServiceProvider The executor provider, or {@code null} when unavailable
     */
    static void setPooledExecutorServiceProvider(@Nullable BeanProvider<ExecutorService> executorServiceProvider) {
        PythonContextRuntime.pooledExecutorServiceProvider = executorServiceProvider;
    }

    /**
     * Resolve a Python instance for the current asyncio event loop when one is active.
     *
     * @param fallback The startup-context instance
     * @param classReference The Python class reference
     * @return An event-loop-local instance, or the fallback when no event-loop context is active
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value asyncInstance(Value fallback, PythonClassReference classReference) {
        PythonPool pool = pythonPool;
        if (pool == null || isReuseContext()) {
            return fallback;
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop == null) {
            return fallback;
        }
        Value target = pool.getEventLoopClass(eventLoop, classReference);
        GraalPyRuntimeUtil.copyTransferableMembers(fallback, target);
        copyRememberedAsyncMembers(fallback, target);
        return target;
    }

    /**
     * Remember a host-side member assigned to a Python object so async event-loop contexts can mirror it.
     *
     * @param source The startup-context Python object.
     * @param name The member name.
     * @param value The host value.
     */
    @UsedByGeneratedCode
    public static void rememberAsyncMember(Value source, String name, @Nullable Object value) {
        ContextState state = contextState(source.getContext());
        synchronized (state) {
            state.asyncMembers.computeIfAbsent(source, ignored -> new HashMap<>()).put(name, value);
        }
    }

    private static void copyRememberedAsyncMembers(Value source, Value target) {
        Map<String, Object> members;
        ContextState state = existingContextState(source.getContext());
        if (state == null) {
            return;
        }
        synchronized (state) {
            members = state.asyncMembers.get(source);
            if (members == null || members.isEmpty()) {
                return;
            }
            members = Map.copyOf(members);
        }
        members.forEach((name, value) -> GraalPyRuntimeUtil.putMember(target, name, GraalPyRuntimeUtil.asyncMemberValue(target, value)));
    }

    /**
     * Return the configured Python pool for package-local runtime routing.
     * <p>
     * Callers should check the pooling configuration path before invoking this method; a missing
     * pool means generated bridge calls must use the primary context instead.
     *
     * @return The configured PythonPool. Throws if not initialized.
     */
    static PythonPool getPythonPool() {
        PythonPool pool = pythonPool;
        if (pool == null) {
            throw new IllegalStateException("PythonPool has not been initialized.");
        }
        return pool;
    }

    /**
     * Register a context for execution and shared-engine shutdown tracking.
     *
     * @param context The GraalPy context being tracked
     */
    static void registerContext(Context context) {
        contextState(context);
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
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState contextState = CONTEXT_STATES.remove(context);
            if (contextState != null) {
                listeners = List.copyOf(contextState.noContextListeners);
                contextState.clear();
            } else {
                listeners = List.of();
            }
        }
        runNoActiveExecutionsListeners(listeners);
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
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState state = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
            state.activeExecutions++;
            ACTIVE_EXECUTIONS.incrementAndGet();
        }
    }

    /**
     * Mark the primary context as actively executing host-initiated Python code.
     * <p>
     * This overload is for legacy generated paths that do not already hold a pooled or event-loop
     * context. New context-specific code should prefer {@link #enterExecution(Context)}.
     */
    static void enterExecution() {
        enterExecution(getContext());
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

    private static <T, X extends Throwable> T runWithExecutionFrame(Context ctx, ExecutionFrame frame, CallableOp<T, X> operation) throws X {
        frame.contexts.add(ctx);
        enterExecution(ctx);
        try {
            return operation.call();
        } finally {
            exitExecutionFrame(ctx, frame);
        }
    }

    /**
     * Mark a context execution as complete and run any listeners whose context no longer has
     * active executions.
     *
     * @param context The context leaving Python execution
     */
    static void exitExecution(Context context) {
        List<Runnable> listeners = new ArrayList<>();
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState state = CONTEXT_STATES.get(context);
            if (state != null) {
                state.activeExecutions = Math.max(0, state.activeExecutions - 1);
                if (state.activeExecutions == 0 && !state.noActiveExecutionsListeners.isEmpty()) {
                    listeners.addAll(state.noActiveExecutionsListeners);
                    state.noActiveExecutionsListeners.clear();
                }
            }
            ACTIVE_EXECUTIONS.updateAndGet(value -> Math.max(0, value - 1));
        }
        runNoActiveExecutionsListeners(listeners);
    }

    /**
     * Mark an execution against the primary context as complete.
     * <p>
     * This must only be paired with {@link #enterExecution()} so the aggregate and primary-context
     * execution counters remain balanced.
     */
    static void exitExecution() {
        exitExecution(getContext());
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
     * Listener registration and counter checks are performed under {@link #ACTIVE_EXECUTIONS_LOCK}
     * so a new listener cannot miss the transition from active to idle.
     *
     * @param context The context to observe
     * @param listener The listener to run when the context is idle
     */
    static void onNoActiveExecutions(Context context, Runnable listener) {
        boolean runNow;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState state = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
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
        if (CURRENT_EXECUTION.isBound() && !CURRENT_EXECUTION.get().contexts.isEmpty()) {
            CURRENT_EXECUTION.get().exitListeners.add(() -> onNoActiveExecutions(context, listener));
            return;
        }
        onNoActiveExecutions(context, listener);
    }

    /**
     * Register a listener to run once every distinct context in the collection is idle.
     * <p>
     * Duplicate contexts are collapsed by identity so a pooled context cannot make the listener wait
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
        List<Context> activeContexts;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            IdentityHashMap<Context, Boolean> seen = new IdentityHashMap<>();
            activeContexts = contexts.stream()
                .filter(ctx -> seen.put(ctx, Boolean.TRUE) == null)
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
     * installation happen under {@link #ACTIVE_EXECUTIONS_LOCK} so a concurrent unregister cannot
     * miss the listener.
     *
     * @param engine The engine to observe
     * @param listener The listener to run when the engine no longer owns contexts
     */
    static void onNoContexts(Engine engine, Runnable listener) {
        boolean runNow;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            List<ContextState> states = CONTEXT_STATES.entrySet().stream()
                .filter(entry -> entry.getKey().getEngine() == engine)
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
        deferNoActiveExecutionListener(() -> {
            for (Runnable listener : listeners) {
                try {
                    listener.run();
                } catch (Throwable e) {
                    LOG.warn("Python no-active-executions listener failed", e);
                    if (e instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (e instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException(e);
                }
            }
        });
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
     * Obtain a pooled Python class instance (per-context cached).
     *
     * @param classReference The Python class reference
     * @return The pooled class instance (Value) from some context
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(PythonClassReference classReference) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(classReference);
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return getPythonPool().getEventLoopClass(eventLoop, classReference);
        }
        return getPythonPool().getAnyClass(classReference);
    }

    /**
     * Obtain a pooled Python class instance from a specific context.
     *
     * @param classReference The Python class reference
     * @param context The context
     * @return The pooled class instance (Value)
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(PythonClassReference classReference, Context context) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(classReference, context);
        }
        return getPythonPool().getClass(context, classReference);
    }

    /**
     * Execute a function with a borrowed pooled class instance.
     *
     * @param classReference The Python class reference
     * @param fn Function receiving the pooled Value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static <T> T withPooled(PythonClassReference classReference, java.util.function.Function<Value, T> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooled(classReference, fn));
        }
        if (isReuseContext() || pythonPool == null) {
            return withPrimaryContext(context -> fn.apply(findClass(classReference, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return fn.apply(getPythonPool().getEventLoopClass(eventLoop, classReference));
        }
        return getPythonPool().withClass(classReference, fn);
    }

    /**
     * Obtain a pooled Python script/module object.
     *
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @return A pooled script Value
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(String packageName, String scriptName) {
        if (isReuseContext() || pythonPool == null) {
            return findScript(packageName, scriptName);
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return getPythonPool().getEventLoopScript(eventLoop, packageName, scriptName);
        }
        return getPythonPool().getAnyScript(packageName, scriptName);
    }

    /**
     * Obtain a pooled Python script/module object from a specific context.
     *
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param context The context
     * @return A pooled script Value
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(String packageName, String scriptName, Context context) {
        if (isReuseContext() || pythonPool == null) {
            return findScript(packageName, scriptName, context);
        }
        return getPythonPool().getScript(context, packageName, scriptName);
    }

    /**
     * Execute a function with a borrowed pooled script/module object.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param fn Function receiving the script Value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     */
    @UsedByGeneratedCode
    public static <T> T withPooledScript(String packageName, String scriptName, java.util.function.Function<Value, T> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooledScript(packageName, scriptName, fn));
        }
        if (isReuseContext() || pythonPool == null) {
            return withPrimaryContext(context -> fn.apply(findScript(packageName, scriptName, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return fn.apply(getPythonPool().getEventLoopScript(eventLoop, packageName, scriptName));
        }
        return getPythonPool().withScript(packageName, scriptName, fn);
    }

    /**
     * Create a wrapper for a Python value that is evaluated and cached in each pooled context.
     *
     * @param expression The Python expression or statements to evaluate
     * @return A pooled value wrapper
     * @since 5.2.0
     */
    public static PooledValue withPooledValue(String expression) {
        return new PooledValue() {
            @Override
            public <T> T withValue(java.util.function.Function<Value, T> callback) {
                return PythonContextRuntime.withPooledValue(expression, callback);
            }
        };
    }

    /**
     * Execute a callback with a Python value evaluated and cached in a borrowed pooled context.
     *
     * @param expression The Python expression or statements to evaluate
     * @param fn Function receiving the pooled value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     * @since 5.2.0
     */
    public static <T> T withPooledValue(String expression, java.util.function.Function<Value, T> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooledValue(expression, fn));
        }
        if (isReuseContext() || pythonPool == null) {
            return withPrimaryContext(context -> fn.apply(getOrCreateValue(context, expression)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            Context context = getPythonPool().getEventLoopContext(eventLoop);
            return fn.apply(getPythonPool().getValue(context, expression));
        }
        return getPythonPool().withValue(expression, fn);
    }

    private static Value getOrCreateValue(Context context, String expression) {
        return withContextLock(context, () -> {
            ContextState state = contextState(context);
            String key = "value:" + expression;
            Value value = state.helpers.get(key);
            if (value == null || GraalPyRuntimeUtil.isNone(value)) {
                value = context.eval(PYTHON, expression);
                Value existing = state.helpers.putIfAbsent(key, value);
                if (existing != null && !GraalPyRuntimeUtil.isNone(existing)) {
                    value = existing;
                }
            }
            return value;
        });
    }

    private static boolean shouldOffloadPooledExecution() {
        BeanProvider<ExecutorService> provider = pooledExecutorServiceProvider;
        return provider != null
            && provider.isResolvable()
            && PythonAsyncioRuntime.currentEventLoopForContext() == null
            && LoomSupport.isVirtual(Thread.currentThread());
    }

    private static <T> T offloadPooledExecution(Supplier<T> action) {
        BeanProvider<ExecutorService> provider = pooledExecutorServiceProvider;
        if (provider == null || !provider.isResolvable()) {
            return action.get();
        }
        try {
            return provider.get().submit(action::get).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while offloading pooled Python execution", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Pooled Python execution failed", cause);
        }
    }

    /**
     * Inject an attribute into all pooled script contexts.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param attribute The attribute name
     * @param value The value to inject
     */
    @UsedByGeneratedCode
    public static void injectPooledScript(String packageName, String scriptName, String attribute, Object value) {
        if (isReuseContext() || pythonPool == null) {
            Value script = findScript(packageName, scriptName);
            script.putMember(attribute, GraalPyRuntimeUtil.coerceToContext(value, script.getContext()));
            return;
        }
        getPythonPool().injectScript(packageName, scriptName, attribute, value);
    }

    /**
     * Inject an async-adapted attribute into all pooled script contexts.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param attribute The attribute name
     * @param value The value to inject
     */
    @UsedByGeneratedCode
    public static void injectPooledScriptAsync(String packageName, String scriptName, String attribute, Object value) {
        if (isReuseContext() || pythonPool == null) {
            Value script = findScript(packageName, scriptName);
            script.putMember(attribute, GraalPyRuntimeUtil.asyncMemberValue(script, value));
            return;
        }
        getPythonPool().injectScriptAsync(packageName, scriptName, attribute, value);
    }

    /**
     * Invoke a method on a pooled class instance.
     *
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value invokePooled(PythonClassReference classReference, String methodName, Object... args) {
        return withPooled(classReference, v -> GraalPyRuntimeUtil.invokePythonMethod(
            v,
            methodName,
            GraalPyRuntimeUtil.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    /**
     * Invoke a method on a pooled script instance.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     */
    @UsedByGeneratedCode
    public static Value invokePooledScript(String packageName, String scriptName, String methodName, Object... args) {
        return withPooledScript(packageName, scriptName, v -> v.getMember(methodName).execute(
            GraalPyRuntimeUtil.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    public static Context getContext() {
        Context ctx = context;
        if (ctx == null) {
            throw new IllegalStateException("GraalPy context has not been initialized. " +
                "Make sure micronaut-context-python is on the classpath.");
        }
        return ctx;
    }

    /**
     * Create an instance that is abstract and fill out the abstract methods with stubs to be later populated.
     *
     * @param classReference The Python class reference
     * @param args The args
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newIntroduction(PythonClassReference classReference, Object... args) {
        Value pythonClass = findClass(classReference);
        if (!pythonClass.hasMember("__micronaut_introduction__")) {

            Value abstractMethods = pythonClass.getMember("__abstractmethods__");
            if (abstractMethods != null && abstractMethods.hasIterator()) {
                Value iterator = abstractMethods.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    String methodName = iterator.getIteratorNextElement().asString();
                    ProxyExecutable stub = (_) -> null;
                    pythonClass.putMember(methodName, stub);
                }
            }
            Context context = pythonClass.getContext();
            if (!context.getBindings(PYTHON).hasMember("update_abstractmethods")) {
                context.eval(PYTHON, "from abc import update_abstractmethods");
            }

            Value updateAbstractMethods = context.getBindings(PYTHON).getMember("update_abstractmethods");
            updateAbstractMethods.execute(pythonClass);
            Value isProtocol = pythonClass.getMember("_is_protocol");
            if (isProtocol != null && isProtocol.isBoolean() && isProtocol.asBoolean()) {
                pythonClass.putMember("_is_protocol", false);
            }
        }
        return instantiate(classReference, args, pythonClass);
    }

    /**
     * Create a new abstract introduction instance, omitting trailing null arguments that correspond
     * to Python constructor defaults.
     *
     * @param classReference The Python class reference
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newIntroductionWithDefaultedTrailingNulls(PythonClassReference classReference,
                                                                  int requiredArgCount,
                                                                  Object... args) {
        return newIntroduction(classReference, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a new instance for the given class reference and args.
     *
     * @param classReference The Python class reference
     * @param args The args
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstance(PythonClassReference classReference, Object... args) {
        Value pythonClass = findClass(classReference);
        return instantiate(classReference, args, pythonClass);
    }

    /**
     * Resolve a Python enum constant by its Java enum name.
     *
     * @param classReference The Python class reference
     * @param name The enum constant name
     * @return The Python enum constant
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value enumValue(PythonClassReference classReference, String name) {
        Value pythonClass = findClass(classReference);
        return withContextClassLoader(() -> {
            Value enumValue = pythonClass.getMember(name);
            if (enumValue != null && !GraalPyRuntimeUtil.isNone(enumValue)) {
                return enumValue;
            }
            Value members = pythonClass.getMember("__members__");
            if (members != null && members.hasHashEntries()) {
                enumValue = members.getHashValue(name);
                if (enumValue != null && !GraalPyRuntimeUtil.isNone(enumValue)) {
                    return enumValue;
                }
            }
            String qualifiedName = qualifiedName(classReference);
            throw new IllegalArgumentException("Cannot resolve Python enum constant: " + qualifiedName + "." + name);
        });
    }

    /**
     * Create a new instance, omitting trailing null arguments that correspond to Python constructor
     * defaults.
     *
     * @param classReference The Python class reference
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstanceWithDefaultedTrailingNulls(PythonClassReference classReference,
                                                             int requiredArgCount,
                                                             Object... args) {
        return newInstance(classReference, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a Python instance without invoking {@code __init__}.
     *
     * @param classReference The Python class reference
     * @return The new uninitialized instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newUninitializedInstance(PythonClassReference classReference) {
        Context context = getContext();
        Value pythonClass = findClass(classReference, context);
        return context.eval(PYTHON, "lambda cls: object.__new__(cls)").execute(pythonClass);
    }

    /**
     * Create a new instance and set properties via member assignment when no constructor exists.
     *
     * @param classReference The Python class reference
     * @param props Map of property names to values
     * @return The new instance with members populated
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstance(PythonClassReference classReference, Map<String, Object> props) {
        Value pythonClass = findClass(classReference);
        return withContextClassLoader(() -> {
            Value instance = instantiate(classReference, new Object[0], pythonClass);
            if (props != null && !props.isEmpty()) {
                Value propertySetter = propertySetter(instance.getContext());
                for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                    propertySetter.execute(
                        instance,
                        e.getKey(),
                        GraalPyRuntimeUtil.coerceToContext(e.getValue(), instance.getContext())
                    );
                }
            }
            return instance;
        });
    }

    /**
     * Create a new frozen dataclass instance and set properties without invoking __init__.
     *
     * @param classReference The Python class reference
     * @param props Map of property names to values
     * @return The new frozen dataclass instance with members populated
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newFrozenDataclassInstance(PythonClassReference classReference, Map<String, Object> props) {
        Value pythonClass = findClass(classReference);
        return withContextClassLoader(() -> {
            Value instance = uninitializedInstanceFactory(pythonClass.getContext()).execute(pythonClass);
            if (props != null && !props.isEmpty()) {
                Value propertySetter = propertySetter(instance.getContext());
                for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                    propertySetter.execute(
                        instance,
                        e.getKey(),
                        GraalPyRuntimeUtil.coerceToContext(e.getValue(), instance.getContext())
                    );
                }
            }
            return instance;
        });
    }

    private static Value uninitializedInstanceFactory(Context context) {
        return helper(context, NEW_UNINITIALIZED_INSTANCE, NEW_UNINITIALIZED_INSTANCE_SOURCE);
    }

    private static Value propertySetter(Context context) {
        return helper(context, SET_INSTANCE_PROPERTY, SET_INSTANCE_PROPERTY_SOURCE);
    }

    private static Value instantiate(PythonClassReference classReference, Object[] args, Value pythonClass) {
        return withContextClassLoader(() -> {
            if (pythonClass.canInstantiate()) {
                return pythonClass.newInstance(GraalPyRuntimeUtil.coerceArgumentsToContext(pythonClass.getContext(), args));
            } else {
                throw new InstantiationException("Cannot instantiate class: " + qualifiedName(classReference) + ". Ensure the class is a valid Python class and is non-abstract.");
            }
        });
    }

    private static Object[] trimDefaultedTrailingNulls(int requiredArgCount, Object[] args) {
        int length = args.length;
        while (length > requiredArgCount && args[length - 1] == null) {
            length--;
        }
        return length == args.length ? args : Arrays.copyOf(args, length);
    }

    /**
     * Find a Python class by pre-split class reference.
     *
     * @param classReference The Python class reference
     * @return The class Value
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value findClass(PythonClassReference classReference) {
        return findClass(classReference, getContext());
    }

    static Value findClass(PythonClassReference classReference, Context ctx) {
        if (classReference.packageName() == null || PYTHON.equals(classReference.packageName())) {
            Value value = ctx.getBindings(PYTHON).getMember(classReference.rootName());
            if (value == null) {
                Value member = importModule(ctx, classReference.rootName()).getMember(classReference.rootName());
                if (member == null) {
                    throw new InstantiationException("Cannot find Python class: " + classReference.displayName());
                }
                value = member;
            }
            return nestedMember(value, classReference);
        }

        try {
            return nestedMember(importPackageMember(ctx, classReference.packageName(), classReference.rootName()), classReference);
        } catch (Exception e) {
            throw new InstantiationException("Failed to import Python class [" + qualifiedName(classReference) + "]: " + e.getMessage(), e);
        }
    }

    private static Value nestedMember(Value root, PythonClassReference classReference) {
        Value value = root;
        for (String nestedName : classReference.nestedMemberNames()) {
            value = value.getMember(nestedName);
            if (value == null) {
                throw new InstantiationException("Cannot find Python class: " + classReference.displayName());
            }
        }
        return value;
    }

    private static String qualifiedName(PythonClassReference classReference) {
        return classReference.packageName() == null || PYTHON.equals(classReference.packageName())
            ? classReference.displayName()
            : classReference.packageName() + "." + classReference.displayName();
    }

    /**
     * Find a Python script/module Value.
     * @param packageName The package name (or python for top-level)
     * @param scriptName The script/module name
     * @return The module Value
     */
    @UsedByGeneratedCode
    public static Value findScript(String packageName, String scriptName) {
        Context ctx = getContext();
        return findScript(packageName, scriptName, ctx);
    }

    /**
     * Resolve a Python module from a specific context.
     * <p>
     * Pooled script caching depends on this method returning a module value owned by {@code ctx};
     * callers must not share the returned value with another GraalPy context.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @param ctx The context that should perform imports
     * @return The resolved module value
     */
    static Value findScript(String packageName, String scriptName, Context ctx) {
        Value v = ctx.getBindings(PYTHON);
        if (v != null) {
            if (PYTHON.equals(packageName)) {
                if ("Unnamed".equals(scriptName)) {
                    return v;
                } else {
                    return importModule(ctx, scriptName);
                }
            } else {
                return importModule(ctx, packageName + "." + scriptName);
            }
        } else {
            throw new InstantiationException("Cannot find Python module: " + packageName);
        }
    }

    /**
     * Invoke a static method on the given Python class.
     *
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args The method arguments
     * @return The method result
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value invokeStaticMethod(PythonClassReference classReference, String methodName, Object... args) {
        Context ctx = getContext();
        enterExecution(ctx);
        try {
            return findClass(classReference, ctx).invokeMember(methodName, args);
        } finally {
            exitExecution(ctx);
        }
    }

    private static Value importPackageMember(Context ctx, String packageName, String importName) {
        Value module = importModule(ctx, packageName);
        Value member = module.getMember(importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        member = importPackageSubmoduleMember(ctx, packageName, importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        member = findClassInPackageModules(ctx, packageName, importName);
        if (member != null && isPythonClass(ctx, member)) {
            return member;
        }
        throw new InstantiationException("Cannot find Python member: " + packageName + "." + importName);
    }

    private static @Nullable Value importPackageSubmoduleMember(Context ctx, String packageName, String importName) {
        try {
            Value submodule = importModule(ctx, packageName + "." + importName);
            Value member = submodule.getMember(importName);
            if (member != null) {
                return member;
            }
        } catch (Exception ignored) {
            // Fall back to the Python source module name below.
        }
        String pythonModuleName = NameUtils.underscoreSeparate(importName, true);
        if (!pythonModuleName.equals(importName)) {
            try {
                Value submodule = importModule(ctx, packageName + "." + pythonModuleName);
                return submodule.getMember(importName);
            } catch (Exception ignored) {
                // Fall back to package module scanning below.
            }
        }
        return null;
    }

    private static @Nullable Value findClassInPackageModules(Context ctx, String packageName, String importName) {
        Value findClass = helper(ctx, "__micronaut_find_class_in_package_modules", FIND_CLASS_IN_PACKAGE_MODULES_SOURCE);
        return findClass.execute(packageName, importName);
    }

    private static boolean isPythonClass(Context ctx, @Nullable Value value) {
        if (value == null || GraalPyRuntimeUtil.isNone(value)) {
            return false;
        }
        Value isClass = helper(ctx, "__micronaut_inspect_isclass", INSPECT_IS_CLASS_SOURCE);
        return isClass.execute(value).asBoolean();
    }

    private static Value importModule(Context ctx, String moduleName) {
        return withContextClassLoader(() -> {
            Value importModule = helper(ctx, "__micronaut_import_module", IMPORT_MODULE_SOURCE);
            return importModule.execute(moduleName);
        });
    }

    /**
     * Resolve or initialize a cached helper function inside the given context.
     * <p>
     * Helpers are stored per-context because Graal values cannot be shared across contexts. Helper
     * initialization deliberately avoids {@link #withContextLock(Context, Supplier)} because GraalPy
     * operations acquire the Python GIL; taking the context monitor first can deadlock with another
     * thread that already owns the GIL and re-enters Micronaut runtime helper code.
     *
     * @param context The context that owns the helper function
     * @param name The binding name exposed by the helper source
     * @param source The source that installs the helper when absent
     * @return The helper function value for the context
     */
    static Value helper(Context context, String name, Source source) {
        ContextState state = contextState(context);
        Value helper = state.helpers.get(name);
        if (helper != null) {
            return helper;
        }
        Value bindings = context.getBindings(PYTHON);
        helper = bindings.getMember(name);
        if (helper == null || GraalPyRuntimeUtil.isNone(helper)) {
            context.eval(source);
            helper = bindings.getMember(name);
        }
        Value existing = state.helpers.putIfAbsent(name, helper);
        return existing == null ? helper : existing;
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
        synchronized (contextState(context).lock) {
            return action.get();
        }
    }

    static <T> T withPrimaryContext(java.util.function.Function<Context, T> callback) {
        Context primary = getContext();
        return withContextLock(primary,
            () -> withExecutionFrame(primary, () -> callback.apply(primary)));
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
     * Set the GraalPy context. This method is called by GraalPyContextFactory
     * during application startup.
     *
     * @param context The GraalPy context to set
     */
    public static void setContext(Context context) {
        PythonContextRuntime.context = context;
        PythonContextRuntime.contextClassLoader = Thread.currentThread().getContextClassLoader();
    }

    /**
     * Set the GraalPy context and the application class loader that should be active when
     * generated bridge classes enter Python from arbitrary runtime threads.
     *
     * @param context The GraalPy context to set
     * @param classLoader The application class loader used to build the context
     */
    public static void setContext(Context context, @Nullable ClassLoader classLoader) {
        PythonContextRuntime.context = context;
        PythonContextRuntime.contextClassLoader = classLoader;
    }

    /**
     * Returns the application class loader associated with the current GraalPy context.
     *
     * @return The application class loader
     */
    public static @Nullable ClassLoader getContextClassLoader() {
        return contextClassLoader;
    }

    /**
     * Check if the context has been initialized.
     *
     * @return true if the context is available, false otherwise
     */
    public static boolean isInitialized() {
        return context != null;
    }

    /**
     * Check whether the supplied context is the primary runtime context by identity.
     * <p>
     * Identity comparison is intentional because multiple GraalPy contexts can share the same
     * engine and equivalent configuration but still own incompatible {@link Value} instances.
     *
     * @param context The context to compare
     * @return {@code true} when the context is the primary runtime context
     */
    @SuppressWarnings("ReferenceEquality")
    static boolean isCurrentContext(Context context) {
        return PythonContextRuntime.context == context;
    }

    /**
     * Reset the context to null. This method is called during application shutdown
     * to ensure proper cleanup and prevent memory leaks.
     */
    public static void resetContext() {
        if (REUSE_CONTEXT.get()) {
            Context ctx = context;
            if (ctx == null) {
                return;
            }
            ctx.eval(RELOAD_MODULES_SOURCE);
            return;
        }
        context = null;
        contextClassLoader = null;
        pythonPool = null;
        pooledExecutorServiceProvider = null;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            CONTEXT_STATES.values().forEach(ContextState::clear);
            CONTEXT_STATES.clear();
            ACTIVE_EXECUTIONS.set(0);
        }
    }

    private static <T> T withContextClassLoader(Supplier<T> action) {
        ClassLoader classLoader = contextClassLoader;
        if (classLoader == null) {
            return action.get();
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == classLoader) {
            return action.get();
        }
        thread.setContextClassLoader(classLoader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * If context reuse is set to true, then the context will never be cleared.
     * @param reuse tells if the context should be reused
     */
    public static void setReuseContext(boolean reuse) {
        REUSE_CONTEXT.set(reuse);
    }

    /**
     * Returns true if the context should be reused.
     * @return the reuse flag
     */
    public static boolean isReuseContext() {
        return REUSE_CONTEXT.get();
    }

    /**
     * Pre-split Python class identity used by generated bridge code.
     *
     * @param packageName The Python package, or {@code null} for top-level classes
     * @param rootName The top-level Python import/member name
     * @param nestedMemberNames The nested member names below the root class
     * @param displayName The class display name used in diagnostics
     * @param cacheKey The stable class cache key used by pooled contexts
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public record PythonClassReference(
        @Nullable String packageName,
        String rootName,
        String[] nestedMemberNames,
        String displayName,
        String cacheKey
    ) {
        /**
         * Create a Python class reference.
         *
         * @param packageName The Python package
         * @param rootName The top-level Python import/member name
         * @param nestedMemberNames The nested member names below the root class
         * @param displayName The class display name used in diagnostics
         * @param cacheKey The stable class cache key used by pooled contexts
         */
        public PythonClassReference {
            nestedMemberNames = nestedMemberNames.clone();
        }

        @Override
        public String[] nestedMemberNames() {
            return nestedMemberNames.clone();
        }
    }

    private static final class ContextState {
        private final Object lock = new Object();
        private final IdentityHashMap<Value, Map<String, Object>> asyncMembers = new IdentityHashMap<>();
        private final Map<String, Value> helpers = new ConcurrentHashMap<>();
        private final List<Runnable> noActiveExecutionsListeners = new ArrayList<>();
        private final List<Runnable> noContextListeners = new ArrayList<>();
        private int activeExecutions;

        private void clear() {
            asyncMembers.clear();
            helpers.clear();
            noActiveExecutionsListeners.clear();
            noContextListeners.clear();
            activeExecutions = 0;
        }
    }

    private static final class ExecutionFrame {
        private final List<Context> contexts = new ArrayList<>();
        private final List<Runnable> exitListeners = new ArrayList<>();
    }
}
