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
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.python.embedding.GraalPyResources;
import org.graalvm.python.embedding.VirtualFileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

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

    private final ApplicationContext applicationContext;
    private boolean providedContext = false;
    private final CompletableFuture<Void> gracefulShutdown = new CompletableFuture<>();

    public GraalPyContextFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Create and initialize the GraalPy context.
     * This bean loads on startup due to the @Context annotation.
     *
     * @param engine The engine
     * @param hostAccess The host access
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
            LOG.debug("Building Primary GraalPy context");
            long now = System.currentTimeMillis();
            var context = buildContext(hostAccess, engine, classLoader, contextConfiguration);

            // Make context available to bridge classes
            PythonContextRuntime.setContext(context, classLoader);
            LOG.debug("Created Primary GraalPy Context in {}ms", System.currentTimeMillis() - now);
            return context;

        } catch (Exception e) {
            throw new ApplicationStartupException(
                "Failed to initialize GraalPy context: " + e.getMessage(), e);
        }
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
        var context = buildContext(bootstrapHostAccess(classLoader), GraalPyEngineFactory.buildPythonEngine(), classLoader, contextConfiguration, applicationMain);
        PythonContextRuntime.setReuseContext(true);
        PythonContextRuntime.setContext(context, classLoader);
        return context;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static HostAccess bootstrapHostAccess(ClassLoader classLoader) {
        List<TargetTypeMapping<?>> mappings = (List) SoftServiceLoader.load(TargetTypeMapping.class, classLoader).collectAll();
        return new GraalPyHostAccessFactory().hostAccess(mappings);
    }

    static Context buildContext(HostAccess hostAccess, Engine engine, ClassLoader classLoader) throws IOException {
        return buildContext(hostAccess, engine, classLoader, new GraalPyContextConfiguration());
    }

    private static Context buildContext(HostAccess hostAccess,
                                        Engine engine,
                                        ClassLoader classLoader,
                                        GraalPyContextConfiguration contextConfiguration) throws IOException {
        return buildContext(hostAccess, engine, classLoader, contextConfiguration, APPLICATION_MAIN);
    }

    private static Context buildContext(HostAccess hostAccess,
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
            .allowValueSharing(true)
            .allowPolyglotAccess(PolyglotAccess.ALL)
            // Allow access to host classes
            .allowHostAccess(hostAccess)
            .hostClassLoader(classLoader)
            .engine(engine)
            .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
            .allowHostClassLookup(_ -> true);
        resolveVirtualEnvExecutable(System.getenv())
            .ifPresent(executable -> builder.option("python.Executable", executable.toString()));

        LOG.debug("Configured GraalPy Context.Builder in {}ms", System.currentTimeMillis() - now);

        now = System.currentTimeMillis();
        var context = builder.build();
        PythonContextRuntime.registerContext(context);
        LOG.debug("GraalPy Context Built in {}ms", System.currentTimeMillis() - now);

        // The per-context builtin is only needed by context-reuse tests. Avoid
        // evaluating another Python snippet during normal application startup.
        if (Boolean.getBoolean(CONTEXT_ID_PROPERTY)) {
            now = System.currentTimeMillis();
            String id = java.util.UUID.randomUUID().toString();
            context.eval(PYTHON, "import builtins; builtins.__MN_CTX_ID__ = '" + id + "'");
            LOG.debug("GraalPy Context ID registered in {}ms", System.currentTimeMillis() - now);
        }
        // Try to load the generated pyronaut_application.py from META-INF
        now = System.currentTimeMillis();
        evaluateMain(classLoader, INTERNAL_MAIN, context);
        evaluateMain(classLoader, applicationMain, context);
        LOG.debug("GraalPy main.py evaluated in {}ms", System.currentTimeMillis() - now);
        return context;
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
                String scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                LOG.trace("Resolved main.py source");
                LOG.trace(scriptContent);
                Source source = Source.newBuilder(PYTHON, scriptContent, mainPy)
                    .build();
                context.eval(source);
            }
        }
    }

    /**
     * Cleanup method called during application shutdown.
     * Resets the context in PythonContextRuntime to prevent memory leaks.
     */
    @Override
    public void onDestroyed(BeanDestroyedEvent<Context> event) {
        if (!PythonContextRuntime.isReuseContext()) {
            var ctx = event.getBean();
            if (ctx != null) {
                PythonContextRuntime.onNoActiveExecutionsAfterCurrentFrame(ctx, () -> {
                    closeContext(ctx);
                    if (!providedContext && PythonContextRuntime.isCurrentContext(ctx)) {
                        PythonContextRuntime.resetContext();
                    }
                });
                return;
            }
            if (!providedContext && PythonContextRuntime.isCurrentContext(ctx)) {
                PythonContextRuntime.resetContext();
            }
        }
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
                PythonContextRuntime.unregisterContext(ctx);
            }
        }
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        Context ctx = PythonContextRuntime.isInitialized() ? PythonContextRuntime.getContext() : null;
        if (ctx == null || PythonContextRuntime.isReuseContext()) {
            gracefulShutdown.complete(null);
            return gracefulShutdown;
        }
        if (gracefulShutdown.isDone()) {
            return gracefulShutdown;
        }
        PythonContextRuntime.onNoActiveExecutions(ctx, () -> gracefulShutdown.complete(null));
        return gracefulShutdown;
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return OptionalLong.of(PythonContextRuntime.activeExecutions());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

}
