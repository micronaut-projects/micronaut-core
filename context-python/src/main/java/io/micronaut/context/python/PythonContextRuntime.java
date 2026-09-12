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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.exception.InstantiationException;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.lang.ScopedValue.CallableOp;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Runtime coordination point for generated Python bridge classes.
 * <p>
 * This type is the static entry point of generated bridge classes: it resolves the application
 * runtime installed by {@link GraalPyContextFactory} (see {@link PythonApplicationRuntime}), resolves
 * classes and scripts from the primary context or a {@link PythonPool}, mirrors host members into
 * event-loop contexts and executes the shared Python helper functions. Execution tracking and the
 * per-context state live in {@link PythonContextRegistry}.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonContextRuntime {
    /**
     * The GraalPy language id.
     */
    public static final String PYTHON = "python";

    private static final String NEW_UNINITIALIZED_INSTANCE = "__micronaut_new_uninitialized_instance";
    private static final String SET_INSTANCE_PROPERTY = "__micronaut_set_instance_property";
    private static final String SET_INSTANCE_PROPERTIES = "__micronaut_set_instance_properties";
    private static final String PREPARE_INTRODUCTION = "__micronaut_prepare_introduction";
    private static final String RUNTIME_MODULE_NAME = "micronaut_runtime";
    private static final String RUNTIME_MODULE_RESOURCE = "META-INF/GRAALPY-VFS/micronaut-application/src/micronaut_runtime.py";
    private static final Source IMPORT_RUNTIME_MODULE_SOURCE = Source.newBuilder(PYTHON, "__import__('" + RUNTIME_MODULE_NAME + "')", "micronaut-import-runtime.py").cached(true).buildLiteral();
    private static final Source LOAD_RUNTIME_MODULE_SOURCE = Source.newBuilder(PYTHON, """
        import sys as __micronaut_sys
        import types as __micronaut_types

        def __micronaut_load_runtime_module(source):
            module = __micronaut_types.ModuleType('micronaut_runtime')
            __micronaut_sys.modules['micronaut_runtime'] = module
            exec(source, module.__dict__)
            return module
        """, "micronaut-load-runtime-module.py").cached(true).buildLiteral();
    private static final AtomicReference<@Nullable String> RUNTIME_MODULE_FALLBACK_SOURCE = new AtomicReference<>();
    private static final Source RELOAD_MODULES_SOURCE = Source.newBuilder(PYTHON, """
        import importlib
        import sys
        for module in sys.modules.values():
            try:
                importlib.reload(module)
            except:
                pass
        """, "micronaut-reload-modules.py").cached(true).buildLiteral();

    private PythonContextRuntime() {
    }

    /**
     * The primary context of the installed application runtime.
     *
     * @return The primary context
     * @throws IllegalStateException When no application runtime is installed
     */
    public static Context getContext() {
        return PythonApplicationRuntime.require().context();
    }

    /**
     * Install the runtime of an application: its primary context and the class loader that should be
     * active when generated bridge classes enter Python from arbitrary runtime threads. This is
     * called by {@link GraalPyContextFactory} during application startup.
     *
     * @param context The primary GraalPy context
     * @param classLoader The application class loader used to build the context
     * @return The installed runtime
     */
    static PythonApplicationRuntime setContext(Context context, @Nullable ClassLoader classLoader) {
        PythonApplicationRuntime runtime = new PythonApplicationRuntime(context, classLoader);
        PythonApplicationRuntime.install(runtime);
        return runtime;
    }

    /**
     * Check if an application runtime is installed.
     *
     * @return true if the primary context is available, false otherwise
     */
    public static boolean isInitialized() {
        return PythonApplicationRuntime.current() != null;
    }

    /**
     * Check whether the supplied context is the primary context of the installed runtime.
     * <p>
     * {@link Context#equals(Object)} compares the underlying context, so the creator instance and the
     * view returned by {@link Value#getContext()} both match the primary context.
     *
     * @param context The context to compare
     * @return {@code true} when the context is the primary runtime context
     */
    static boolean isCurrentContext(@Nullable Context context) {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        return runtime == null ? context == null : runtime.owns(context);
    }

    /**
     * Uninstall the application runtime. This method is called during application shutdown
     * to ensure proper cleanup and prevent memory leaks; with context reuse enabled it only
     * reloads the Python modules of the primary context.
     */
    public static void resetContext() {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        if (runtime == null) {
            return;
        }
        if (isReuseContext()) {
            runtime.context().eval(RELOAD_MODULES_SOURCE);
            return;
        }
        PythonApplicationRuntime.uninstall(runtime);
        PythonContextRegistry.forgetContext(runtime.context());
    }

    /**
     * If context reuse is set to true, then the context will never be cleared.
     * @param reuse tells if the context should be reused
     */
    public static void setReuseContext(boolean reuse) {
        PythonApplicationRuntime.setReuseContext(reuse);
    }

    /**
     * Returns true if the context should be reused.
     * @return the reuse flag
     */
    public static boolean isReuseContext() {
        return PythonApplicationRuntime.isReuseContext();
    }

    /**
     * Whether generated calls must use the primary context: no pool is registered or the context is reused.
     */
    private static boolean usePrimaryContext() {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        return runtime == null || runtime.pool() == null || PythonApplicationRuntime.isReuseContext();
    }

    /**
     * Return the configured Python pool for package-local runtime routing.
     * <p>
     * Callers should check {@link #usePrimaryContext()} before invoking this method; a missing
     * pool means generated bridge calls must use the primary context instead.
     *
     * @return The configured PythonPool. Throws if not initialized.
     */
    static PythonPool getPythonPool() {
        PythonPool pool = PythonApplicationRuntime.require().pool();
        if (pool == null) {
            throw new IllegalStateException("PythonPool has not been initialized.");
        }
        return pool;
    }

    private static @Nullable BeanProvider<ExecutorService> pooledExecutorServiceProvider() {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        return runtime == null ? null : runtime.pooledExecutorServiceProvider();
    }

    private static <T> T withContextClassLoader(Supplier<T> action) {
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        return runtime == null ? action.get() : runtime.withContextClassLoader(action);
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
        PythonApplicationRuntime runtime = PythonApplicationRuntime.current();
        PythonPool pool = runtime == null ? null : runtime.pool();
        if (pool == null || isReuseContext()) {
            return fallback;
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop == null) {
            return fallback;
        }
        // the event-loop context's class load and the member copies are guest work: run them inside
        // an execution frame of that context so a close waits for them
        Context eventLoopContext = pool.getEventLoopContext(eventLoop);
        return PythonContextRegistry.withTrackedExecutionFrame(eventLoopContext, () -> {
            Value target = pool.getEventLoopClass(eventLoop, classReference);
            PythonCoercion.copyTransferableMembers(fallback, target);
            copyRememberedAsyncMembers(fallback, target);
            return target;
        });
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
        PythonContextRegistry.ContextState state = PythonContextRegistry.state(source.getContext());
        synchronized (state) {
            state.asyncMembers.computeIfAbsent(source, ignored -> new HashMap<>()).put(name, value);
        }
    }

    private static void copyRememberedAsyncMembers(Value source, Value target) {
        Map<String, Object> members;
        PythonContextRegistry.ContextState state = PythonContextRegistry.existingState(source.getContext());
        if (state == null) {
            return;
        }
        synchronized (state) {
            members = state.asyncMembers.get(source);
            if (members == null || members.isEmpty()) {
                return;
            }
            // not Map.copyOf: a member remembered as null is legitimate
            members = new HashMap<>(members);
        }
        members.forEach((name, value) -> PythonCoercion.putMember(target, name, PythonCoercion.asyncMemberValue(target, value)));
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
        if (usePrimaryContext()) {
            // the primary context is shared: the load runs inside a frame of it
            return withPrimaryContext(context -> findClass(classReference, context));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            // the load, and any injection the context is behind on, are guest work of the loop's context
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> pool.getEventLoopClass(eventLoop, classReference));
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
        // the caller owns the context (a borrowed pooled context, its loop's context); the load is
        // still counted as an execution so a close waits for it
        return PythonContextRegistry.withExecutionFrame(context, () -> usePrimaryContext()
            ? findClass(classReference, context)
            : getPythonPool().getClass(context, classReference));
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
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(findClass(classReference, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            // the load and the callback run in a tracked frame of the event-loop context: a close
            // waits for them and cannot be revived by them
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(pool.getEventLoopClass(eventLoop, classReference)));
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
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> findScript(packageName, scriptName, context));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> pool.getEventLoopScript(eventLoop, packageName, scriptName));
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
        return PythonContextRegistry.withExecutionFrame(context, () -> usePrimaryContext()
            ? findScript(packageName, scriptName, context)
            : getPythonPool().getScript(context, packageName, scriptName));
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
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(findScript(packageName, scriptName, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(pool.getEventLoopScript(eventLoop, packageName, scriptName)));
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
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(getOrCreateValue(context, expression)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(pool.getValue(context, expression)));
        }
        return getPythonPool().withValue(expression, fn);
    }

    private static Value getOrCreateValue(Context context, String expression) {
        // no context monitor around the eval: GraalPy holds its GIL across host calls, so a thread
        // waiting for the monitor while owning the GIL and one owning the monitor while waiting for
        // the GIL would deadlock; the concurrent map settles a duplicate evaluation instead
        PythonContextRegistry.ContextState state = PythonContextRegistry.state(context);
        String key = "value:" + expression;
        Value value = state.helpers.get(key);
        if (value == null || PythonConversion.isNone(value)) {
            value = context.eval(PYTHON, expression);
            Value existing = state.helpers.putIfAbsent(key, value);
            if (existing != null && !PythonConversion.isNone(existing)) {
                value = existing;
            }
        }
        return value;
    }

    private static boolean shouldOffloadPooledExecution() {
        BeanProvider<ExecutorService> provider = pooledExecutorServiceProvider();
        return provider != null
            && provider.isResolvable()
            && PythonAsyncioRuntime.currentEventLoopForContext() == null
            && Thread.currentThread().isVirtual();
    }

    private static <T> T offloadPooledExecution(Supplier<T> action) {
        BeanProvider<ExecutorService> provider = pooledExecutorServiceProvider();
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
        if (usePrimaryContext()) {
            // guest work on the shared primary context: framed, so a close waits for it
            withPrimaryContext(context -> {
                Value script = findScript(packageName, scriptName, context);
                script.putMember(attribute, PythonCoercion.coerceToContext(value, context));
                return null;
            });
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
        if (usePrimaryContext()) {
            withPrimaryContext(context -> {
                Value script = findScript(packageName, scriptName, context);
                script.putMember(attribute, PythonCoercion.asyncMemberValue(script, value));
                return null;
            });
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
        return withPooled(classReference, v -> PythonInvocation.invokePythonMethod(
            v,
            methodName,
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    /**
     * Invoke an async method on a pooled class instance and drive the coroutine while the borrowed
     * context is still leased to the call; the context is returned to the pool once the stage completes.
     *
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args Arguments
     * @return The stage of the coroutine
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static CompletionStage<?> invokePooledAsync(PythonClassReference classReference, String methodName, Object... args) {
        return withPooledStage(classReference, v -> PythonAsyncioRuntime.toCompletionStage(PythonInvocation.invokePythonMethod(
            v,
            methodName,
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        )));
    }

    /**
     * Invoke an async method on a pooled script and drive the coroutine while the borrowed context
     * is still leased to the call; the context is returned to the pool once the stage completes.
     *
     * @param packageName The package
     * @param scriptName The script name
     * @param methodName The method name
     * @param args Arguments
     * @return The stage of the coroutine
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static CompletionStage<?> invokePooledScriptAsync(String packageName, String scriptName, String methodName, Object... args) {
        return withPooledScriptStage(packageName, scriptName, v -> PythonAsyncioRuntime.toCompletionStage(v.getMember(methodName).execute(
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        )));
    }

    private static CompletionStage<?> withPooledStage(PythonClassReference classReference, Function<Value, CompletionStage<?>> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooledStage(classReference, fn));
        }
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(findClass(classReference, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            // the loop's own context: the coroutine continues on the loop, no lease to keep
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(pool.getEventLoopClass(eventLoop, classReference)));
        }
        return getPythonPool().withClassUntilComplete(classReference, fn);
    }

    private static CompletionStage<?> withPooledScriptStage(String packageName, String scriptName, Function<Value, CompletionStage<?>> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooledScriptStage(packageName, scriptName, fn));
        }
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(findScript(packageName, scriptName, context)));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(pool.getEventLoopScript(eventLoop, packageName, scriptName)));
        }
        return getPythonPool().withScriptUntilComplete(packageName, scriptName, fn);
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
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        ));
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
        return newIntroduction(getContext(), classReference, args);
    }

    /**
     * Create an abstract introduction instance in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param args The args
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newIntroduction(Context context, PythonClassReference classReference, Object... args) {
        // generated code reaches this outside any bridge call: the work is an execution of the context
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value pythonClass = findClass(classReference, context);
            // stubs the abstract methods once per class and context; the marker it sets makes later calls a no-op
            helper(context, PREPARE_INTRODUCTION).execute(pythonClass);
            return instantiate(classReference, args, pythonClass);
        });
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
     * Create an abstract introduction instance in a supplied context, omitting trailing defaults.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newIntroductionWithDefaultedTrailingNulls(Context context,
                                                                  PythonClassReference classReference,
                                                                  int requiredArgCount,
                                                                  Object... args) {
        return newIntroduction(context, classReference, trimDefaultedTrailingNulls(requiredArgCount, args));
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
        return newInstance(getContext(), classReference, args);
    }

    /**
     * Create a new instance in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param args The args
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstance(Context context, PythonClassReference classReference, Object... args) {
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value pythonClass = findClass(classReference, context);
            return instantiate(classReference, args, pythonClass);
        });
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
        return enumValue(getContext(), classReference, name);
    }

    /**
     * Resolve a Python enum constant by its Java enum name in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param name The enum constant name
     * @return The Python enum constant
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value enumValue(Context context, PythonClassReference classReference, String name) {
        return PythonContextRegistry.withExecutionFrame(context, () -> enumValueInFrame(context, classReference, name));
    }

    private static Value enumValueInFrame(Context context, PythonClassReference classReference, String name) {
        Value pythonClass = findClass(classReference, context);
        return withContextClassLoader(() -> {
            Value enumValue = pythonClass.getMember(name);
            if (enumValue != null && !PythonConversion.isNone(enumValue)) {
                return enumValue;
            }
            Value members = pythonClass.getMember("__members__");
            if (members != null && members.hasHashEntries()) {
                enumValue = members.getHashValue(name);
                if (enumValue != null && !PythonConversion.isNone(enumValue)) {
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
     * Create a new instance in a supplied context, omitting trailing defaults.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param requiredArgCount The number of non-defaulted positional constructor arguments
     * @param args The arguments
     * @return The new instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstanceWithDefaultedTrailingNulls(Context context,
                                                              PythonClassReference classReference,
                                                              int requiredArgCount,
                                                              Object... args) {
        return newInstance(context, classReference, trimDefaultedTrailingNulls(requiredArgCount, args));
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
        return newUninitializedInstance(getContext(), classReference);
    }

    /**
     * Create an instance without invoking {@code __init__} in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @return The new uninitialized instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newUninitializedInstance(Context context, PythonClassReference classReference) {
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value pythonClass = findClass(classReference, context);
            return uninitializedInstanceFactory(context).execute(pythonClass);
        });
    }

    /**
     * Create an instance without invoking {@code __init__} and populate its properties in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param props Map of property names to values
     * @return The populated uninitialized instance
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newUninitializedInstance(Context context,
                                                 PythonClassReference classReference,
                                                 @Nullable Map<String, Object> props) {
        // one frame from the class lookup to the last member write
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value instance = newUninitializedInstance(context, classReference);
            populateProperties(instance, props);
            return instance;
        });
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
    public static Value newInstance(PythonClassReference classReference, @Nullable Map<String, Object> props) {
        return newInstance(getContext(), classReference, props);
    }

    /**
     * Create a new instance and populate its properties in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param props Map of property names to values
     * @return The new instance with members populated
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newInstance(Context context, PythonClassReference classReference, @Nullable Map<String, Object> props) {
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value pythonClass = findClass(classReference, context);
            return withContextClassLoader(() -> {
                Value instance = instantiate(classReference, new Object[0], pythonClass);
                populateProperties(instance, props);
                return instance;
            });
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
    public static Value newFrozenDataclassInstance(PythonClassReference classReference, @Nullable Map<String, Object> props) {
        return newFrozenDataclassInstance(getContext(), classReference, props);
    }

    /**
     * Create a frozen dataclass instance and populate its properties in a supplied context.
     *
     * @param context The target context
     * @param classReference The Python class reference
     * @param props Map of property names to values
     * @return The new instance with members populated
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static Value newFrozenDataclassInstance(Context context,
                                                   PythonClassReference classReference,
                                                   @Nullable Map<String, Object> props) {
        return PythonContextRegistry.withExecutionFrame(context, () -> {
            Value pythonClass = findClass(classReference, context);
            return withContextClassLoader(() -> {
                Value instance = uninitializedInstanceFactory(pythonClass.getContext()).execute(pythonClass);
                populateProperties(instance, props);
                return instance;
            });
        });
    }

    private static void populateProperties(Value instance, @Nullable Map<String, Object> props) {
        if (props != null && !props.isEmpty()) {
            Value propertySetter = propertySetter(instance.getContext());
            for (java.util.Map.Entry<String, Object> e : props.entrySet()) {
                propertySetter.execute(
                    instance,
                    e.getKey(),
                    PythonCoercion.coerceToContext(e.getValue(), instance.getContext())
                );
            }
        }
    }

    /**
     * Set a property on an instance without invoking an overridden {@code __setattr__} implementation.
     *
     * @param instance The target instance
     * @param name The property name
     * @param value The property value
     * @since 5.2.0
     */
    @UsedByGeneratedCode
    public static void setInstanceProperty(Value instance, String name, @Nullable Object value) {
        propertySetter(instance.getContext()).execute(
            instance,
            name,
            PythonCoercion.coerceToContext(value, instance.getContext())
        );
    }

    private static Value uninitializedInstanceFactory(Context context) {
        return helper(context, NEW_UNINITIALIZED_INSTANCE);
    }

    private static Value propertySetter(Context context) {
        return helper(context, SET_INSTANCE_PROPERTY);
    }

    /**
     * The helper that assigns several instance attributes in one guest call.
     *
     * @param context The context
     * @return The helper function
     */
    static Value propertiesSetter(Context context) {
        return helper(context, SET_INSTANCE_PROPERTIES);
    }

    private static Value instantiate(PythonClassReference classReference, Object[] args, Value pythonClass) {
        return withContextClassLoader(() -> {
            if (pythonClass.canInstantiate()) {
                return pythonClass.newInstance(PythonCoercion.coerceArgumentsToContext(pythonClass.getContext(), args));
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
        // the primary context is shared: the import runs inside a frame of it
        return withPrimaryContext(context -> findClass(classReference, context));
    }

    static Value findClass(PythonClassReference classReference, Context ctx) {
        // Resolving a class means importing its module and asking inspect.isclass, several guest
        // calls that dominated the cost of rebuilding a dataclass in a pooled context. Classes are
        // therefore cached per context; the cache lives and dies with the context state.
        PythonContextRegistry.ContextState state = PythonContextRegistry.state(ctx);
        String cacheKey = classCacheKey(classReference);
        Value cached = state.classes.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        Value resolved = resolveClass(classReference, ctx);
        state.classes.put(cacheKey, resolved);
        return resolved;
    }

    private static String classCacheKey(PythonClassReference classReference) {
        String[] nested = classReference.nestedMemberNames();
        if (nested.length == 0) {
            return qualifiedName(classReference) + '#' + classReference.rootName();
        }
        return qualifiedName(classReference) + '#' + classReference.rootName() + '.' + String.join(".", nested);
    }

    private static Value resolveClass(PythonClassReference classReference, Context ctx) {
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
        // the primary context is shared: the import runs inside a frame of it
        return withPrimaryContext(context -> findScript(packageName, scriptName, context));
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
        return PythonContextRegistry.withExecutionFrame(ctx, () -> findClass(classReference, ctx)
            .invokeMember(methodName, PythonCoercion.coerceArgumentsToContext(ctx, args)));
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
        Value findClass = helper(ctx, "__micronaut_find_class_in_package_modules");
        return findClass.execute(packageName, importName);
    }

    private static boolean isPythonClass(Context ctx, @Nullable Value value) {
        if (value == null || PythonConversion.isNone(value)) {
            return false;
        }
        Value isClass = helper(ctx, "__micronaut_inspect_isclass");
        return isClass.execute(value).asBoolean();
    }

    private static Value importModule(Context ctx, String moduleName) {
        return withContextClassLoader(() -> {
            Value importModule = helper(ctx, "__micronaut_import_module");
            return importModule.execute(moduleName);
        });
    }

    /**
     * Resolve a cached helper function of the {@code micronaut_runtime} module inside the given context.
     * <p>
     * Helpers are stored per-context because Graal values cannot be shared across contexts. Helper
     * initialization deliberately avoids {@link PythonContextRegistry#withContextLock(Context, Supplier)} because GraalPy
     * operations acquire the Python GIL; taking the context monitor first can deadlock with another
     * thread that already owns the GIL and re-enters Micronaut runtime helper code.
     *
     * @param context The context that owns the helper function
     * @param name The function name in the runtime module
     * @return The helper function value for the context
     */
    /**
     * Run host-initiated Python code inside an execution frame of the context, so shutdown waits for
     * it and nested bridge calls share the frame.
     *
     * @param context The context the operation executes in
     * @param operation The operation
     * @param <T> The result type
     * @param <X> The checked exception type the operation may throw
     * @return The operation result
     * @throws X When the operation throws
     */
    @Internal
    public static <T, X extends Throwable> T withExecutionFrame(Context context, CallableOp<T, X> operation) throws X {
        return PythonContextRegistry.withExecutionFrame(context, operation);
    }

    /**
     * Run a callback inside an execution frame unless its context is closing or already closed, in
     * which case the callback is skipped; the decision and the execution count are one step.
     *
     * @param context The context of the callback
     * @param operation The callback
     * @return {@code false} when the callback was skipped
     */
    @Internal
    public static boolean tryWithExecutionFrame(Context context, Runnable operation) {
        return PythonContextRegistry.tryWithExecutionFrame(context, operation);
    }

    /**
     * Run an operation inside an execution frame of a context that must still be tracked and not
     * closing: unlike {@link #withExecutionFrame} this never creates the state of an unknown (closed)
     * context. Used for callbacks on channels that outlive a Python context.
     *
     * @param context The context
     * @param operation The operation
     * @param <T> The result type
     * @param <X> The exception type
     * @return The result
     * @throws X When the operation throws
     * @throws IllegalStateException When the context is closing or closed
     */
    @Internal
    public static <T, X extends Throwable> T withTrackedExecutionFrame(Context context, CallableOp<T, X> operation) throws X {
        return PythonContextRegistry.withTrackedExecutionFrame(context, operation);
    }

    /**
     * A function of the {@code micronaut_runtime} module, resolved once per context.
     *
     * @param context The context
     * @param name The function name
     * @return The function
     */
    @Internal
    public static Value helper(Context context, String name) {
        PythonContextRegistry.ContextState state = PythonContextRegistry.state(context);
        Value helper = state.helpers.get(name);
        if (helper != null) {
            return helper;
        }
        helper = runtimeModule(context, state).getMember(name);
        if (helper == null) {
            throw new IllegalStateException("The micronaut_runtime module does not define [" + name + "]");
        }
        Value existing = state.helpers.putIfAbsent(name, helper);
        return existing == null ? helper : existing;
    }

    /**
     * Resolve a helper installed by an explicit source, for bootstrap code that runs before the
     * runtime module can be imported.
     *
     * @param context The context that owns the helper function
     * @param name The binding name exposed by the helper source
     * @param source The source that installs the helper when absent
     * @return The helper function value for the context
     */
    static Value helper(Context context, String name, Source source) {
        PythonContextRegistry.ContextState state = PythonContextRegistry.state(context);
        Value helper = state.helpers.get(name);
        if (helper != null) {
            return helper;
        }
        Value bindings = context.getBindings(PYTHON);
        helper = bindings.getMember(name);
        if (helper == null || PythonConversion.isNone(helper)) {
            context.eval(source);
            helper = bindings.getMember(name);
        }
        Value existing = state.helpers.putIfAbsent(name, helper);
        return existing == null ? helper : existing;
    }

    private static Value runtimeModule(Context context, PythonContextRegistry.ContextState state) {
        Value module = state.runtimeModule.get();
        if (module != null) {
            return module;
        }
        try {
            module = context.eval(IMPORT_RUNTIME_MODULE_SOURCE);
        } catch (PolyglotException e) {
            // The virtual file system of this context does not carry the module (a bare context created
            // outside the application): load it from the classpath resource instead.
            String source = RUNTIME_MODULE_FALLBACK_SOURCE.get();
            if (source == null) {
                try (InputStream inputStream = PythonContextRuntime.class.getClassLoader().getResourceAsStream(RUNTIME_MODULE_RESOURCE)) {
                    if (inputStream == null) {
                        throw new IllegalStateException("Resource [" + RUNTIME_MODULE_RESOURCE + "] not found", e);
                    }
                    source = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (IOException ioException) {
                    throw new IllegalStateException("Unable to read [" + RUNTIME_MODULE_RESOURCE + "]", ioException);
                }
                RUNTIME_MODULE_FALLBACK_SOURCE.compareAndSet(null, source);
            }
            module = helper(context, "__micronaut_load_runtime_module", LOAD_RUNTIME_MODULE_SOURCE).execute(source);
        }
        state.runtimeModule.set(module);
        return module;
    }

    static <T extends @Nullable Object> T withPrimaryContext(Function<Context, T> callback) {
        Context primary = getContext();
        return PythonContextRegistry.withExecutionFrame(primary, () -> callback.apply(primary));
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

        // the generated members of a record compare an array by identity, which would make two references to
        // the same class unequal: the nested member names are compared, hashed and printed by their content
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PythonClassReference other)) {
                return false;
            }
            return Objects.equals(packageName, other.packageName)
                && Objects.equals(rootName, other.rootName)
                && Arrays.equals(nestedMemberNames, other.nestedMemberNames)
                && Objects.equals(displayName, other.displayName)
                && Objects.equals(cacheKey, other.cacheKey);
        }

        @Override
        public int hashCode() {
            return Objects.hash(packageName, rootName, Arrays.hashCode(nestedMemberNames), displayName, cacheKey);
        }

        @Override
        public String toString() {
            return "PythonClassReference[packageName=" + packageName
                + ", rootName=" + rootName
                + ", nestedMemberNames=" + Arrays.toString(nestedMemberNames)
                + ", displayName=" + displayName
                + ", cacheKey=" + cacheKey
                + ']';
        }
    }
}
