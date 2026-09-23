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
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.order.Ordered;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;

/**
 * Factory bean that creates and initializes the GraalPy context.
 * Loads the generated Python application script and makes the context
 * available via PythonContextRuntime for bridge classes to use.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Factory
@Experimental
public class GraalPyContextFactory implements BeanDestroyedEventListener<org.graalvm.polyglot.Context>, GracefulShutdownCapable, Ordered {
    public static final String APPLICATION_PATH = "META-INF/GRAALPY-VFS/micronaut-application";
    public static final String APPLICATION_SRC_PATH = APPLICATION_PATH + "/src/";
    public static final String INTERNAL_MAIN = "__main__.py";
    public static final String APPLICATION_MAIN = "main.py";
    public static final String PYRONAUT_MAIN_CLASS = "pyronaut_application.PyronautMain";
    /** Enables the per-context Python builtin used by context-reuse tests. */
    public static final String CONTEXT_ID_PROPERTY = "micronaut.python.context-id.enabled";
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyContextFactory.class);
    private static final Source LOAD_VFS_MODULE_SOURCE = Source.newBuilder(PYTHON, """
        import importlib.util as __micronaut_importlib_util
        import sys as __micronaut_sys
        import types as __micronaut_types

        def __micronaut_load_vfs_module(module_path):
            module = __micronaut_sys.modules.get('__main__')
            if module is None:
                module = __micronaut_types.ModuleType('__main__')
                __micronaut_sys.modules['__main__'] = module
            spec = __micronaut_importlib_util.spec_from_file_location('__main__', module_path)
            spec.loader.exec_module(module)
        """, "micronaut-load-vfs-module.py").cached(true).buildLiteral();
    /**
     * Python code that completes the members of every Java object with what GraalPy host interop does not
     * resolve: the trailing-underscore aliases of members named after a Python keyword
     * ({@code builder.from_(...)} for {@code Builder.from(...)}, {@code spec.and_(other)} for
     * {@code Specification.and(other)}), and the public methods a class inherits from a non-public
     * superclass (see {@link PythonHostMembers}).
     * <p>
     * The compiler rewrites keyword aliases only on names it can resolve statically (imported Java
     * classes and {@code java.type(...)} aliases). Objects that Java returns at runtime are plain GraalPy
     * foreign objects, so the alias is resolved here instead: a Python class registered with
     * {@code polyglot.register_interop_type} for {@code java.lang.Object} enters the type of every host
     * object instance and its {@code __getattr__} runs only after the regular foreign member lookup has
     * failed, retrying with the underscore stripped (the rule the compiler applies, {@code keyword.iskeyword},
     * so the same spelling works everywhere) and then asking the runtime for an inherited member.
     */
    private static final Source JAVA_OBJECT_MEMBERS_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_register_java_object_members():
            import keyword
            import java
            from polyglot import register_interop_type
            host_members = java.type('io.micronaut.context.python.PythonHostMembers')

            class MicronautJavaObject:
                __slots__ = ()

                def __getattr__(self, name):
                    if name.endswith('_') and keyword.iskeyword(name[:-1]):
                        try:
                            return getattr(self, name[:-1])
                        except AttributeError:
                            pass  # report the spelling the caller used, not the stripped one
                    if not name.startswith('__'):
                        member = host_members.inheritedMember(self, name)
                        if member is not None:
                            return member
                    raise AttributeError(f"foreign object has no attribute '{name}'")

            register_interop_type(java.type('java.lang.Object'), MicronautJavaObject)

        __micronaut_register_java_object_members()
        del __micronaut_register_java_object_members
        """, "micronaut-java-object-members.py").cached(true).buildLiteral();
    /**
     * Installs the Python view of the generated Java wrappers ({@link ValueCoercible}). A wrapper a
     * Java call returns to Python is a foreign object; GraalPy treats an instance of a registered
     * interop type as an instance of the registered Python class, so the view makes the wrapper
     * report the class of, compare equal to, hash like and print as the Python object it wraps,
     * while its attributes and methods remain those of the wrapper (which delegates to the object).
     */
    private static final Source JAVA_WRAPPER_VIEW_SOURCE = Source.newBuilder(PYTHON, """
        def __micronaut_register_java_wrapper_view(wrapper_class):
            import polyglot

            def unwrap(value):
                return value.asPolyglotValue() if isinstance(value, JavaWrapperView) else value

            class JavaWrapperView:
                @property
                def __class__(self):
                    return type(self.asPolyglotValue())

                def __eq__(self, other):
                    return self.asPolyglotValue() == unwrap(other)

                def __ne__(self, other):
                    return self.asPolyglotValue() != unwrap(other)

                def __hash__(self):
                    return hash(self.asPolyglotValue())

                def __repr__(self):
                    return repr(self.asPolyglotValue())

                def __str__(self):
                    return str(self.asPolyglotValue())

            try:
                polyglot.register_interop_type(wrapper_class, JavaWrapperView)
            except KeyError:
                pass  # already registered in this context
        """, "micronaut-java-wrapper-view.py").cached(true).buildLiteral();

    private final ApplicationContext applicationContext;
    private boolean providedContext = false;
    /** The runtime installed for the context this factory built; {@code null} for a provided (reused) context. */
    private final AtomicReference<@Nullable PythonApplicationRuntime> runtime = new AtomicReference<>();
    private final CompletableFuture<Void> gracefulShutdown = new CompletableFuture<>();

    public GraalPyContextFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Create and initialize the GraalPy context.
     * This bean loads on startup due to the @Context annotation; generated Python code that runs
     * before the eager beans are initialized (type converters, beans of {@code processOnStartup}
     * executable methods) creates it earlier through {@link PythonRuntimeBootstrapConfigurer}.
     * <p>
     * When a platform entry point reached Python before this application context existed, the context
     * it was given is adopted as the primary context and the injected {@code hostAccess},
     * {@code engine} and {@code contextConfiguration} are <em>not</em> applied to it: they describe
     * beans of an application that did not exist when the context was built. The adopted context
     * carries the default {@link GraalPyContextConfiguration}, an engine of its own and the host
     * access of the class loader that bootstrapped it (its {@code TargetTypeMapping} services),
     * while the {@code GraalPyContextCustomizer} services and the {@link #CONTEXT_ID_PROPERTY}
     * system property, which are not bean-resolved, do apply to it. An application that configures
     * {@code graalpy.context.*} or supplies its own {@code @Named("python")} {@code HostAccess} or
     * {@code Engine} bean therefore behaves differently depending on whether something reached Python
     * before it started, so the configuration that was not applied is logged as a warning. An
     * application that reaches Python only from its own beans builds its context here and is
     * unaffected.
     *
     * @param engine The engine
     * @param hostAccess The host access
     * @param contextConfiguration The GraalPy context configuration
     * @return The initialized GraalPy context
     */
    @io.micronaut.context.annotation.Context
    @Singleton
    @Named(PYTHON)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public org.graalvm.polyglot.Context graalPyContext(
        @Named(PYTHON) HostAccess hostAccess,
        @Named(PYTHON) Engine engine,
        GraalPyContextConfiguration contextConfiguration) {
        if (PythonContextRuntime.isInitialized() && PythonContextRuntime.isReuseContext()) {
            providedContext = true;
            // Reuse context: this is an optimization for reloading
            return PythonContextRuntime.getContext();
        }

        try {
            var classLoader = applicationContext.getClassLoader();
            PythonApplicationRuntime adopted = PythonApplicationRuntime.adoptStandalone(classLoader);
            if (adopted != null) {
                // a platform entry point (a TestPropertyProvider, a contextBuilder, a reflectively
                // instantiated bean) already created Python objects in a context bootstrapped for it:
                // adopt that context instead of building a second one the earlier objects do not live in
                LOG.debug("Adopting the GraalPy context bootstrapped before the application context");
                warnDiscardedConfiguration(contextConfiguration);
                runtime.set(adopted);
                return adopted.context();
            }
            LOG.debug("Building Primary GraalPy context");
            long now = System.currentTimeMillis();
            var context = buildContext(hostAccess, engine, classLoader, contextConfiguration);

            // Make context available to bridge classes
            runtime.set(PythonContextRuntime.setContext(context, classLoader));
            LOG.debug("Created Primary GraalPy Context in {}ms", System.currentTimeMillis() - now);
            return context;

        } catch (Exception e) {
            throw new ApplicationStartupException(
                "Failed to initialize GraalPy context: " + e.getMessage(), e);
        }
    }

    /**
     * Report the configuration of this application that the context it adopted was not built with: a
     * context bootstrapped before the application exists cannot be built from the application's beans,
     * so an application whose configuration is not the default one silently runs with another. The
     * same application then behaves differently depending on whether a platform entry point reached
     * Python before it started, which is what this line makes visible.
     *
     * @param contextConfiguration The context configuration of this application
     */
    private void warnDiscardedConfiguration(GraalPyContextConfiguration contextConfiguration) {
        List<String> discarded = new ArrayList<>(4);
        if (!contextConfiguration.getOptions().isEmpty()) {
            discarded.add(GraalPyContextConfiguration.PREFIX + ".options " + contextConfiguration.getOptions().keySet());
        }
        if (!contextConfiguration.getHostClassLookup().isEmpty()) {
            discarded.add(GraalPyContextConfiguration.PREFIX + ".host-class-lookup " + contextConfiguration.getHostClassLookup());
        }
        addUserSupplied(discarded, HostAccess.class, GraalPyHostAccessFactory.class);
        addUserSupplied(discarded, Engine.class, GraalPyEngineFactory.class);
        if (!discarded.isEmpty()) {
            LOG.warn("The GraalPy context adopted from the entry point that reached Python before this application " +
                "started was built before the application existed, so {} of this application {} not applied to it. " +
                "Reach Python only from the beans of the application to have its own configuration applied.",
                String.join(", ", discarded), discarded.size() == 1 ? "is" : "are");
        }
    }

    /**
     * Add the {@code @Named("python")} bean of the given type to the report when the application
     * supplies it itself, that is when it is not produced by the framework factory.
     *
     * @param discarded The report
     * @param beanType The bean type
     * @param defaultFactory The factory producing the framework's own bean
     * @param <T> The bean type
     */
    private <T> void addUserSupplied(List<String> discarded, Class<T> beanType, Class<?> defaultFactory) {
        applicationContext.findBeanDefinition(beanType, Qualifiers.byName(PYTHON))
            .filter(definition -> !defaultFactory.equals(definition.getDeclaringType().orElse(null)))
            .ifPresent(definition -> discarded.add("the " + beanType.getSimpleName() + " bean"));
    }

    /**
     * Create a reusable GraalPy context and evaluate the requested application bootstrap script.
     *
     * @param classLoader The application class loader
     * @return The initialized GraalPy context
     * @throws IOException If the context cannot load application resources
     */
    public static Context bootstrapReusableContext(ClassLoader classLoader) throws IOException {
        return bootstrapReusableContext(classLoader, Map.of());
    }

    /**
     * Create a reusable GraalPy context and evaluate the requested application bootstrap script.
     *
     * @param classLoader The application class loader
     * @param options Additional GraalPy context options
     * @return The initialized GraalPy context
     * @throws IOException If the context cannot load application resources
     */
    public static Context bootstrapReusableContext(ClassLoader classLoader,
                                                   Map<String, String> options) throws IOException {
        return bootstrapReusableContext(classLoader, options, APPLICATION_MAIN);
    }

    /**
     * Create a reusable GraalPy context and evaluate the requested application bootstrap script.
     *
     * @param classLoader The application class loader
     * @param options Additional GraalPy context options
     * @param applicationMain The Python source resource to evaluate after the generated launcher
     * @return The initialized GraalPy context
     * @throws IOException If the context cannot load application resources
     */
    public static Context bootstrapReusableContext(ClassLoader classLoader,
                                                   Map<String, String> options,
                                                   String applicationMain) throws IOException {
        if (PythonContextRuntime.isInitialized() && PythonContextRuntime.isReuseContext()) {
            return PythonContextRuntime.getContext();
        }
        GraalPyContextConfiguration contextConfiguration = new GraalPyContextConfiguration();
        contextConfiguration.getBuilder().options(options);
        Engine engine = GraalPyEngineFactory.buildPythonEngine();
        Context context = null;
        try {
            context = buildContext(bootstrapHostAccess(classLoader), engine, classLoader, contextConfiguration, applicationMain);
        } finally {
            if (context == null) {
                // the engine was created for this context alone; the bootstrap failure propagates
                closeQuietly(engine);
            }
        }
        PythonContextRuntime.setReuseContext(true);
        PythonContextRuntime.setContext(context, classLoader);
        return context;
    }

    static void closeQuietly(Engine engine) {
        try {
            engine.close(true);
        } catch (RuntimeException e) {
            LOG.warn("Failed to close the engine of a context that did not bootstrap", e);
        }
    }

    static void closeQuietly(Context context) {
        try {
            context.close(true);
        } catch (RuntimeException e) {
            LOG.warn("Failed to close a context that did not bootstrap", e);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static HostAccess bootstrapHostAccess(ClassLoader classLoader) {
        List<TargetTypeMapping<?>> mappings = (List) SoftServiceLoader.load(TargetTypeMapping.class, classLoader).collectAll();
        return new GraalPyHostAccessFactory().hostAccess(mappings, classLoader);
    }

    static Context buildContext(HostAccess hostAccess, Engine engine, ClassLoader classLoader) throws IOException {
        return buildContext(hostAccess, engine, classLoader, new GraalPyContextConfiguration());
    }

    static Context buildContext(HostAccess hostAccess,
                                Engine engine,
                                ClassLoader classLoader,
                                GraalPyContextConfiguration contextConfiguration) throws IOException {
        return buildContext(hostAccess, engine, classLoader, contextConfiguration, APPLICATION_MAIN);
    }

    static Context buildContext(HostAccess hostAccess,
                                Engine engine,
                                ClassLoader classLoader,
                                GraalPyContextConfiguration contextConfiguration,
                                String applicationMain) throws IOException {
        System.setProperty("org.graalvm.python.vfs.allow_multiple", "true");
        System.setProperty("org.graalvm.python.vfs.multiple_vfs_checks_as_warning", "true");
        long now = System.currentTimeMillis();


        Context.Builder builder = contextConfiguration.getBuilder()
            .apply(GraalPyResources.forVirtualFileSystem(VirtualFileSystem.newBuilder()
                .resourceDirectory(APPLICATION_PATH)
                .resourceClassLoader(classLoader).build()))
            .logHandler(new GraalPySlf4jLogHandler())
            .allowExperimentalOptions(true)
            .allowCreateProcess(true)
            .allowEnvironmentAccess(EnvironmentAccess.INHERIT)
            .allowValueSharing(true)
            .allowPolyglotAccess(PolyglotAccess.ALL)
            // Allow access to host classes
            .allowHostAccess(hostAccess)
            .hostClassLoader(classLoader)
            .engine(engine)
            .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
            // Python reaches Java through java.type(...) and "from a.b import C". By default the
            // filter accepts every class the application class loader can load, the application's
            // own packages included, which is what generated and user Python code needs. That also
            // exposes java.lang.Runtime, ProcessBuilder and the like, so an application that runs
            // Python it trusts less than its Java can narrow the surface with
            // graalpy.context.host-class-lookup; the filter then keeps the JDK, Jakarta and framework
            // packages the generated code depends on and adds only the configured packages.
            .allowHostClassLookup(contextConfiguration.hostClassFilter());
        resolveVirtualEnvExecutable(System.getenv())
            .ifPresent(executable -> builder.option("python.Executable", executable.toString()));
        GraalPyContextCustomizers.load(classLoader)
            .forEach(customizer -> customizer.customize(builder));

        LOG.debug("Configured GraalPy Context.Builder in {}ms", System.currentTimeMillis() - now);

        now = System.currentTimeMillis();
        var context = builder.build();
        PythonContextRegistry.registerContext(context);
        LOG.debug("GraalPy Context Built in {}ms", System.currentTimeMillis() - now);
        boolean bootstrapped = false;
        try {
            PythonContextRuntime.helper(context, "__micronaut_register_java_wrapper_view", JAVA_WRAPPER_VIEW_SOURCE)
                .executeVoid(ValueCoercible.class);
            // The per-context builtin is only needed by context-reuse tests. Avoid
            // evaluating another Python snippet during normal application startup.
            if (Boolean.getBoolean(CONTEXT_ID_PROPERTY)) {
                now = System.currentTimeMillis();
                String id = java.util.UUID.randomUUID().toString();
                context.eval(PYTHON, "import builtins; builtins.__MN_CTX_ID__ = '" + id + "'");
                LOG.debug("GraalPy Context ID registered in {}ms", System.currentTimeMillis() - now);
            }
            // Before any application code runs: Java objects answer to keyword-safe member aliases and to
            // the public methods GraalPy does not expose because a non-public superclass declares them
            now = System.currentTimeMillis();
            context.eval(JAVA_OBJECT_MEMBERS_SOURCE);
            LOG.debug("GraalPy Java object members registered in {}ms", System.currentTimeMillis() - now);
            // Before the application modules import: the Java packages, types and annotations they import
            // are served by the finder of the runtime module, from the manifests the compiler wrote
            now = System.currentTimeMillis();
            PythonContextRuntime.installJavaImportFinder(context);
            LOG.debug("GraalPy Java import finder installed in {}ms", System.currentTimeMillis() - now);
            // Try to load the generated pyronaut_application.py from META-INF
            now = System.currentTimeMillis();
            evaluateMain(classLoader, INTERNAL_MAIN, context);
            evaluateMain(classLoader, applicationMain, context);
            LOG.debug("GraalPy main.py evaluated in {}ms", System.currentTimeMillis() - now);
            bootstrapped = true;
            return context;
        } finally {
            if (!bootstrapped) {
                // a context that failed to bootstrap has no bean to destroy it: unregister and close it
                // here; the bootstrap failure propagates whatever the close does
                PythonContextRegistry.unregisterContext(context);
                closeQuietly(context);
            }
        }
    }

    static Optional<Path> resolveVirtualEnvExecutable(Map<String, String> environment) {
        String virtualEnv = environment.get("VIRTUAL_ENV");
        if (virtualEnv == null || virtualEnv.isBlank()) {
            return Optional.empty();
        }
        Path venv = Path.of(virtualEnv);
        for (Path executable : List.of(
            venv.resolve("bin/python"),
            venv.resolve("Scripts/python.exe")
        )) {
            if (Files.isRegularFile(executable)) {
                return Optional.of(executable);
            }
        }
        return Optional.empty();
    }

    private static void evaluateMain(ClassLoader classLoader, String mainPy, Context context) throws IOException {
        String mainPyPath = APPLICATION_SRC_PATH + mainPy;
        try (InputStream inputStream = classLoader
            .getResourceAsStream(mainPyPath)) {

            if (inputStream != null) {
                LOG.debug("Evaluating main.py {}", mainPyPath);
                String modulePath = "/graalpy_vfs/src/" + mainPy;
                Value loader = PythonContextRuntime.helper(context, "__micronaut_load_vfs_module", LOAD_VFS_MODULE_SOURCE);
                loader.executeVoid(modulePath);
            }
        }
    }

    /**
     * The Python runtime of this application, bound to the primary context.
     * <p>
     * The runtime this factory installed, not the one generated code currently resolves: while a
     * nested application is running, the enclosing application still binds its pool and its asyncio
     * configuration to its own runtime. A provided (reused) context has no runtime of its own, its
     * runtime is the installed one.
     *
     * @param context The primary context
     * @return The runtime installed for the context
     */
    @Singleton
    PythonApplicationRuntime pythonRuntime(@Named(PYTHON) org.graalvm.polyglot.Context context) {
        PythonApplicationRuntime applicationRuntime = providedContext ? PythonApplicationRuntime.current() : this.runtime.get();
        if (applicationRuntime == null || !applicationRuntime.owns(context)) {
            throw new IllegalStateException("The Python runtime is not installed for the primary GraalPy context");
        }
        return applicationRuntime;
    }

    /**
     * Cleanup method called during application shutdown.
     * <p>
     * When the destroyed context is the one this factory built, the runtime of this application is
     * uninstalled right away, so generated code of an enclosing application (a nested
     * {@code ApplicationContext.run(...)} in a Python test) resolves its own runtime again, and the
     * context is closed once it is idle. Any other context, one of an enclosing application or a
     * reused one, is left alone.
     */
    @Override
    public void onDestroyed(BeanDestroyedEvent<Context> event) {
        if (PythonContextRuntime.isReuseContext() || providedContext) {
            // the context outlives this application: the Python scoped proxies of its beans must not
            var ctx = event.getBean();
            if (ctx != null) {
                PythonContextRegistry.forgetScopedProxies(ctx);
            }
            return;
        }
        var ctx = event.getBean();
        PythonApplicationRuntime installedRuntime = this.runtime.get();
        if (installedRuntime == null || !installedRuntime.owns(ctx) || !this.runtime.compareAndSet(installedRuntime, null)) {
            return;
        }
        PythonApplicationRuntime.uninstall(installedRuntime);
        PythonContextRegistry.closeWhenIdleAfterCurrentFrame(ctx, () -> {
            try {
                closeContext(ctx);
            } finally {
                // an adopted context was bootstrapped with an engine of its own, which nothing else closes
                installedRuntime.closeOwnedEngine();
            }
        });
    }

    static void closeContext(Context ctx) {
        boolean closed = false;
        try {
            ctx.close(true);
            closed = true;
        } catch (IllegalStateException e) {
            LOG.warn("Unexpected failure while closing Python context", e);
            throw e;
        } catch (PolyglotException e) {
            if (!e.isCancelled()) {
                LOG.warn("Unexpected polyglot failure while closing Python context", e);
                throw e;
            }
            closed = true;
        } catch (AssertionError e) {
            LOG.warn("Unexpected assertion while closing Python context", e);
            throw e;
        } finally {
            if (closed) {
                PythonContextRegistry.unregisterContext(ctx);
            }
        }
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        PythonApplicationRuntime applicationRuntime = this.runtime.get();
        Context ctx = applicationRuntime != null ? applicationRuntime.context() : null;
        if (ctx == null || PythonContextRuntime.isReuseContext()) {
            gracefulShutdown.complete(null);
            return gracefulShutdown;
        }
        if (gracefulShutdown.isDone()) {
            return gracefulShutdown;
        }
        PythonContextRegistry.onNoActiveExecutions(ctx, () -> gracefulShutdown.complete(null));
        return gracefulShutdown;
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return OptionalLong.of(PythonContextRegistry.activeExecutions());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
