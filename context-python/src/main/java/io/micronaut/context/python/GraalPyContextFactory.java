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
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Factory bean that creates and initializes the GraalPy context.
 * Loads the generated Python application script and makes the context
 * available via ContextHolder for bridge classes to use.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Factory
public class GraalPyContextFactory implements BeanDestroyedEventListener<org.graalvm.polyglot.Context>, GracefulShutdownCapable, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(GraalPyContextFactory.class);
    public static final String APPLICATION_PATH = "META-INF/GRAALPY-VFS/micronaut-application";
    public static final String APPLICATION_SRC_PATH = APPLICATION_PATH + "/src/";
    public static final String INTERNAL_MAIN = "__main__.py";
    public static final String APPLICATION_MAIN = "main.py";
    public static final String PYRONAUT_MAIN_CLASS = "pyronaut_application.PyronautMain";
    private static final long CONTEXT_CLOSE_GRACE_PERIOD_MILLIS = Math.max(
        0,
        Long.getLong("micronaut.python.context.close.grace-period-millis", 5_000L)
    );
    private static final long CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS = Math.max(
        0,
        Long.getLong("micronaut.python.context.close.cancel-period-millis", 1_000L)
    );
    private static final AtomicInteger CONTEXT_CLOSE_THREAD_COUNTER = new AtomicInteger();
    private static final ExecutorService CONTEXT_CLOSE_EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "python-context-close-" + CONTEXT_CLOSE_THREAD_COUNTER.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });
    private static final ScheduledExecutorService CONTEXT_CLOSE_TIMEOUT_EXECUTOR = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "python-context-close-timeout");
        thread.setDaemon(true);
        return thread;
    });

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
        @Named(PYTHON) Engine engine) {
        if (ContextHolder.isInitialized() && ContextHolder.isReuseContext()) {
            providedContext = true;
            // Reuse context: this is an optimization for reloading
            return ContextHolder.getContext();
        }

        try {
            var classLoader = applicationContext.getClassLoader();
            var context = buildContext(hostAccess, engine, classLoader);

            // Make context available to bridge classes
            ContextHolder.setContext(context, classLoader);

            return context;

        } catch (Exception e) {
            throw new ApplicationStartupException(
                "Failed to initialize GraalPy context: " + e.getMessage(), e);
        }
    }

    public static @NonNull Context bootstrapReusableContext(@NonNull ClassLoader classLoader) throws IOException {
        return bootstrapReusableContext(classLoader, Map.of());
    }

    public static @NonNull Context bootstrapReusableContext(@NonNull ClassLoader classLoader,
                                                            @NonNull Map<String, String> options) throws IOException {
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
    public static @NonNull Context bootstrapReusableContext(@NonNull ClassLoader classLoader,
                                                            @NonNull Map<String, String> options,
                                                            @NonNull String applicationMain) throws IOException {
        if (ContextHolder.isInitialized() && ContextHolder.isReuseContext()) {
            return ContextHolder.getContext();
        }
        var context = buildContext(bootstrapHostAccess(classLoader), GraalPyEngineFactory.buildPythonEngine(), classLoader, options, applicationMain);
        ContextHolder.setReuseContext(true);
        ContextHolder.setContext(context, classLoader);
        return context;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static HostAccess bootstrapHostAccess(@NonNull ClassLoader classLoader) {
        List<TargetTypeMapping<?>> mappings = (List) SoftServiceLoader.load(TargetTypeMapping.class, classLoader).collectAll();
        return new GraalPyHostAccessFactory().hostAccess(mappings);
    }

    static @NonNull Context buildContext(HostAccess hostAccess, Engine engine, ClassLoader classLoader) throws IOException {
        return buildContext(hostAccess, engine, classLoader, Map.of());
    }

    private static @NonNull Context buildContext(HostAccess hostAccess,
                                                 Engine engine,
                                                 ClassLoader classLoader,
                                                 Map<String, String> options) throws IOException {
        return buildContext(hostAccess, engine, classLoader, options, APPLICATION_MAIN);
    }

    private static @NonNull Context buildContext(HostAccess hostAccess,
                                                 Engine engine,
                                                 ClassLoader classLoader,
                                                 Map<String, String> options,
                                                 String applicationMain) throws IOException {
        var beacon = findBeacon(classLoader);
        System.setProperty("org.graalvm.python.vfs.allow_multiple", "true");
        System.setProperty("org.graalvm.python.vfs.multiple_vfs_checks_as_warning", "true");
        var builder = GraalPyResources.contextBuilder(VirtualFileSystem.newBuilder()
                .resourceDirectory(APPLICATION_PATH)
                .resourceLoadingClass(beacon).build())
            .allowExperimentalOptions(true)
            .allowCreateProcess(true)
            .allowValueSharing(true)
            .allowPolyglotAccess(PolyglotAccess.ALL)
            .option("python.WarnExperimentalFeatures", "false")
             // Allow access to host classes
             .allowHostAccess(hostAccess)
             .hostClassLoader(classLoader)
             .engine(engine)
             .exceptionHandler(GraalPyExceptionHandler.RETHROW_HOST_RUNTIME_EXCEPTION)
             .allowHostClassLookup(name -> true);
        var pyEnv = System.getenv("PYENV_VERSION");
        var venv = System.getenv("VIRTUAL_ENV");
        if (pyEnv != null && venv != null && pyEnv.startsWith("graalpy")) {
            builder.option("python.Executable", Path.of(venv).resolve("bin/python").toString());
        }
        options.forEach(builder::option);

        var context = builder.build();
        ContextHolder.registerContextEngine(context, engine);
        context.initialize(PYTHON);
        // set a per-context unique id for tests and tracing via builtins
        String id = java.util.UUID.randomUUID().toString();
        context.eval(PYTHON, "import builtins; builtins.__MN_CTX_ID__ = '" + id + "'");

        // Try to load the generated pyronaut_application.py from META-INF
        evaluateMain(classLoader, INTERNAL_MAIN, context);
        evaluateMain(classLoader, applicationMain, context);
        return context;
    }

    private static void evaluateMain(ClassLoader classLoader, String mainPy, Context context) throws IOException {
        try (InputStream inputStream = classLoader
            .getResourceAsStream(APPLICATION_SRC_PATH + mainPy)) {

            if (inputStream != null) {
                String scriptContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                Source source = Source.newBuilder(PYTHON, scriptContent, mainPy)
                    .build();
                context.eval(source);
            }
        }
    }

    private static Class<?> findBeacon(ClassLoader classLoader) {
        try {
            return classLoader.loadClass(PYRONAUT_MAIN_CLASS);
        } catch (ClassNotFoundException e) {
            // will happen when compiled as a native image
            return GraalPyContextFactory.class;
        }
    }

    /**
     * Cleanup method called during application shutdown.
     * Resets the context in ContextHolder to prevent memory leaks.
     */
    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Context> event) {
        if (!ContextHolder.isReuseContext()) {
            var ctx = event.getBean();
            if (ctx != null) {
                ContextHolder.onNoActiveExecutions(ctx, () -> {
                    closeContextAsync(ctx, false).whenComplete((ignored, throwable) -> {
                        if (throwable != null) {
                            LOG.warn("Error while closing Python context: " + throwable.getMessage(), throwable);
                        }
                        if (!providedContext && ContextHolder.isCurrentContext(ctx)) {
                            ContextHolder.resetContext();
                        }
                    });
                });
                return;
            }
            if (!providedContext && ContextHolder.isCurrentContext(ctx)) {
                ContextHolder.resetContext();
            }
        }
    }

    static void closeContext(Context ctx, boolean cancelIfExecuting) {
        CompletableFuture<Void> close = closeContextAsync(ctx, cancelIfExecuting).toCompletableFuture();
        long waitMillis = contextCloseWaitMillis(cancelIfExecuting);
        try {
            close.get(waitMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(cause);
        } catch (TimeoutException e) {
            LOG.warn(
                "Python context close did not complete within {} ms; continuing shutdown. " +
                    "A daemon close thread may remain until GraalPy finishes its internal thread shutdown.",
                waitMillis
            );
        }
    }

    static CompletionStage<Void> closeContextAsync(Context ctx, boolean cancelIfExecuting) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        submitContextClose(ctx, cancelIfExecuting, result);
        scheduleContextCloseTimeouts(ctx, result, cancelIfExecuting);
        return result;
    }

    private static void submitContextClose(Context ctx, boolean cancelIfExecuting, CompletableFuture<Void> result) {
        CONTEXT_CLOSE_EXECUTOR.execute(() -> {
            if (result.isDone()) {
                return;
            }
            try {
                if (closeContextDirect(ctx, cancelIfExecuting) == ContextCloseResult.RETRY_WHEN_IDLE) {
                    if (!result.isDone()) {
                        ContextHolder.deferNoActiveExecutionListener(() ->
                            ContextHolder.onNoActiveExecutions(ctx, () -> submitContextClose(ctx, cancelIfExecuting, result))
                        );
                    }
                    return;
                }
                result.complete(null);
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
    }

    private static void scheduleContextCloseTimeouts(Context ctx,
                                                     CompletableFuture<Void> result,
                                                     boolean cancelIfExecuting) {
        long graceMillis = cancelIfExecuting ? CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS : CONTEXT_CLOSE_GRACE_PERIOD_MILLIS;
        scheduleContextCloseTimeout(() -> {
            if (result.isDone()) {
                return;
            }
            if (!cancelIfExecuting) {
                LOG.warn(
                    "Python context graceful close exceeded {} ms; continuing shutdown while the daemon close task finishes.",
                    CONTEXT_CLOSE_GRACE_PERIOD_MILLIS
                );
                result.complete(null);
            } else {
                completeTimedOutContextClose(ctx, result);
            }
        }, graceMillis);
    }

    private static void scheduleContextCloseTimeout(Runnable task, long delayMillis) {
        var ignored = CONTEXT_CLOSE_TIMEOUT_EXECUTOR.schedule(task, delayMillis, TimeUnit.MILLISECONDS);
    }

    private static void completeTimedOutContextClose(Context ctx, CompletableFuture<Void> result) {
        if (!result.isDone()) {
            if (ContextHolder.hasActiveExecutions(ctx)) {
                LOG.warn(
                    "Python context cancellation close exceeded {} ms, but the context is executing; deferring shutdown continuation.",
                    CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS
                );
                scheduleContextCloseTimeout(() -> completeTimedOutContextClose(ctx, result), CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS);
                return;
            }
            LOG.warn(
                "Python context cancellation close exceeded {} ms; continuing shutdown while the daemon close task finishes.",
                CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS
            );
            result.complete(null);
        }
    }

    private static long contextCloseWaitMillis(boolean cancelIfExecuting) {
        long waitMillis = CONTEXT_CLOSE_CANCEL_PERIOD_MILLIS + 500L;
        if (!cancelIfExecuting) {
            waitMillis += CONTEXT_CLOSE_GRACE_PERIOD_MILLIS;
        }
        return waitMillis;
    }

    private enum ContextCloseResult {
        CLOSED,
        RETRY_WHEN_IDLE
    }

    private static ContextCloseResult closeContextDirect(Context ctx, boolean cancelIfExecuting) {
        boolean closed = false;
        try {
            ctx.close(cancelIfExecuting);
            closed = true;
        } catch (IllegalStateException e) {
            if (cancelIfExecuting) {
                throw e;
            }
            /*
             * Bean destruction is the final owner of this Context. A graceful shutdown waits for
             * Micronaut-tracked Python bridge executions to finish, but GraalPy can still report
             * a transient entered context while unwinding a callback. Defer and retry instead of
             * cancelling immediately; cancellation during normal websocket teardown has exposed
             * GraalPy GIL assertions.
             */
            return ContextCloseResult.RETRY_WHEN_IDLE;
        } catch (PolyglotException e) {
            if (!e.isCancelled()) {
                throw e;
            }
            closed = true;
        } catch (AssertionError e) {
            /*
             * GraalPy can report this Truffle assertion while a context close is racing with
             * cancellation/unwinding of a just-finished Python execution. At this point the
             * runtime is already on the hard-close path, so treat only this exact assertion as
             * equivalent to a cancelled close and continue cleanup. Other assertion failures are
             * still propagated.
             */
            if (!"The TruffleContext must be entered.".equals(e.getMessage())) {
                throw e;
            }
            closed = true;
        } finally {
            if (closed) {
                ContextHolder.unregisterContextEngine(ctx);
            }
        }
        return ContextCloseResult.CLOSED;
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        Context ctx = ContextHolder.isInitialized() ? ContextHolder.getContext() : null;
        if (ctx == null || ContextHolder.isReuseContext()) {
            gracefulShutdown.complete(null);
            return gracefulShutdown;
        }
        if (gracefulShutdown.isDone()) {
            return gracefulShutdown;
        }
        ContextHolder.onNoActiveExecutions(ctx, () -> gracefulShutdown.complete(null));
        return gracefulShutdown;
    }

    @Override
    public OptionalLong reportActiveTasks() {
        return OptionalLong.of(ContextHolder.activeExecutions());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
