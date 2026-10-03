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
import org.graalvm.polyglot.Engine;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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
 * <p>
 * Generated code is also reached by platform entry points that run before any application context
 * exists at all ({@code TestPropertyProvider.getProperties()}, the {@code contextBuilder} of
 * {@code @MicronautTest}, a reflective no-arg instantiation). {@link #require()} then builds a
 * default context for them, and the application that starts next adopts it instead of building a
 * second one, so the Python objects created before the application are the ones it sees. That
 * capability is deliberately narrow: it is available only while no application has been recorded,
 * that is before the first application of the JVM and between the creation of an application
 * context builder and the application it builds (see {@link #expectApplication()}). Generated code
 * reached after an application shut down is a leftover reference, not an entry point, and keeps
 * failing with {@code GraalPy context has not been initialized} rather than silently starting a
 * second Python runtime.
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
    /**
     * Whether generated code that finds no runtime and no recorded application context may build a
     * default one for itself: {@code true} until an application context is recorded, and again from
     * the moment an application context builder is created (an application is on its way, and the
     * entry points of that application run before it is recorded) until that application records
     * itself or shuts down.
     * <p>
     * Generated code reached after the application that owned the runtime closed therefore keeps
     * failing loudly instead of bootstrapping a context of its own, while a platform entry point of
     * the application that is about to start gets one.
     */
    private static final AtomicBoolean OUTSIDE_APPLICATION = new AtomicBoolean(true);
    private static final Logger LOG = LoggerFactory.getLogger(PythonApplicationRuntime.class);
    /** The installed runtimes, the one generated code resolves last; guarded by itself. */
    private static final List<PythonApplicationRuntime> INSTALLED = new ArrayList<>();
    private static final AtomicBoolean REUSE_CONTEXT = new AtomicBoolean();
    /**
     * The runtime bootstrapped outside an application, until the application that starts next adopts
     * it; guarded by {@link #INSTALLED}.
     */
    private static @Nullable PythonApplicationRuntime standalone;

    private final Context context;
    private final @Nullable ClassLoader classLoader;
    /** The engine created for a context bootstrapped outside an application, which nothing else closes. */
    private final AtomicReference<@Nullable Engine> ownedEngine = new AtomicReference<>();
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
     * executable methods) then finds the same primary context the rest of the application uses. When
     * no application context is recorded but one is on its way, a default context is bootstrapped for
     * the entry point that reached Python before it; after an application shut down, nothing is
     * bootstrapped and the call fails.
     *
     * @return The installed runtime
     * @throws IllegalStateException When no runtime is installed and none can be bootstrapped
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
        // from here on generated code builds the context of this application; a call that arrives
        // with no application recorded at all is a leftover of it, not an entry point before it
        OUTSIDE_APPLICATION.set(false);
    }

    /**
     * Record that an application context is being prepared: its entry points
     * ({@code TestPropertyProvider.getProperties()} and everything else the platform runs while it
     * builds the context) reach generated code before {@link #bootstrapFrom(BeanContext)} records the
     * application, and a default context is bootstrapped for them.
     * <p>
     * Called for every application context builder that is created (see
     * {@code PythonRuntimeBootstrapConfigurer}), which is what separates an entry point of the
     * application that is starting from a leftover reference of one that shut down.
     */
    static void expectApplication() {
        OUTSIDE_APPLICATION.set(true);
    }

    /**
     * Stop installing the runtime from a bean context that is shutting down, when it is still the
     * recorded one.
     *
     * @param beanContext The bean context
     */
    static void forgetBootstrap(BeanContext beanContext) {
        WeakReference<BeanContext> recorded = BOOTSTRAP_CONTEXT.get();
        if (recorded != null && recorded.get() == beanContext && BOOTSTRAP_CONTEXT.compareAndSet(recorded, null)) {
            // generated code reached from here on belongs to no application: a leftover reference of
            // the one that shut down, which must fail rather than bootstrap a runtime of its own
            OUTSIDE_APPLICATION.set(false);
        }
    }

    /**
     * Install the runtime by creating the GraalPy context bean of the recorded application context,
     * or, when no application context is recorded, by building a default context for the platform
     * entry point that reached Python before any application.
     *
     * @return The installed runtime, or {@code null} when a primary context is already being built
     * on this thread
     */
    private static @Nullable PythonApplicationRuntime bootstrap() {
        // generated code reached while a primary context is being built (main.py) cannot build a second one
        if (BOOTSTRAPPING.get()) {
            return null;
        }
        WeakReference<BeanContext> recorded = BOOTSTRAP_CONTEXT.get();
        BeanContext beanContext = recorded == null ? null : recorded.get();
        BOOTSTRAPPING.set(true);
        try {
            if (beanContext == null) {
                // a platform entry point running before any application context exists; a call that
                // arrives after an application shut down is a leftover reference and gets no context
                return OUTSIDE_APPLICATION.get() ? bootstrapStandalone() : null;
            }
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
     * Build the primary context of a JVM that reaches generated Python code before any application
     * context exists. {@code TestPropertyProvider.getProperties()}, the {@code contextBuilder} of
     * {@code @MicronautTest} and the reflective no-arg instantiation of a platform entry point all run
     * before the application starts, and a generated constructor reached from one of them has no bean
     * context to build the primary context from.
     * <p>
     * The context is built with the default configuration, exactly like
     * {@link GraalPyContextFactory#bootstrapReusableContext(ClassLoader)} but without making context
     * reuse the JVM-wide policy, and stays installed until {@link #adoptStandalone(ClassLoader)} hands
     * it to the application that starts next: the Python objects a platform entry point created are
     * then the ones the application sees, and the application closes the context when it shuts down.
     * <p>
     * Like the primary context of an application, the context is built without holding
     * {@link #INSTALLED}: building it evaluates {@code main.py} and the
     * {@code GraalPyContextCustomizer} services, and generated code another thread reaches while that
     * runs takes the same monitor ({@link #isInstalled(Context)} is called from generated code). The
     * monitor is taken only to publish the result, and a context that lost the race is closed with
     * the engine created for it.
     *
     * @return The installed runtime
     */
    private static PythonApplicationRuntime bootstrapStandalone() {
        PythonApplicationRuntime installed = CURRENT.get();
        if (installed != null) {
            return installed;
        }
        ClassLoader classLoader = standaloneClassLoader();
        Engine engine = GraalPyEngineFactory.buildPythonEngine();
        PythonApplicationRuntime runtime;
        try {
            Context context = GraalPyContextFactory.buildContext(
                GraalPyContextFactory.bootstrapHostAccess(classLoader), engine, classLoader);
            runtime = new PythonApplicationRuntime(context, classLoader);
        } catch (IOException | RuntimeException e) {
            // the engine was created for this context alone
            GraalPyContextFactory.closeQuietly(engine);
            throw new IllegalStateException("Failed to initialize the default GraalPy context: " + e.getMessage(), e);
        }
        runtime.ownedEngine.set(engine);
        PythonApplicationRuntime raced;
        synchronized (INSTALLED) {
            raced = CURRENT.get();
            if (raced == null) {
                standalone = runtime;
                install(runtime);
            }
        }
        if (raced != null) {
            // another thread published a runtime while this context was being built: closing a
            // context runs guest code, so it happens outside the monitor
            LOG.debug("Discarding the GraalPy context bootstrapped in parallel with the installed one");
            runtime.closeOwned();
            return raced;
        }
        return runtime;
    }

    /**
     * The class loader a context bootstrapped outside an application is built with: the one of the
     * thread that reached Python, so the application that adopts the context can be started from the
     * same class loader.
     * <p>
     * It decides more than which Java classes Python code can look up: it is also the loader
     * {@link GraalPyContextFactory#bootstrapHostAccess(ClassLoader)} loads the {@code TargetTypeMapping}
     * services from, and the one {@code GraalPyContextCustomizer} services are discovered with. An
     * entry point that runs under a class loader that sees fewer of those services than the
     * application does therefore builds a context with fewer host conversions than the application
     * would have built, which is why {@link #adoptStandalone(ClassLoader)} requires the application to
     * be started from the very same loader and logs when it is not.
     *
     * @return The class loader
     */
    private static ClassLoader standaloneClassLoader() {
        ClassLoader threadClassLoader = Thread.currentThread().getContextClassLoader();
        if (threadClassLoader != null) {
            return threadClassLoader;
        }
        ClassLoader ownClassLoader = PythonApplicationRuntime.class.getClassLoader();
        return ownClassLoader != null ? ownClassLoader : ClassLoader.getSystemClassLoader();
    }

    /**
     * Hand a context bootstrapped outside an application to the application that is starting, which
     * owns it from then on: a Python object a platform entry point created before the application
     * belongs to the application's primary context, instead of a context the application knows
     * nothing about.
     * <p>
     * The application must be started from the very class loader that built the context: that loader
     * decided which host classes the context can look up and which {@code TargetTypeMapping} services
     * its host access carries (see {@link #standaloneClassLoader()}), so a context built by another
     * loader is not the context this application would have built. Such a context is left where it is
     * — the entry point that created Python objects in it keeps them — and the application builds its
     * own; the mismatch is logged, because the two contexts are then invisible to each other.
     *
     * @param classLoader The class loader of the application that is starting
     * @return The adopted runtime, or {@code null} when no context was bootstrapped outside an
     * application, or one was but another class loader built it
     */
    static @Nullable PythonApplicationRuntime adoptStandalone(@Nullable ClassLoader classLoader) {
        PythonApplicationRuntime adopted;
        ClassLoader bootstrapClassLoader;
        synchronized (INSTALLED) {
            adopted = standalone;
            if (adopted == null) {
                return null;
            }
            bootstrapClassLoader = adopted.classLoader();
            if (classLoader != null && bootstrapClassLoader != classLoader) {
                adopted = null;
            } else {
                standalone = null;
                install(adopted);
            }
        }
        if (adopted == null) {
            LOG.warn("A GraalPy context was bootstrapped before the application context by class loader {}, " +
                "but the application is starting from {}: the application builds a second context, and the " +
                "Python objects created before it live in the first one. The host classes and TargetTypeMapping " +
                "services of the two contexts are those of their own class loader.", bootstrapClassLoader, classLoader);
        }
        return adopted;
    }

    /**
     * Close the engine created for a context this runtime bootstrapped outside an application, once
     * that context is closed. Nothing else owns that engine: the engine of an application is a bean.
     * <p>
     * Called for a context an application adopted and then destroyed; a context that is dropped
     * without ever being adopted is disposed of by {@link #closeOwned()} instead.
     */
    void closeOwnedEngine() {
        Engine engine = ownedEngine.getAndSet(null);
        if (engine != null) {
            GraalPyContextFactory.closeQuietly(engine);
        }
    }

    /**
     * Close the context this runtime bootstrapped outside an application, and then the engine created
     * for it, when it is dropped without ever being adopted: no bean holds either of them, so nothing
     * else ever closes them and the engine (native memory and compiler threads) would live for the
     * life of the JVM.
     * <p>
     * A runtime whose context an application adopted no longer holds the engine here: the application
     * closes the context and {@link #closeOwnedEngine()} follows it, so this is a no-op for it, as it
     * is for the runtime of an application, whose context and engine are beans.
     */
    private void closeOwned() {
        Engine engine = ownedEngine.getAndSet(null);
        if (engine == null) {
            return;
        }
        try {
            PythonContextRegistry.unregisterContext(context);
            GraalPyContextFactory.closeQuietly(context);
        } finally {
            GraalPyContextFactory.closeQuietly(engine);
        }
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
        boolean removed;
        boolean neverAdopted;
        synchronized (INSTALLED) {
            removed = INSTALLED.remove(runtime);
            neverAdopted = standalone == runtime;
            if (neverAdopted) {
                standalone = null;
            }
            CURRENT.set(INSTALLED.isEmpty() ? null : INSTALLED.getLast());
        }
        if (neverAdopted) {
            // a context bootstrapped outside an application that no application ever adopted: closing
            // it runs guest code, so it happens outside the monitor
            runtime.closeOwned();
        }
        return removed;
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
     * <p>
     * A context bootstrapped outside an application that no application adopted is closed here with
     * the engine created for it: nothing else owns either of them.
     *
     * @return The runtimes that were installed, in installation order
     */
    static List<PythonApplicationRuntime> uninstallAll() {
        List<PythonApplicationRuntime> removed;
        PythonApplicationRuntime neverAdopted;
        synchronized (INSTALLED) {
            removed = List.copyOf(INSTALLED);
            INSTALLED.clear();
            neverAdopted = standalone;
            standalone = null;
            CURRENT.set(null);
            // the JVM-wide state is back to what it was before any application ran, entry points included
            OUTSIDE_APPLICATION.set(true);
        }
        if (neverAdopted != null) {
            // closing a context runs guest code, so it happens outside the monitor
            neverAdopted.closeOwned();
        }
        return removed;
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
