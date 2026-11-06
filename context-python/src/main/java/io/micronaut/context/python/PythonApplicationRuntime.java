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

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.exceptions.NoSuchBeanException;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
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
 * application at a time. Applications nest: a test started by {@code @MicronautTest} runs a second
 * {@code ApplicationContext.run(...)} from Python, and every application owns its own primary context
 * and runtime. The installed runtimes are therefore kept in order, and
 * {@link #uninstall(PythonApplicationRuntime)} removes exactly the given runtime, wherever it is: when
 * the nested application shuts down, generated code resolves the runtime of the enclosing application
 * again, and an enclosing application that shuts down first cannot remove the nested one.
 * <p>
 * Context reuse ({@link #setReuseContext(boolean)}) is a JVM-wide policy: the installed runtime then
 * outlives the application contexts that use it and a reset only reloads the Python modules.
 * <p>
 * The primary context is a {@code @Context} bean, but generated code can run before the eager beans
 * are initialized: type converters are created first, and the beans of {@code processOnStartup}
 * executable methods (message listeners, scheduled jobs) are instantiated by their processors before
 * the eager beans. The application context that is starting is therefore recorded by
 * {@link #bootstrapFrom(BeanContext)} (see {@link PythonRuntimeBootstrapConfigurer}), and
 * {@link #require()} creates the GraalPy context bean of that application, which installs the
 * runtime, the first time generated code needs it before the bean exists. Like the installed
 * runtime, that record is JVM-wide and the last writer wins: when two application contexts start in
 * parallel, an early Python bean of one may build the GraalPy context of the other, exactly as it
 * would have resolved the other's installed runtime.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Internal
final class PythonApplicationRuntime {

    private static final AtomicReference<@Nullable PythonApplicationRuntime> CURRENT = new AtomicReference<>();
    // weakly held: a context whose start failed publishes no ShutdownEvent and stays recorded until the
    // next context replaces it, which must not keep the failed context alive
    private static final AtomicReference<@Nullable WeakReference<BeanContext>> BOOTSTRAP_CONTEXT = new AtomicReference<>();
    private static final ThreadLocal<Boolean> BOOTSTRAPPING = ThreadLocal.withInitial(() -> false);
    /** The installed runtimes, the one generated code resolves last; guarded by itself. */
    private static final List<PythonApplicationRuntime> INSTALLED = new ArrayList<>();
    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();

    private final Context context;
    private final @Nullable ClassLoader classLoader;
    private final AtomicReference<@Nullable PythonPool> pool = new AtomicReference<>();
    private final AtomicReference<@Nullable BeanProvider<ExecutorService>> pooledExecutorServiceProvider = new AtomicReference<>();

    /**
     * @param context The primary context
     * @param classLoader The application class loader that built the context, when known
     */
    PythonApplicationRuntime(Context context, @Nullable ClassLoader classLoader) {
        this.context = context;
        this.classLoader = classLoader;
    }

    /**
     * The application class loader that built the primary context.
     *
     * @return The class loader, or {@code null} when it is not known
     */
    @Nullable ClassLoader classLoader() {
        return classLoader;
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
     * <p>
     * When no runtime is installed but an application context is starting, the GraalPy context bean
     * of that application is created, which installs the runtime: generated code that runs before the
     * {@code @Context} beans are initialized (type converters, beans of {@code processOnStartup}
     * executable methods) then finds the same primary context the rest of the application uses.
     *
     * @return The installed runtime
     * @throws IllegalStateException When no runtime is installed and no application context can provide one
     */
    static PythonApplicationRuntime require() {
        PythonApplicationRuntime runtime = CURRENT.get();
        if (runtime == null) {
            runtime = bootstrap();
        }
        if (runtime == null) {
            throw new IllegalStateException("GraalPy context has not been initialized. " +
                "Make sure micronaut-context-python is on the classpath.");
        }
        return runtime;
    }

    /**
     * Record the application context that is starting, so the runtime can be installed on demand by
     * creating its GraalPy context bean.
     *
     * @param beanContext The bean context
     */
    static void bootstrapFrom(BeanContext beanContext) {
        BOOTSTRAP_CONTEXT.set(new WeakReference<>(beanContext));
    }

    /**
     * Stop installing the runtime from a bean context that is shutting down, when it is still the
     * recorded one.
     *
     * @param beanContext The bean context
     */
    static void forgetBootstrap(BeanContext beanContext) {
        WeakReference<BeanContext> recorded = BOOTSTRAP_CONTEXT.get();
        if (recorded != null && recorded.get() == beanContext) {
            BOOTSTRAP_CONTEXT.compareAndSet(recorded, null);
        }
    }

    /**
     * Install the runtime by creating the GraalPy context bean of the recorded application context.
     *
     * @return The installed runtime, or {@code null} when no application context is recorded
     */
    private static @Nullable PythonApplicationRuntime bootstrap() {
        WeakReference<BeanContext> recorded = BOOTSTRAP_CONTEXT.get();
        BeanContext beanContext = recorded == null ? null : recorded.get();
        // generated code reached while the context bean is being built (main.py) cannot build a second one
        if (beanContext == null || BOOTSTRAPPING.get()) {
            return null;
        }
        BOOTSTRAPPING.set(true);
        try {
            // creating the primary context bean installs the runtime (GraalPyContextFactory)
            beanContext.getBean(Context.class, Qualifiers.byName(PythonContextRuntime.PYTHON));
        } catch (NoSuchBeanException e) {
            // a bean context without the GraalPy context bean, such as the bootstrap context
            throw new IllegalStateException("GraalPy context has not been initialized: the bean context " +
                "provides no GraalPy context bean. Make sure micronaut-context-python is on the classpath.", e);
        } finally {
            BOOTSTRAPPING.remove();
        }
        return CURRENT.get();
    }

    /**
     * Make a runtime the one generated code resolves. A runtime installed earlier stays installed
     * behind it and is resolved again once this one is uninstalled.
     *
     * @param runtime The runtime
     */
    static void install(PythonApplicationRuntime runtime) {
        synchronized (INSTALLED) {
            INSTALLED.remove(runtime);
            INSTALLED.add(runtime);
            CURRENT.set(runtime);
        }
    }

    /**
     * Remove an installed runtime. Generated code then resolves the runtime installed before it, if
     * any; a runtime installed after it is not affected.
     *
     * @param runtime The runtime being shut down
     * @return {@code true} when the runtime was installed and has been removed
     */
    static boolean uninstall(PythonApplicationRuntime runtime) {
        synchronized (INSTALLED) {
            boolean removed = INSTALLED.remove(runtime);
            CURRENT.set(INSTALLED.isEmpty() ? null : INSTALLED.getLast());
            return removed;
        }
    }

    /**
     * Whether a context is the primary context of an installed runtime: the one generated code
     * resolves, or an enclosing application's that becomes current again once the nested one is
     * uninstalled. A context whose runtime was uninstalled (its application closed) is not.
     *
     * @param context The context, or a view of one
     * @return {@code true} when an installed runtime owns the context
     */
    static boolean isInstalled(Context context) {
        synchronized (INSTALLED) {
            for (PythonApplicationRuntime installed : INSTALLED) {
                if (installed.owns(context)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Remove every installed runtime.
     *
     * @return The runtimes that were installed, in installation order
     */
    static List<PythonApplicationRuntime> uninstallAll() {
        synchronized (INSTALLED) {
            List<PythonApplicationRuntime> removed = List.copyOf(INSTALLED);
            INSTALLED.clear();
            CURRENT.set(null);
            return removed;
        }
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
        return pool.get();
    }

    /**
     * Register the active pool after the pool bean decides whether pooling is enabled.
     * <p>
     * A {@code null} pool intentionally routes generated bridge calls back to the primary context.
     *
     * @param pool The pool, or {@code null}
     */
    void pool(@Nullable PythonPool pool) {
        this.pool.set(pool);
    }

    /**
     * The executor pooled calls are moved to when they start on a virtual thread.
     *
     * @return The executor provider, or {@code null} when unavailable
     */
    @Nullable BeanProvider<ExecutorService> pooledExecutorServiceProvider() {
        return pooledExecutorServiceProvider.get();
    }

    /**
     * Register the executor used when pooled Python execution is requested from a virtual thread.
     *
     * @param provider The executor provider, or {@code null} when unavailable
     */
    void pooledExecutorServiceProvider(@Nullable BeanProvider<ExecutorService> provider) {
        this.pooledExecutorServiceProvider.set(provider);
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
