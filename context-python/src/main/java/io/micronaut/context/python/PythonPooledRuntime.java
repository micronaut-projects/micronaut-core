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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletionStage;

import static io.micronaut.context.python.PythonContextRuntime.PythonClassReference;
import static io.micronaut.context.python.PythonContextRuntime.enteredContext;
import static io.micronaut.context.python.PythonContextRuntime.getPythonPool;
import static io.micronaut.context.python.PythonContextRuntime.offloadPooledExecution;
import static io.micronaut.context.python.PythonContextRuntime.shouldOffloadPooledExecution;
import static io.micronaut.context.python.PythonContextRuntime.usePrimaryContext;
import static io.micronaut.context.python.PythonContextRuntime.withPrimaryContext;

/**
 * The runtime entry points of a pooled bean that owns its per-context values.
 *
 * <p>A pooled bean usually needs none of this: the pool caches one instance per context per class,
 * and every bean of that class is interchangeable. A bean needs its own values when it has
 * constructor arguments, because two beans of one class can then hold different dependencies, or
 * when it is AOP-proxied, because a Python proxy belongs to the context it was created in and a
 * pooled bean exists in every context. Either way the values live in a {@link PythonPooledInstance}
 * that the generated wrapper holds, and these are the methods the wrapper's generated code calls.
 *
 * <p>Each one that takes a {@code @Nullable PythonPooledInstance override} is the same entry point as
 * its counterpart in {@link PythonContextRuntime} plus that holder: when it is absent the call takes
 * the pool's per-class or per-module cache exactly as before, so a pooled type without advice and
 * without arguments is on the path it has always been on.
 *
 * <p>They all consult {@link PythonContextRuntime#enteredContext()} before choosing a context, for the
 * reason given there. {@link PythonPool} makes the same check before it borrows, which is what stops a
 * new entry point here from reintroducing the deadlock that came of choosing twice.
 *
 * @author graemerocher
 * @since 5.2.10
 */
@Internal
@Experimental
public final class PythonPooledRuntime {

    private PythonPooledRuntime() {
    }

    /**
     * A pooled class instance, or the per-context value of a holder that stands in for it.
     *
     * <p>A pooled class with no constructor arguments is served from the pool's per-class cache,
     * which is what the holder is null for. A holder appears when the bean is AOP-proxied: the
     * proxy is one Python object per context, created around the class rather than being an
     * instance of it, so it cannot live in that cache and the wrapper carries it instead. Without
     * this the generated bridge would reach the unproxied instance and the advice -- a
     * {@code @Transactional} boundary, for one -- would be skipped with nothing said.
     *
     * @param override The holder, or {@code null} to use the pool's per-class cache
     * @param classReference The Python class reference
     * @return The value to call
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable PythonPooledInstance override, PythonClassReference classReference) {
        return override == null ? PythonContextRuntime.findPooledClass(classReference) : findPooledInstance(override);
    }

    /**
     * As {@link #findPooledClass(PythonPooledInstance, PythonClassReference)}, in a context the
     * caller already owns.
     *
     * @param override The holder, or {@code null} to use the pool's per-class cache
     * @param classReference The Python class reference
     * @param context The context
     * @return The value to call
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledClass(@Nullable PythonPooledInstance override, PythonClassReference classReference, Context context) {
        return override == null ? PythonContextRuntime.findPooledClass(classReference, context) : findPooledInstance(override, context);
    }

    /**
     * Invoke a method on a pooled class, or on the holder standing in for it.
     *
     * @param override The holder, or {@code null} to use the pool's per-class cache
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value invokePooled(@Nullable PythonPooledInstance override, PythonClassReference classReference, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooled(classReference, methodName, args)
            : invokePooledInstance(override, methodName, args);
    }

    /**
     * As {@link #invokePooled(PythonPooledInstance, PythonClassReference, String, Object...)}, for
     * an async method.
     *
     * @param override The holder, or {@code null} to use the pool's per-class cache
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args Arguments
     * @return The stage of the coroutine
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static CompletionStage<?> invokePooledAsync(@Nullable PythonPooledInstance override, PythonClassReference classReference, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooledAsync(classReference, methodName, args)
            : invokePooledInstanceAsync(override, methodName, args);
    }

    /**
     * As {@link #invokePooled(PythonPooledInstance, PythonClassReference, String, Object...)}, for
     * an async generator method.
     *
     * @param override The holder, or {@code null} to use the pool's per-class cache
     * @param classReference The Python class reference
     * @param methodName The method name
     * @param args Arguments
     * @return The publisher
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Publisher<?> invokePooledPublisher(@Nullable PythonPooledInstance override, PythonClassReference classReference, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooledPublisher(classReference, methodName, args)
            : invokePooledInstancePublisher(override, methodName, args);
    }

    /**
     * The calling context's instance of a pooled bean that has constructor arguments.
     *
     * <p>Chooses the context exactly as {@link #findPooledClass(PythonClassReference)} does, then
     * asks the bean for its instance there. The instance belongs to the bean rather than to the
     * pool because the pool's cache is keyed by class, and two pooled beans of one class can hold
     * different dependencies.
     *
     * @param instance The pooled bean's per-context instances
     * @return The instance for the calling context
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledInstance(PythonPooledInstance instance) {
        if (usePrimaryContext()) {
            return withPrimaryContext(instance::in);
        }
        Context entered = enteredContext();
        if (entered != null) {
            return instance.in(entered);
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> instance.in(context));
        }
        return getPythonPool().withLeasedContext(instance::in);
    }

    /**
     * A pooled bean's instance in a context the caller already owns.
     *
     * @param instance The pooled bean's per-context instances
     * @param context The context
     * @return The instance for that context
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledInstance(PythonPooledInstance instance, Context context) {
        return PythonContextRegistry.withExecutionFrame(context, () -> instance.in(context));
    }

    /**
     * Invoke a method on a pooled bean that has constructor arguments.
     *
     * @param instance The pooled bean's per-context instances
     * @param methodName The method name
     * @param args Arguments
     * @return The polyglot result
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value invokePooledInstance(PythonPooledInstance instance, String methodName, Object... args) {
        return withPooledInstance(instance, v -> PythonInvocation.invokePythonMethod(
            v,
            methodName,
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        ));
    }

    /**
     * Invoke an async method on a pooled bean that has constructor arguments, driving the coroutine
     * while the context is still leased; see {@link #invokePooledAsync}.
     *
     * @param instance The pooled bean's per-context instances
     * @param methodName The method name
     * @param args Arguments
     * @return The stage of the coroutine
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static CompletionStage<?> invokePooledInstanceAsync(PythonPooledInstance instance, String methodName, Object... args) {
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> PythonAsyncioRuntime.toCompletionStage(
                PythonInvocation.invokePythonMethod(
                    instance.in(context),
                    methodName,
                    PythonCoercion.coerceArgumentsToContext(context, args)
                )));
        }
        Context entered = enteredContext();
        if (entered != null) {
            return PythonAsyncioRuntime.toCompletionStage(PythonInvocation.invokePythonMethod(
                instance.in(entered),
                methodName,
                PythonCoercion.coerceArgumentsToContext(entered, args)
            ));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> PythonAsyncioRuntime.toCompletionStage(
                PythonInvocation.invokePythonMethod(
                    instance.in(context),
                    methodName,
                    PythonCoercion.coerceArgumentsToContext(context, args)
                )));
        }
        return getPythonPool().withLeasedContextUntilComplete(context -> PythonAsyncioRuntime.toCompletionStage(
            PythonInvocation.invokePythonMethod(
                instance.in(context),
                methodName,
                PythonCoercion.coerceArgumentsToContext(context, args)
            )));
    }

    /**
     * Invoke an async generator method on a pooled bean that has constructor arguments; see
     * {@link #invokePooledPublisher}.
     *
     * @param instance The pooled bean's per-context instances
     * @param methodName The method name
     * @param args Arguments
     * @return The publisher
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Publisher<?> invokePooledInstancePublisher(PythonPooledInstance instance, String methodName, Object... args) {
        return withPooledInstance(instance, v -> PythonAsyncioRuntime.generatorToPublisher(PythonInvocation.invokePythonMethod(
            v,
            methodName,
            PythonCoercion.coerceArgumentsToContext(v.getContext(), args)
        )));
    }

    /**
     * Run a callback against a pooled bean's instance for the calling context, choosing the context
     * the way {@link #withPooled} does.
     *
     * @param instance The pooled bean's per-context instances
     * @param fn The callback
     * @param <T> The callback result type
     * @return The callback result
     */
    private static <T> T withPooledInstance(PythonPooledInstance instance, java.util.function.Function<Value, T> fn) {
        if (shouldOffloadPooledExecution()) {
            return offloadPooledExecution(() -> withPooledInstance(instance, fn));
        }
        if (usePrimaryContext()) {
            return withPrimaryContext(context -> fn.apply(instance.in(context)));
        }
        Context entered = enteredContext();
        if (entered != null) {
            return fn.apply(instance.in(entered));
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            PythonPool pool = getPythonPool();
            Context context = pool.getEventLoopContext(eventLoop);
            return PythonContextRegistry.withTrackedExecutionFrame(context, () -> fn.apply(instance.in(context)));
        }
        return getPythonPool().withLeasedContext(context -> fn.apply(instance.in(context)));
    }

    /**
     * A pooled module, or the per-context value of a holder that stands in for it.
     *
     * <p>A route module is a pooled type like any other, and an AOP-proxied one carries a holder of
     * one Python proxy per context for the same reason a class does; see
     * {@link #findPooledClass(PythonPooledInstance, PythonClassReference)}.
     *
     * @param override The holder, or {@code null} to use the pool's per-module cache
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @return The value to call
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(@Nullable PythonPooledInstance override, String packageName, String scriptName) {
        return override == null ? PythonContextRuntime.findPooledScript(packageName, scriptName) : findPooledInstance(override);
    }

    /**
     * As {@link #findPooledScript(PythonPooledInstance, String, String)}, in a context the caller
     * already owns.
     *
     * @param override The holder, or {@code null} to use the pool's per-module cache
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param context The context
     * @return The value to call
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value findPooledScript(@Nullable PythonPooledInstance override, String packageName, String scriptName, Context context) {
        return override == null ? PythonContextRuntime.findPooledScript(packageName, scriptName, context) : findPooledInstance(override, context);
    }

    /**
     * Invoke a module function, or the same function on the holder standing in for the module.
     *
     * @param override The holder, or {@code null} to use the pool's per-module cache
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param methodName The function name
     * @param args Arguments
     * @return The polyglot result
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Value invokePooledScript(@Nullable PythonPooledInstance override, String packageName, String scriptName, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooledScript(packageName, scriptName, methodName, args)
            : invokePooledInstance(override, methodName, args);
    }

    /**
     * As {@link #invokePooledScript(PythonPooledInstance, String, String, String, Object...)}, for an
     * async function.
     *
     * @param override The holder, or {@code null} to use the pool's per-module cache
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param methodName The function name
     * @param args Arguments
     * @return The stage of the coroutine
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static CompletionStage<?> invokePooledScriptAsync(@Nullable PythonPooledInstance override, String packageName, String scriptName, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooledScriptAsync(packageName, scriptName, methodName, args)
            : invokePooledInstanceAsync(override, methodName, args);
    }

    /**
     * As {@link #invokePooledScript(PythonPooledInstance, String, String, String, Object...)}, for an
     * async generator function.
     *
     * @param override The holder, or {@code null} to use the pool's per-module cache
     * @param packageName The Python package
     * @param scriptName The script/module name
     * @param methodName The function name
     * @param args Arguments
     * @return The publisher
     * @since 5.2.10
     */
    @UsedByGeneratedCode
    public static Publisher<?> invokePooledScriptPublisher(@Nullable PythonPooledInstance override, String packageName, String scriptName, String methodName, Object... args) {
        return override == null
            ? PythonContextRuntime.invokePooledScriptPublisher(packageName, scriptName, methodName, args)
            : invokePooledInstancePublisher(override, methodName, args);
    }
}
