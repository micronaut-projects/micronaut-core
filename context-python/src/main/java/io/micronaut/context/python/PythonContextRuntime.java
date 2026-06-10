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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
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
 * @since 5.0.0
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
    private static final AtomicInteger ACTIVE_EXECUTIONS = new AtomicInteger();
    private static final Object ACTIVE_EXECUTIONS_LOCK = new Object();
    private static final List<Runnable> NO_ACTIVE_EXECUTIONS_LISTENERS = new ArrayList<>();
    private static final IdentityHashMap<Context, ContextState> CONTEXT_STATES = new IdentityHashMap<>();
    private static final IdentityHashMap<Engine, EngineState> ENGINE_STATES = new IdentityHashMap<>();
    private static final ThreadLocal<List<Context>> CURRENT_EXECUTION_CONTEXTS = ThreadLocal.withInitial(ArrayList::new);
    private static final ThreadLocal<List<Runnable>> CURRENT_EXECUTION_EXIT_LISTENERS = ThreadLocal.withInitial(ArrayList::new);

    private PythonContextRuntime() {
    }

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

    private static EngineState engineState(Engine engine) {
        return ENGINE_STATES.computeIfAbsent(engine, ignored -> new EngineState());
    }

    /**
     * Internal hook to register the PythonPool once initialized.
     *
     * @param pool The Python pool
     */
    public static void setPythonPool(@Nullable PythonPool pool) {
        PythonContextRuntime.pythonPool = pool;
    }

    /**
     * Resolve a Python instance for the current asyncio event loop when one is active.
     *
     * @param fallback The startup-context instance.
     * @param packageName The Python package.
     * @param simpleName The class simple name.
     * @return An event-loop-local instance, or the fallback when no event-loop context is active.
     */
    @UsedByGeneratedCode
    public static Value asyncInstance(Value fallback, @Nullable String packageName, String simpleName) {
        PythonPool pool = pythonPool;
        if (pool == null || isReuseContext()) {
            return fallback;
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop == null) {
            return fallback;
        }
        Value target = pool.getEventLoopClass(eventLoop, packageName, simpleName);
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
     * Returns the configured Python pool.
     *
     * @return The configured PythonPool. Throws if not initialized.
     */
    public static PythonPool getPythonPool() {
        PythonPool pool = pythonPool;
        if (pool == null) {
            throw new IllegalStateException("PythonPool has not been initialized.");
        }
        return pool;
    }

    static void registerContextEngine(Context context, Engine engine) {
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState contextState = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
            Engine previousEngine = contextState.engine;
            if (previousEngine == engine) {
                return;
            }
            if (previousEngine != null) {
                EngineState previousState = ENGINE_STATES.get(previousEngine);
                if (previousState != null && --previousState.contexts <= 0) {
                    ENGINE_STATES.remove(previousEngine);
                }
            }
            contextState.engine = engine;
            engineState(engine).contexts++;
        }
    }

    static void unregisterContextEngine(Context context) {
        List<Runnable> listeners = List.of();
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState contextState = CONTEXT_STATES.remove(context);
            if (contextState != null) {
                contextState.clear();
                Engine engine = contextState.engine;
                if (engine != null) {
                    EngineState engineState = ENGINE_STATES.get(engine);
                    if (engineState != null && --engineState.contexts <= 0) {
                        ENGINE_STATES.remove(engine);
                        listeners = engineState.noContextsListeners;
                    }
                }
            }
        }
        runNoActiveExecutionsListeners(listeners);
    }

    static void enterExecution(Context context) {
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState state = CONTEXT_STATES.computeIfAbsent(context, ignored -> new ContextState());
            state.activeExecutions++;
            if (state.engine != null) {
                engineState(state.engine).activeExecutions++;
            }
            ACTIVE_EXECUTIONS.incrementAndGet();
        }
    }

    static void enterExecution() {
        Context ctx = getContext();
        enterExecutionFrame(ctx);
    }

    static void enterExecutionFrame(Context ctx) {
        CURRENT_EXECUTION_CONTEXTS.get().add(ctx);
        enterExecution(ctx);
    }

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
                if (state.engine != null) {
                    EngineState engineState = ENGINE_STATES.get(state.engine);
                    if (engineState != null) {
                        engineState.activeExecutions = Math.max(0, engineState.activeExecutions - 1);
                        if (engineState.activeExecutions == 0 && !engineState.noActiveExecutionsListeners.isEmpty()) {
                            listeners.addAll(engineState.noActiveExecutionsListeners);
                            engineState.noActiveExecutionsListeners.clear();
                        }
                    }
                }
            }
            if (ACTIVE_EXECUTIONS.updateAndGet(value -> Math.max(0, value - 1)) == 0 && !NO_ACTIVE_EXECUTIONS_LISTENERS.isEmpty()) {
                listeners.addAll(NO_ACTIVE_EXECUTIONS_LISTENERS);
                NO_ACTIVE_EXECUTIONS_LISTENERS.clear();
            }
        }
        runNoActiveExecutionsListeners(listeners);
    }

    static void exitExecution() {
        List<Context> contexts = CURRENT_EXECUTION_CONTEXTS.get();
        if (contexts.isEmpty()) {
            return;
        }
        Context ctx = contexts.remove(contexts.size() - 1);
        if (contexts.isEmpty()) {
            CURRENT_EXECUTION_CONTEXTS.remove();
        }
        exitExecution(ctx);
        if (contexts.isEmpty()) {
            runCurrentExecutionExitListeners();
        }
    }

    @SuppressWarnings("ReferenceEquality")
    static void exitExecutionFrame(Context ctx) {
        List<Context> contexts = CURRENT_EXECUTION_CONTEXTS.get();
        if (contexts.isEmpty()) {
            exitExecution(ctx);
            return;
        }
        Context removed = contexts.remove(contexts.size() - 1);
        if (removed != ctx) {
            contexts.remove(ctx);
        }
        if (contexts.isEmpty()) {
            CURRENT_EXECUTION_CONTEXTS.remove();
        }
        exitExecution(ctx);
        if (contexts.isEmpty()) {
            runCurrentExecutionExitListeners();
        }
    }

    private static void runCurrentExecutionExitListeners() {
        List<Runnable> listeners = CURRENT_EXECUTION_EXIT_LISTENERS.get();
        if (listeners.isEmpty()) {
            CURRENT_EXECUTION_EXIT_LISTENERS.remove();
            return;
        }
        List<Runnable> snapshot = List.copyOf(listeners);
        listeners.clear();
        CURRENT_EXECUTION_EXIT_LISTENERS.remove();
        runNoActiveExecutionsListeners(snapshot);
    }

    static int activeExecutions() {
        return ACTIVE_EXECUTIONS.get();
    }

    static boolean hasActiveExecutions(Context context) {
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            ContextState state = CONTEXT_STATES.get(context);
            return state != null && state.activeExecutions > 0;
        }
    }

    static void onNoActiveExecutions(Runnable listener) {
        boolean runNow;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            runNow = ACTIVE_EXECUTIONS.get() == 0;
            if (!runNow) {
                NO_ACTIVE_EXECUTIONS_LISTENERS.add(listener);
            }
        }
        if (runNow) {
            listener.run();
        }
    }

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

    static void onNoActiveExecutionsAfterCurrentFrame(Context context, Runnable listener) {
        if (!CURRENT_EXECUTION_CONTEXTS.get().isEmpty()) {
            CURRENT_EXECUTION_EXIT_LISTENERS.get().add(() -> onNoActiveExecutions(context, listener));
            return;
        }
        onNoActiveExecutions(context, listener);
    }

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

    static void onNoActiveExecutions(Engine engine, Runnable listener) {
        boolean runNow;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            EngineState state = ENGINE_STATES.get(engine);
            runNow = state == null || state.activeExecutions == 0;
            if (state != null && !runNow) {
                state.noActiveExecutionsListeners.add(listener);
            }
        }
        if (runNow) {
            listener.run();
        }
    }

    static void onNoContexts(Engine engine, Runnable listener) {
        boolean runNow;
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            EngineState state = ENGINE_STATES.get(engine);
            runNow = state == null || state.contexts == 0;
            if (state != null && !runNow) {
                state.noContextsListeners.add(listener);
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

    static void deferNoActiveExecutionListener(Runnable listener) {
        listener.run();
    }

    /**
     * Obtain a pooled Python class instance (per-context cached).
     *
     * @param packageName The Python package (or null/python for top-level)
     * @param simpleName The class simple name
     * @return The pooled class instance (Value) from some context
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable String packageName, String simpleName) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(packageName, simpleName);
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return getPythonPool().getEventLoopClass(eventLoop, packageName, simpleName);
        }
        return getPythonPool().getAnyClass(packageName, simpleName);
    }

    /**
     * Obtain a pooled Python class instance from a specific context.
     *
     * @param packageName The Python package (or null/python for top-level)
     * @param simpleName The class simple name
     * @param context The context
     * @return The pooled class instance (Value)
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable String packageName, String simpleName, Context context) {
        if (isReuseContext() || pythonPool == null) {
            return findClass(packageName, simpleName, context);
        }
        return getPythonPool().getClass(context, packageName, simpleName);
    }

    /**
     * Execute a function with a borrowed pooled class instance.
     *
     * @param packageName The Python package
     * @param simpleName The class name
     * @param fn Function receiving the pooled Value
     * @param <T> Result type returned by the function
     * @return Result returned from the function
     */
    @UsedByGeneratedCode
    public static <T> T withPooled(@Nullable String packageName, String simpleName, java.util.function.Function<Value, T> fn) {
        if (isReuseContext() || pythonPool == null) {
            return fn.apply(findClass(packageName, simpleName));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return fn.apply(getPythonPool().getEventLoopClass(eventLoop, packageName, simpleName));
        }
        return getPythonPool().withClass(packageName, simpleName, fn);
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
        if (isReuseContext() || pythonPool == null) {
            return fn.apply(findScript(packageName, scriptName));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            return fn.apply(getPythonPool().getEventLoopScript(eventLoop, packageName, scriptName));
        }
        return getPythonPool().withScript(packageName, scriptName, fn);
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
     * @param packageName The package
     * @param simpleName The class name
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     */
    @UsedByGeneratedCode
    public static Value invokePooled(@Nullable String packageName, String simpleName, String methodName, Object... args) {
        return withPooled(packageName, simpleName, v -> GraalPyRuntimeUtil.invokePythonMethod(
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
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param args        The args
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newIntroduction(@Nullable String packageName, String simpleName, Object... args) {
        Value pythonClass = findClass(packageName, simpleName);
        if (!pythonClass.hasMember("__micronaut_introduction__")) {

            Value abstractMethods = pythonClass.getMember("__abstractmethods__");
            if (abstractMethods != null && abstractMethods.hasIterator()) {
                Value iterator = abstractMethods.getIterator();
                while (iterator.hasIteratorNextElement()) {
                    String methodName = iterator.getIteratorNextElement().asString();
                    ProxyExecutable stub = (execArgs) -> null;
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
        return instantiate(packageName, simpleName, args, pythonClass);
    }

    /**
     * Create a new abstract introduction instance, omitting trailing null arguments that correspond
     * to Python constructor defaults.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newIntroductionWithDefaultedTrailingNulls(@Nullable String packageName,
                                                                  String simpleName,
                                                                  int requiredArgCount,
                                                                  Object... args) {
        return newIntroduction(packageName, simpleName, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a new instance for the given package name, simple name and args.
     *
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param args        The args
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newInstance(@Nullable String packageName, String simpleName, Object... args) {
        Value pythonClass = findClass(packageName, simpleName);
        return instantiate(packageName, simpleName, args, pythonClass);
    }

    /**
     * Resolve a Python enum constant by its Java enum name.
     *
     * @param packageName The package name
     * @param simpleName  The simple name
     * @param name        The enum constant name
     * @return The Python enum constant
     */
    @UsedByGeneratedCode
    public static Value enumValue(@Nullable String packageName, String simpleName, String name) {
        Value pythonClass = findClass(packageName, simpleName);
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
            String qualifiedName = packageName == null || PYTHON.equals(packageName) ? simpleName : packageName + "." + simpleName;
            throw new IllegalArgumentException("Cannot resolve Python enum constant: " + qualifiedName + "." + name);
        });
    }

    /**
     * Create a new instance, omitting trailing null arguments that correspond to Python constructor
     * defaults.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     */
    @UsedByGeneratedCode
    public static Value newInstanceWithDefaultedTrailingNulls(@Nullable String packageName,
                                                             String simpleName,
                                                             int requiredArgCount,
                                                             Object... args) {
        return newInstance(packageName, simpleName, trimDefaultedTrailingNulls(requiredArgCount, args));
    }

    /**
     * Create a Python instance without invoking {@code __init__}.
     *
     * @param packageName The package name
     * @param simpleName The simple name
     * @return The new uninitialized instance
     */
    @UsedByGeneratedCode
    public static Value newUninitializedInstance(@Nullable String packageName, String simpleName) {
        Context context = getContext();
        Value pythonClass = findClass(packageName, simpleName, context);
        return context.eval(PYTHON, "lambda cls: object.__new__(cls)").execute(pythonClass);
    }

    /**
     * Create a new instance and set properties via member assignment when no constructor exists.
     * This overload accepts a map of property values and will instantiate the Python class with
     * no positional arguments, then populate attributes using Graal Polyglot putMember.
     *
     * @param packageName The package name (nullable)
     * @param simpleName  The simple class name
     * @param props       Map of property names to values
     * @return The new instance with members populated.
     */
    @UsedByGeneratedCode
    public static Value newInstance(@Nullable String packageName, String simpleName, java.util.Map<String, Object> props) {
        Value pythonClass = findClass(packageName, simpleName);
        return withContextClassLoader(() -> {
            Value instance = instantiate(packageName, simpleName, new Object[0], pythonClass);
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
     * @param packageName The package name (nullable)
     * @param simpleName  The simple class name
     * @param props       Map of property names to values
     * @return The new frozen dataclass instance with members populated.
     */
    @UsedByGeneratedCode
    public static Value newFrozenDataclassInstance(@Nullable String packageName, String simpleName, java.util.Map<String, Object> props) {
        Value pythonClass = findClass(packageName, simpleName);
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

    private static Value instantiate(@Nullable String packageName, String simpleName, Object[] args, Value pythonClass) {
        return withContextClassLoader(() -> {
            if (pythonClass.canInstantiate()) {
                return pythonClass.newInstance(GraalPyRuntimeUtil.coerceArgumentsToContext(pythonClass.getContext(), args));
            } else {
                String qualifiedName = packageName == null || PYTHON.equals(packageName) ? simpleName :  packageName + "." + simpleName;
                throw new InstantiationException("Cannot instantiate class: " + qualifiedName + ". Ensure the class is a valid Python class and is non-abstract.");
            }
        });
    }

    /**
     * Create a new instance for the given simple name and args.
     *
     * @param simpleName The simple name
     * @param args       The args
     * @return The new instance
     */
    public static Value newInstance(String simpleName, Object... args) {
        Value pythonClass = findClass(simpleName);
        return instantiate(null, simpleName, args, pythonClass);
    }

    private static Object[] trimDefaultedTrailingNulls(int requiredArgCount, Object[] args) {
        int length = args.length;
        while (length > requiredArgCount && args[length - 1] == null) {
            length--;
        }
        return length == args.length ? args : Arrays.copyOf(args, length);
    }

    public static Value findClass(@org.jspecify.annotations.Nullable String packageName, String simpleName) {
        return findClass(packageName, simpleName, getContext());
    }

    static Value findClass(@org.jspecify.annotations.Nullable String packageName, String simpleName, Context ctx) {
        if (packageName == null || PYTHON.equals(packageName)) {
            return findClass(simpleName, ctx);
        }

        String importName = rootName(simpleName);
        try {
            return nestedMember(importPackageMember(ctx, packageName, importName), simpleName);
        } catch (Exception e) {
            throw new InstantiationException("Failed to import Python class [" + packageName + "." + simpleName + "]: " + e.getMessage(), e);
        }
    }

    /**
     * Find a Python class by fully qualified name.
     * @param simpleName The class name
     * @return The class Value
     */
    @UsedByGeneratedCode
    public static Value findClass(String simpleName) {
        return findClass(simpleName, getContext());
    }

    static Value findClass(String simpleName, Context ctx) {
        String importName = rootName(simpleName);
        Value v = ctx.getBindings(PYTHON).getMember(importName);
        if (v == null) {
            Value member = importModule(ctx, importName).getMember(importName);
            if (member == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            v = member;
        }
        return nestedMember(v, simpleName);
    }

    private static String rootName(String simpleName) {
        int nestedSeparator = simpleName.indexOf('.');
        return nestedSeparator < 0 ? simpleName : simpleName.substring(0, nestedSeparator);
    }

    private static Value nestedMember(Value root, String simpleName) {
        Value value = root;
        int nestedSeparator = simpleName.indexOf('.');
        if (nestedSeparator < 0) {
            return value;
        }
        int start = nestedSeparator + 1;
        while (start < simpleName.length()) {
            int end = simpleName.indexOf('.', start);
            String nestedName = end < 0 ? simpleName.substring(start) : simpleName.substring(start, end);
            value = value.getMember(nestedName);
            if (value == null) {
                throw new InstantiationException("Cannot find Python class: " + simpleName);
            }
            if (end < 0) {
                break;
            }
            start = end + 1;
        }
        return value;
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
     * @param packageName The package name
     * @param simpleName  The simple class name
     * @param methodName  The method name
     * @param args        The method arguments
     * @return The method result
     */
    public static Value invokeStaticMethod(@Nullable String packageName, String simpleName, String methodName, Object... args) {
        if (packageName == null || PYTHON.equals(packageName)) {
            return invokeStaticMethod(simpleName, methodName, args);
        } else {
            Context ctx = getContext();
            enterExecution(ctx);
            try {
                return findClass(packageName, simpleName, ctx).invokeMember(methodName, args);
            } finally {
                exitExecution(ctx);
            }
        }
    }

    /**
     * Invoke a static method on the given Python class.
     *
     * @param simpleName  The simple class name
     * @param methodName  The method name
     * @param args        The method arguments
     * @return The method result
     */
    public static Value invokeStaticMethod(String simpleName, String methodName, Object... args) {
        Context ctx = getContext();
        enterExecution(ctx);
        try {
            String importName = rootName(simpleName);
            Value v = ctx.getBindings(PYTHON).getMember(importName);
            if (v != null) {
                return nestedMember(v, simpleName).invokeMember(methodName, args);
            } else {
                Value member = importModule(ctx, importName).getMember(importName);
                if (member == null) {
                    throw new InstantiationException("Cannot find Python class: " + simpleName);
                }
                return nestedMember(member, simpleName)
                    .invokeMember(methodName, args);
            }
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

    static Value helper(Context context, String name, Source source) {
        return withContextLock(context, () -> {
            ContextState state = contextState(context);
            Value helper = state.helpers.get(name);
            if (helper == null || GraalPyRuntimeUtil.isNone(helper)) {
                Value bindings = context.getBindings(PYTHON);
                helper = bindings.getMember(name);
                if (helper == null || GraalPyRuntimeUtil.isNone(helper)) {
                    context.eval(source);
                    helper = bindings.getMember(name);
                }
                state.helpers.put(name, helper);
            }
            return helper;
        });
    }

    static <T> T withContextLock(Context context, Supplier<T> action) {
        synchronized (contextState(context).lock) {
            return action.get();
        }
    }

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
        synchronized (ACTIVE_EXECUTIONS_LOCK) {
            CONTEXT_STATES.values().forEach(ContextState::clear);
            CONTEXT_STATES.clear();
            ENGINE_STATES.clear();
            NO_ACTIVE_EXECUTIONS_LISTENERS.clear();
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

    private static final class ContextState {
        private final Object lock = new Object();
        private final IdentityHashMap<Value, Map<String, Object>> asyncMembers = new IdentityHashMap<>();
        private final Map<String, Value> helpers = new HashMap<>();
        private final List<Runnable> noActiveExecutionsListeners = new ArrayList<>();
        private int activeExecutions;
        private @Nullable Engine engine;

        private void clear() {
            asyncMembers.clear();
            helpers.clear();
            noActiveExecutionsListeners.clear();
            activeExecutions = 0;
            engine = null;
        }
    }

    private static final class EngineState {
        private final List<Runnable> noContextsListeners = new ArrayList<>();
        private final List<Runnable> noActiveExecutionsListeners = new ArrayList<>();
        private int contexts;
        private int activeExecutions;
    }
}
