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

import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runtime helpers for Python coroutine bridge methods.
 */
@Internal
public final class PythonAsyncioRuntime {
    private static final String SCHEDULER_NAME = "__micronaut_asyncio_to_completion_stage";
    private static final String AWAITABLE_FACTORY_NAME = "__micronaut_completion_stage_awaitable";
    private static final String AWAITABLE_COMPLETER_NAME = "__micronaut_complete_completion_stage_awaitable";
    private static final AtomicBoolean ENABLED = new AtomicBoolean(true);
    private static volatile List<PythonEventLoopProvider> eventLoopProviders = List.of();
    private static volatile @Nullable ExecutorService executorService;
    private static volatile @Nullable BeanProvider<ExecutorService> executorServiceProvider;
    private static final ExecutorAdapter EXECUTOR_ADAPTER = new ExecutorAdapter();
    private static final String ASYNCIO_MODULE_NAME = "micronaut_asyncio";
    private static final String ASYNCIO_MODULE_BINDING = "__micronaut_asyncio_module";
    private static final String ASYNCIO_MODULE_SOURCE = "META-INF/GRAALPY-VFS/micronaut-application/src/micronaut_asyncio.py";
    private static final ExceptionCompleter EXCEPTION_COMPLETER = new ExceptionCompleter();

    private PythonAsyncioRuntime() {
    }

    /**
     * Convert a Python coroutine or awaitable value into a Java {@link CompletionStage}.
     *
     * @param value The Python result value.
     * @return A stage that completes when the Python awaitable completes.
     */
    @SuppressWarnings({"rawtypes", "FutureReturnValueIgnored"})
    @UsedByGeneratedCode
    public static CompletionStage toCompletionStage(Value value) {
        if (!ENABLED.get()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable async Python bridge methods.");
        }
        PythonCompletableFuture future = new PythonCompletableFuture();
        if (value == null || value.isNull()) {
            future.complete(null);
            return future;
        }
        Context context = value.getContext();
        ContextHolder.enterExecution(context);
        future.whenComplete((ignored, ignoredThrowable) -> ContextHolder.exitExecution(context));
        Optional<PythonEventLoop> currentEventLoop = currentEventLoop();
        PythonEventLoop eventLoop = currentEventLoop.orElse(null);
        Runnable scheduler = () -> schedule(context, value, future, eventLoop);
        if (currentEventLoop.isPresent()) {
            PythonEventLoop current = currentEventLoop.get();
            if (current.inEventLoop()) {
                scheduler.run();
            } else {
                try {
                    current.execute(scheduler);
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        } else {
            scheduler.run();
        }
        return future;
    }

    /**
     * Wrap a Java {@link CompletionStage} as an asyncio future for Python {@code await}.
     *
     * @param context The Python context.
     * @param stage The Java completion stage.
     * @return An asyncio future.
     */
    public static Value toAwaitable(Context context, CompletionStage<?> stage) {
        if (!ENABLED.get()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable async Python bridge methods.");
        }
        Value future;
        Optional<PythonEventLoop> currentEventLoop = currentEventLoop();
        PythonEventLoop eventLoop = currentEventLoop.orElse(null);
        synchronized (context) {
            scheduler(context);
            future = awaitableFactory(context).execute(eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER, stage.toCompletableFuture());
        }
        stage.whenComplete((result, throwable) -> {
            Runnable completion = () -> completeAwaitable(context, future, result, throwable);
            if (currentEventLoop.isPresent()) {
                try {
                    currentEventLoop.get().execute(completion);
                } catch (Throwable e) {
                    completeAwaitable(context, future, null, e);
                }
            } else {
                completion.run();
            }
        });
        return future;
    }

    /**
     * Set whether Python asyncio bridge execution is enabled.
     *
     * @param enabled Whether async bridge execution is enabled.
     */
    public static void setEnabled(boolean enabled) {
        ENABLED.set(enabled);
    }

    static void setEventLoopProviders(Collection<PythonEventLoopProvider> providers) {
        eventLoopProviders = List.copyOf(providers);
    }

    static void setExecutorService(@Nullable ExecutorService executorService) {
        PythonAsyncioRuntime.executorService = executorService;
    }

    static void setExecutorServiceProvider(@Nullable BeanProvider<ExecutorService> executorServiceProvider) {
        PythonAsyncioRuntime.executorServiceProvider = executorServiceProvider;
    }

    private static Optional<PythonEventLoop> currentEventLoop() {
        for (PythonEventLoopProvider provider : eventLoopProviders) {
            Optional<PythonEventLoop> eventLoop = provider.current();
            if (eventLoop.isPresent()) {
                return eventLoop;
            }
        }
        return Optional.empty();
    }

    static Optional<PythonEventLoop> currentEventLoopForContext() {
        return currentEventLoop();
    }

    private static void schedule(Context context, Value value, PythonCompletableFuture future, @Nullable PythonEventLoop eventLoop) {
        try {
            synchronized (context) {
                Value scheduler = scheduler(context);
                scheduler.executeVoid(value, future, EXCEPTION_COMPLETER, eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER);
            }
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    private static Value scheduler(Context context) {
        return asyncioModule(context).getMember(SCHEDULER_NAME);
    }

    private static Value awaitableFactory(Context context) {
        return asyncioModule(context).getMember(AWAITABLE_FACTORY_NAME);
    }

    private static Value awaitableCompleter(Context context) {
        return asyncioModule(context).getMember(AWAITABLE_COMPLETER_NAME);
    }

    private static Value asyncioModule(Context context) {
        Value bindings = context.getBindings(GraalPyRuntimeUtil.PYTHON);
        if (!bindings.hasMember(ASYNCIO_MODULE_BINDING)) {
            importAsyncioModule(context, bindings);
        }
        return bindings.getMember(ASYNCIO_MODULE_BINDING);
    }

    private static void importAsyncioModule(Context context, Value bindings) {
        try {
            context.eval(
                GraalPyRuntimeUtil.PYTHON,
                "import importlib as __micronaut_importlib\n"
                    + ASYNCIO_MODULE_BINDING
                    + " = __micronaut_importlib.import_module('"
                    + ASYNCIO_MODULE_NAME
                    + "')"
            );
        } catch (PolyglotException e) {
            String message = e.getMessage();
            if (message == null || !message.contains("ModuleNotFoundError")) {
                throw e;
            }
            loadAsyncioModuleSource(context, bindings);
        }
    }

    private static void loadAsyncioModuleSource(Context context, Value bindings) {
        try (InputStream inputStream = PythonAsyncioRuntime.class.getClassLoader().getResourceAsStream(ASYNCIO_MODULE_SOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing Micronaut asyncio Python runtime resource: " + ASYNCIO_MODULE_SOURCE);
            }
            bindings.putMember("__micronaut_asyncio_source", new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
            context.eval(
                GraalPyRuntimeUtil.PYTHON,
                "import types as __micronaut_types\n"
                    + "import sys as __micronaut_sys\n"
                    + ASYNCIO_MODULE_BINDING
                    + " = __micronaut_types.ModuleType('"
                    + ASYNCIO_MODULE_NAME
                    + "')\n"
                    + "__micronaut_sys.modules['"
                    + ASYNCIO_MODULE_NAME
                    + "'] = "
                    + ASYNCIO_MODULE_BINDING
                    + "\n"
                    + "exec(__micronaut_asyncio_source, "
                    + ASYNCIO_MODULE_BINDING
                    + ".__dict__)\n"
                    + "del __micronaut_asyncio_source"
            );
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load Micronaut asyncio Python runtime resource: " + ASYNCIO_MODULE_SOURCE, e);
        }
    }

    private static void completeAwaitable(Context context, Value future, @Nullable Object result, @Nullable Throwable throwable) {
        synchronized (context) {
            awaitableCompleter(context).executeVoid(future, result, throwable);
        }
    }

    /**
     * Completes futures with Java exceptions from Python exception data.
     */
    public static final class ExceptionCompleter {
        private ExceptionCompleter() {
        }

        /**
         * Complete a future exceptionally.
         *
         * @param future The future.
         * @param exceptionType The Python exception type.
         * @param message The Python exception message.
         */
        public void completeExceptionally(CompletableFuture<?> future, String exceptionType, String message) {
            future.completeExceptionally(new RuntimeException(exceptionType + ": " + message));
        }
    }

    /**
     * Runs {@code asyncio.run_in_executor(None, ...)} callbacks on Micronaut's blocking executor.
     */
    public static final class ExecutorAdapter {
        private ExecutorAdapter() {
        }

        /**
         * Run a Python callback on the configured blocking executor and complete a loop future on the event loop.
         *
         * @param future The Python asyncio future.
         * @param callback The Python callback.
         * @param eventLoop The current event loop.
         */
        public void run(Value future, Value callback, PythonEventLoop eventLoop) {
            Context context = callback.getContext();
            ExecutorService executor = blockingExecutor();
            if (executor == null) {
                completeAwaitable(context, future, null, new IllegalStateException("No Micronaut blocking executor is available for asyncio.run_in_executor"));
                return;
            }
            try {
                Future<?> ignored = executor.submit(() -> {
                    @Nullable Object result = null;
                    @Nullable Throwable failure = null;
                    try {
                        synchronized (context) {
                            result = executorResult(callback.execute());
                        }
                    } catch (Throwable e) {
                        failure = e;
                    }
                    @Nullable Object completedResult = result;
                    @Nullable Throwable completedFailure = failure;
                    try {
                        eventLoop.execute(() -> completeAwaitable(context, future, completedResult, completedFailure));
                    } catch (Throwable e) {
                        completeAwaitable(context, future, null, e);
                    }
                });
            } catch (Throwable e) {
                completeAwaitable(context, future, null, e);
            }
        }

        private static @Nullable Object executorResult(@Nullable Value value) {
            if (value == null || value.isNull()) {
                return null;
            }
            if (value.isString()) {
                return value.asString();
            }
            if (value.isBoolean()) {
                return value.asBoolean();
            }
            if (value.isNumber()) {
                return value.as(Object.class);
            }
            if (value.isHostObject()) {
                return value.asHostObject();
            }
            return value;
        }

        private static @Nullable ExecutorService blockingExecutor() {
            ExecutorService executor = executorService;
            if (executor != null) {
                return executor;
            }
            BeanProvider<ExecutorService> provider = executorServiceProvider;
            if (provider != null && provider.isResolvable()) {
                return provider.get();
            }
            return null;
        }
    }

    /**
     * CompletableFuture variant that can propagate Java cancellation to a Python task.
     */
    public static final class PythonCompletableFuture extends CompletableFuture<Object> {
        private volatile @Nullable Runnable cancelCallback;

        /**
         * Set a callback invoked when this future is cancelled.
         *
         * @param cancelCallback The cancellation callback.
         */
        public void setCancelCallback(Runnable cancelCallback) {
            this.cancelCallback = cancelCallback;
            if (isCancelled()) {
                cancelCallback.run();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                Runnable callback = cancelCallback;
                if (callback != null) {
                    callback.run();
                }
            }
            return cancelled;
        }
    }
}
