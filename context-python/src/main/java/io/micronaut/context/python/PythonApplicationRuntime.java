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
import io.micronaut.core.annotation.Internal;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * The Python runtime of one application: its primary GraalPy context, the class loader that built
 * the context, the optional {@link PythonPool} and the executor that pooled calls are moved to
 * when they start on a virtual thread.
 * <p>
 * An instance is created by {@link GraalPyContextFactory} together with the primary context and is
 * exposed as a bean, so the pool and the asyncio configurer bind to the runtime of their own
 * application context. Generated bridge code has no application context at hand and goes through
 * the static entry points of {@link PythonContextRuntime}, which resolve the runtime installed last
 * by {@link #install(PythonApplicationRuntime)}; one JVM therefore serves generated code from one
 * application at a time. {@link #uninstall(PythonApplicationRuntime)} only clears the reference when
 * it still points at the given runtime, so a runtime that was replaced while its shutdown was pending
 * cannot remove its successor.
 * <p>
 * Context reuse ({@link #setReuseContext(boolean)}) is a JVM-wide policy: the installed runtime then
 * outlives the application contexts that use it and a reset only reloads the Python modules.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
final class PythonApplicationRuntime {

    private static final AtomicReference<@Nullable PythonApplicationRuntime> CURRENT = new AtomicReference<>();
    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();

    private final Context context;
    private final @Nullable ClassLoader classLoader;
    private volatile @Nullable PythonPool pool;
    private volatile @Nullable BeanProvider<ExecutorService> pooledExecutorServiceProvider;

    /**
     * @param context The primary context
     * @param classLoader The application class loader that built the context, when known
     */
    PythonApplicationRuntime(Context context, @Nullable ClassLoader classLoader) {
        this.context = context;
        this.classLoader = classLoader;
    }

    /**
     * The runtime generated code resolves.
     *
     * @return The installed runtime, or {@code null} when no application is running
     */
    static @Nullable PythonApplicationRuntime current() {
        return CURRENT.get();
    }

    /**
     * The runtime generated code resolves, which must be installed.
     *
     * @return The installed runtime
     * @throws IllegalStateException When no runtime is installed
     */
    static PythonApplicationRuntime require() {
        PythonApplicationRuntime runtime = CURRENT.get();
        if (runtime == null) {
            throw new IllegalStateException("GraalPy context has not been initialized. " +
                "Make sure micronaut-context-python is on the classpath.");
        }
        return runtime;
    }

    /**
     * Make a runtime the one generated code resolves.
     *
     * @param runtime The runtime
     */
    static void install(PythonApplicationRuntime runtime) {
        CURRENT.set(runtime);
    }

    /**
     * Clear the installed runtime when it is still the given one.
     *
     * @param runtime The runtime being shut down
     * @return {@code true} when the runtime was installed and has been cleared
     */
    static boolean uninstall(PythonApplicationRuntime runtime) {
        return CURRENT.compareAndSet(runtime, null);
    }

    /**
     * If context reuse is set to true, then the context will never be cleared.
     *
     * @param reuse tells if the context should be reused
     */
    static void setReuseContext(boolean reuse) {
        REUSE_CONTEXT.set(reuse);
    }

    /**
     * Returns true if the context should be reused.
     *
     * @return {@code true} when the primary context outlives the application contexts using it
     */
    static boolean isReuseContext() {
        return REUSE_CONTEXT.get();
    }

    /**
     * The primary context of the application.
     *
     * @return The primary context
     */
    Context context() {
        return context;
    }

    /**
     * Whether a context is this runtime's primary context.
     *
     * @param candidate A context, or a view of one
     * @return {@code true} when the candidate is this runtime's primary context
     */
    boolean owns(@Nullable Context candidate) {
        return context.equals(candidate);
    }

    /**
     * The pool of the application.
     *
     * @return The pool, or {@code null} when pooling is disabled
     */
    @Nullable PythonPool pool() {
        return pool;
    }

    /**
     * Register the active pool after the pool bean decides whether pooling is enabled.
     * <p>
     * A {@code null} pool intentionally routes generated bridge calls back to the primary context.
     *
     * @param pool The pool, or {@code null}
     */
    void pool(@Nullable PythonPool pool) {
        this.pool = pool;
    }

    /**
     * The executor pooled calls are moved to when they start on a virtual thread.
     *
     * @return The executor provider, or {@code null} when unavailable
     */
    @Nullable BeanProvider<ExecutorService> pooledExecutorServiceProvider() {
        return pooledExecutorServiceProvider;
    }

    /**
     * Register the executor used when pooled Python execution is requested from a virtual thread.
     *
     * @param provider The executor provider, or {@code null} when unavailable
     */
    void pooledExecutorServiceProvider(@Nullable BeanProvider<ExecutorService> provider) {
        this.pooledExecutorServiceProvider = provider;
    }

    /**
     * Run an action with the application class loader as the thread context class loader, so Python
     * code entered from arbitrary runtime threads can resolve host classes.
     *
     * @param action The action
     * @param <T> The result type
     * @return The action result
     */
    <T> T withContextClassLoader(Supplier<T> action) {
        ClassLoader loader = classLoader;
        if (loader == null) {
            return action.get();
        }
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        if (previous == loader) {
            return action.get();
        }
        thread.setContextClassLoader(loader);
        try {
            return action.get();
        } finally {
            thread.setContextClassLoader(previous);
        }
    }
}
