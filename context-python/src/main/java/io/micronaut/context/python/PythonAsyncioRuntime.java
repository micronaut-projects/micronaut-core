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
import io.micronaut.context.BeanProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.propagation.PropagatedContext;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.scheduler.NonBlocking;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Runtime helpers for Python coroutine bridge methods.
 */
@Internal
@Experimental
public final class PythonAsyncioRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(PythonAsyncioRuntime.class);
    private static final String SCHEDULER_NAME = "__micronaut_asyncio_to_completion_stage";
    private static final String AWAITABLE_FACTORY_NAME = "__micronaut_completion_stage_awaitable";
    private static final String AWAITABLE_COMPLETER_NAME = "__micronaut_complete_completion_stage_awaitable";
    private static final String LOOP_INSTALLER_NAME = "__micronaut_install_asyncio_event_loop";
    private static final String ITERATOR_PUBLISHER_NAME = "__micronaut_async_iterator_publisher";
    private static final String PUBLISHER_AWAITABLE_NAME = "__micronaut_publisher_awaitable";
    private static final String REACTIVE_CONTEXT_NAME = "__micronaut_current_reactive_context";
    private static final String JAVA_STAGE_MEMBER = "_micronaut_java_stage";
    private static final AtomicReference<RuntimeState> STATE = new AtomicReference<>(new RuntimeState(true, List.of(), null, null, 0, ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet()));
    private static final ExecutorAdapter EXECUTOR_ADAPTER = new ExecutorAdapter();
    private static final String ASYNCIO_MODULE_NAME = "micronaut_asyncio";
    private static final String ASYNCIO_MODULE_BINDING = "__micronaut_asyncio_module";
    private static final String ASYNCIO_MODULE_SOURCE = "META-INF/GRAALPY-VFS/micronaut-application/src/micronaut_asyncio.py";
    private static final ExceptionCompleter EXCEPTION_COMPLETER = new ExceptionCompleter();
    private static final AtomicReference<@Nullable String> ASYNCIO_FALLBACK_SOURCE = new AtomicReference<>();
    private static final AtomicBoolean OFFLOAD_WARNED = new AtomicBoolean();
    private static final Source IMPORT_ASYNCIO_MODULE_SOURCE = Source.newBuilder(
        PythonContextRuntime.PYTHON,
        "import importlib as __micronaut_importlib\n"
            + ASYNCIO_MODULE_BINDING
            + " = __micronaut_importlib.import_module('"
            + ASYNCIO_MODULE_NAME
            + "')",
        "micronaut-import-asyncio-runtime.py"
    ).cached(true).buildLiteral();

    private PythonAsyncioRuntime() {
    }

    /**
     * Convert a Python coroutine or awaitable value into a Java {@link CompletionStage}.
     *
     * @param value The Python result value.
     * @return A stage that completes when the Python awaitable completes.
     */
    @SuppressWarnings("rawtypes")
    @UsedByGeneratedCode
    public static CompletionStage toCompletionStage(Value value) {
        return toCompletionStage(value, null);
    }

    /**
     * Convert a Python coroutine into a publisher that starts the coroutine when it is first
     * subscribed, in the reactive context of that subscriber: the publishers the coroutine awaits
     * are subscribed with the Reactor context (a reactive transaction status, for instance) and
     * the propagated context of the subscriber. A {@link CompletionStage} is eager; a bridged
     * {@code async def} declared to return a publisher uses this deferred form instead.
     * <p>
     * Consequences of the deferred form: the coroutine never runs when nobody subscribes (Python
     * then warns that the coroutine was never awaited), the event loop is the one of the
     * subscribing thread, so a {@code subscribeOn} decides which loop runs the coroutine, and the
     * result of the first subscription is shared with later subscribers, a cancelled subscription
     * leaving the coroutine running for them.
     *
     * @param value The Python coroutine or awaitable value.
     * @return A publisher of the coroutine's result, empty when the coroutine returns {@code None}.
     */
    @UsedByGeneratedCode
    @SuppressWarnings("unchecked")
    public static Publisher<Object> toPublisher(Value value) {
        if (value.isHostObject() && value.asHostObject() instanceof Publisher<?> publisher) {
            return (Publisher<Object>) publisher;
        }
        return PythonPublishers.deferred(reactiveContext -> {
            return reactiveContext.propagatedContext().propagate(
                () -> toCompletionStage(value, reactiveContext).toCompletableFuture()
            );
        });
    }

    /**
     * Convert a Python coroutine or awaitable value into a Java {@link CompletionStage}, running
     * the coroutine within a reactive context.
     *
     * @param value The Python result value.
     * @param reactiveContext The reactive context the publishers awaited by the coroutine are
     *                        subscribed in, or {@code null} for the propagated context of the caller
     * @return A stage that completes when the Python awaitable completes.
     */
    @SuppressWarnings("FutureReturnValueIgnored")
    static CompletionStage<Object> toCompletionStage(Value value, @Nullable PythonReactiveContext reactiveContext) {
        RuntimeState runtimeState = state();
        if (!runtimeState.enabled()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable async Python bridge methods.");
        }
        if (value == null || value.isNull()) {
            PythonCompletableFuture future = new PythonCompletableFuture();
            future.complete(null);
            return future;
        }
        CompletionStage<Object> javaStage = javaStage(value);
        if (javaStage != null) {
            return javaStage;
        }
        Context context = value.getContext();
        PythonCompletableFuture future = new PythonCompletableFuture();
        PythonContextRegistry.enterExecution(context);
        future.whenComplete((ignored, ignoredThrowable) -> PythonContextRegistry.exitExecution(context));
        PythonEventLoop eventLoop = currentEventLoop(runtimeState);
        PythonReactiveContext taskContext = reactiveContext != null ? reactiveContext : callerContext();
        Runnable scheduler = () -> schedule(context, value, future, eventLoop, taskContext);
        if (eventLoop != null) {
            if (eventLoop.inEventLoop()) {
                scheduler.run();
            } else {
                try {
                    eventLoop.execute(PropagatedContext.wrapCurrent(scheduler));
                } catch (Throwable e) {
                    future.completeExceptionally(e);
                }
            }
        } else if (NonBlockingThreads.isNonBlockingThread() && offload(context, future, scheduler)) {
            return future;
        } else {
            scheduler.run();
        }
        return future;
    }

    /**
     * Run the coroutine of a non-blocking thread that has no Micronaut event loop (a Netty event loop
     * without {@code micronaut-context-python-netty}, or beyond {@code max-event-loop-contexts}) on the
     * blocking executor. Without an event loop
     * the coroutine is driven to completion by a loop on the calling thread, which would block that
     * Netty event loop, and with it every awaited client call needing a connection on it, until the
     * call times out.
     *
     * @param context The context of the coroutine
     * @param future The future of the coroutine
     * @param scheduler The scheduling of the coroutine
     * @return Whether the blocking executor took the coroutine
     */
    private static boolean offload(Context context, PythonCompletableFuture future, Runnable scheduler) {
        ExecutorService executor = ExecutorAdapter.blockingExecutor();
        if (executor == null) {
            return false;
        }
        if (OFFLOAD_WARNED.compareAndSet(false, true)) {
            LOG.warn("A Python coroutine was started on the non-blocking thread [{}], which has no Micronaut asyncio event loop: "
                + "it runs on the blocking executor instead. Coroutines run on the Netty event loop when "
                + "io.micronaut:micronaut-context-python-netty is on the runtime classpath and the event loop is within "
                + "micronaut.python.pool.max-event-loop-contexts.", Thread.currentThread().getName());
        }
        try {
            // the worker runs guest code of its own: host calls of the coroutine resolve this context
            Runnable tracked = () -> {
                try {
                    PythonContextRegistry.withTrackedExecutionFrame(context, () -> {
                        scheduler.run();
                        return null;
                    });
                } catch (Throwable e) { // NOSONAR nothing else observes this worker: any failure must complete the future
                    future.completeExceptionally(e);
                }
            };
            executor.execute(PropagatedContext.wrapCurrent(tracked));
            return true;
        } catch (RuntimeException e) {
            LOG.debug("The blocking executor refused a Python coroutine; it runs on the calling thread", e);
            return false;
        }
    }

    /**
     * Expose the async iterator (an async generator object, typically) a bridge method returned as a
     * Reactive Streams {@link Publisher}. The iterator is advanced on the current Micronaut event
     * loop as the subscriber requests elements; without one, and without a running Python loop, the
     * call fails rather than driving the generator synchronously.
     *
     * @param value The Python async iterator
     * @return A cold publisher that can be subscribed to once
     */
    @SuppressWarnings("rawtypes")
    @UsedByGeneratedCode
    public static Publisher generatorToPublisher(@Nullable Value value) {
        RuntimeState runtimeState = state();
        if (!runtimeState.enabled()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable async generator bridge methods.");
        }
        if (value == null || value.isNull()) {
            return Publishers.empty();
        }
        Context context = value.getContext();
        PythonEventLoop eventLoop = currentEventLoop(runtimeState);
        Value publisher = asyncioHelper(context, ITERATOR_PUBLISHER_NAME).execute(value, eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER);
        return publisher.asHostObject();
    }

    /**
     * The Python view of a publisher returned by a Java member: awaiting it requests one item and
     * cancels, as {@link #toAwaitable} does for a stage, while {@code as_async_iterable} unwraps the
     * publisher to consume every item. Nothing is subscribed until one of the two happens.
     *
     * @param context The Python context
     * @param publisher The publisher, or a value convertible to one
     * @param reactiveContext The reactive context of the coroutine that called the member, or {@code null}
     * @return The Python awaitable
     */
    static Value publisherAwaitable(Context context, Object publisher, @Nullable PythonReactiveContext reactiveContext) {
        return asyncioHelper(context, PUBLISHER_AWAITABLE_NAME).execute(publisher, reactiveContext);
    }

    /**
     * Python entry point of the publisher awaitable: the asyncio future of the publisher's first item.
     *
     * @param publisher The publisher
     * @param reactiveContext The reactive context to subscribe within, or {@code null} for none
     * @return The future
     */
    @Internal
    public static Value awaitPublisher(Value publisher, @Nullable PythonReactiveContext reactiveContext) {
        Object source = publisher.isHostObject() ? publisher.asHostObject() : publisher;
        CompletionStage<?> stage = PythonCoercion.AsyncMemberAdapter.publisherStage(source, reactiveContext);
        if (stage == null) {
            throw new IllegalArgumentException("Not a publisher: " + publisher);
        }
        return toAwaitable(Context.getCurrent(), stage);
    }

    /**
     * Python entry point of {@code await} on a Java object: the asyncio future of a
     * {@link CompletionStage}, or of the first item of a publisher (subscribed within the reactive
     * context of the awaiting coroutine), so a {@code CompletableFuture} or {@code Mono} a Java call
     * returned is awaitable like the value of an injected client.
     *
     * @param value The Java object
     * @return The future
     */
    @Internal
    public static Value awaitJava(Value value) {
        Context context = Context.getCurrent();
        Object hostObject = value.isHostObject() ? value.asHostObject() : null;
        if (hostObject instanceof CompletionStage<?> stage) {
            return toAwaitable(context, stage);
        }
        Value reactiveContext = asyncioHelper(context, REACTIVE_CONTEXT_NAME).execute();
        PythonReactiveContext subscriberContext = reactiveContext.isHostObject() && reactiveContext.asHostObject() instanceof PythonReactiveContext current
            ? current
            : null;
        return awaitPublisher(value, subscriberContext);
    }

    /**
     * The Python asyncio loop of the current Micronaut event loop, installed on demand. Called by the
     * {@code micronaut_asyncio} module when a stream is created outside a running coroutine.
     *
     * @return The loop, or {@code null} when the calling thread has no admitted event loop
     */
    @Internal
    public static @Nullable Value currentAsyncioLoop() {
        RuntimeState runtimeState = state();
        if (!runtimeState.enabled()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable Python-native streaming.");
        }
        PythonEventLoop eventLoop = currentEventLoop(runtimeState);
        if (eventLoop == null) {
            return null;
        }
        Context context = Context.getCurrent();
        return asyncioHelper(context, LOOP_INSTALLER_NAME).execute(eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER);
    }

    /**
     * The Java stage an awaitable carries, or {@code null} for a Python awaitable. An intercepted
     * {@code async def} of a proxied bean hands its {@link CompletionStage} to Python callers as an
     * awaitable that remembers the stage under {@code _micronaut_java_stage}: a Java caller of the
     * bridge gets that stage back as it is, without a loop driving it on the calling thread.
     */
    @SuppressWarnings("unchecked")
    private static @Nullable CompletionStage<Object> javaStage(Value value) {
        if (!value.hasMember(JAVA_STAGE_MEMBER)) {
            return null;
        }
        Value member = value.getMember(JAVA_STAGE_MEMBER);
        if (member != null && member.isHostObject() && member.asHostObject() instanceof CompletionStage<?> stage) {
            return (CompletionStage<Object>) stage;
        }
        return null;
    }

    /**
     * Wrap a Java {@link CompletionStage} as an asyncio future for Python {@code await}.
     *
     * @param context The Python context.
     * @param stage The Java completion stage.
     * @return An asyncio future.
     */
    public static Value toAwaitable(Context context, CompletionStage<?> stage) {
        RuntimeState runtimeState = state();
        if (!runtimeState.enabled()) {
            throw new IllegalStateException("Python asyncio support is disabled. Set micronaut.python.asyncio.enabled=true to enable async Python bridge methods.");
        }
        Value future;
        PythonEventLoop eventLoop = currentEventLoop(runtimeState);
        // guest calls run without the context monitor: GraalPy's GIL serialises them, and a monitor
        // held while waiting for the GIL deadlocks against a Python thread re-entering the runtime
        scheduler(context);
        future = awaitableFactory(context).execute(eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER, stage.toCompletableFuture());
        stage.whenComplete((result, throwable) -> {
            // no context is captured from the completing thread (a Reactor scheduler, a client loop): the
            // task the completion wakes up restores its own propagated context from its contextvars
            Runnable completion = () -> completeAwaitable(context, future, result, throwable);
            if (eventLoop != null) {
                try {
                    eventLoop.execute(completion);
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
        updateState(current -> new RuntimeState(enabled, current.eventLoopProviders(), current.executorService(), current.executorServiceProvider(), current.maxEventLoops(), current.admittedLoops(), current.refusedLoops()));
    }

    /**
     * Replace the event loop providers used to detect the active Python event loop.
     * <p>
     * This hook is package-private so Micronaut infrastructure can refresh runtime
     * state when the application context starts or changes without exposing the
     * provider list as a public API.
     *
     * @param providers The currently available event loop providers.
     */
    static void setEventLoopProviders(Collection<PythonEventLoopProvider> providers) {
        // new providers, new admission: the loops of the previous configuration, and everything their
        // queued callbacks reference, are not kept alive by the admission sets
        updateState(current -> new RuntimeState(current.enabled(), List.copyOf(providers), current.executorService(), current.executorServiceProvider(), current.maxEventLoops(), ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet()));
    }

    /**
     * Set the blocking executor used by Python {@code asyncio.run_in_executor(None, ...)}.
     * <p>
     * A direct executor takes precedence over the provider variant and is primarily
     * used when infrastructure has already resolved the Micronaut blocking executor.
     *
     * @param executorService The resolved blocking executor, or {@code null} to defer to the provider.
     */
    static void setExecutorService(@Nullable ExecutorService executorService) {
        updateState(current -> new RuntimeState(current.enabled(), current.eventLoopProviders(), executorService, current.executorServiceProvider(), current.maxEventLoops(), current.admittedLoops(), current.refusedLoops()));
    }

    /**
     * Set the lazy provider for the blocking executor used by Python {@code run_in_executor}.
     * <p>
     * The provider is consulted only when no direct executor has been configured,
     * which keeps executor resolution lazy during application context initialization.
     *
     * @param executorServiceProvider The blocking executor provider, or {@code null} when unavailable.
     */
    static void setExecutorServiceProvider(@Nullable BeanProvider<ExecutorService> executorServiceProvider) {
        updateState(current -> new RuntimeState(current.enabled(), current.eventLoopProviders(), current.executorService(), executorServiceProvider, current.maxEventLoops(), current.admittedLoops(), current.refusedLoops()));
    }

    /**
     * Cap the number of event loops that get a dedicated asyncio context. Loops beyond the cap are
     * reported as absent, so their requests run Python through the shared pool. Changing the cap
     * forgets which loops were admitted.
     *
     * @param maxEventLoops The cap, or {@code 0} for no cap
     */
    static void setMaxEventLoops(int maxEventLoops) {
        updateState(current -> new RuntimeState(current.enabled(), current.eventLoopProviders(), current.executorService(), current.executorServiceProvider(),
            maxEventLoops, ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet()));
    }

    private static RuntimeState state() {
        return Objects.requireNonNull(STATE.get(), "state");
    }

    private static synchronized void updateState(UnaryOperator<RuntimeState> updater) {
        // read-modify-write under the class monitor so concurrent configuration calls keep each other's fields
        STATE.set(updater.apply(state()));
    }

    private static @Nullable PythonEventLoop currentEventLoop(RuntimeState runtimeState) {
        for (PythonEventLoopProvider provider : runtimeState.eventLoopProviders()) {
            PythonEventLoop eventLoop = provider.currentLoop();
            if (eventLoop != null) {
                return admit(runtimeState, eventLoop) ? eventLoop : null;
            }
        }
        return null;
    }

    private static boolean admit(RuntimeState runtimeState, PythonEventLoop eventLoop) {
        int maxEventLoops = runtimeState.maxEventLoops();
        if (maxEventLoops <= 0) {
            return true;
        }
        Set<PythonEventLoop> admitted = runtimeState.admittedLoops();
        if (admitted.contains(eventLoop)) {
            return true;
        }
        synchronized (admitted) {
            if (admitted.contains(eventLoop)) {
                return true;
            }
            if (admitted.size() < maxEventLoops) {
                admitted.add(eventLoop);
                return true;
            }
        }
        if (runtimeState.refusedLoops().add(eventLoop)) {
            LOG.warn("Event loop {} gets no dedicated Python context: the {} allowed by micronaut.python.pool.max-event-loop-contexts are in use; its requests run Python through the shared pool", eventLoop, maxEventLoops);
        }
        return false;
    }

    /**
     * Resolve the event loop currently associated with the calling execution context.
     * <p>
     * Package collaborators use this to route async Python invocations without
     * duplicating provider iteration or depending on the runtime state's shape.
     *
     * @return The current event loop, or {@code null} when execution is not on a known loop.
     */
    static @Nullable PythonEventLoop currentEventLoopForContext() {
        return currentEventLoop(state());
    }

    /**
     * The context of an eager coroutine: the propagated context of the caller, kept by the task so
     * its steps run in it whichever thread resumes them, or none when the caller has no context.
     */
    private static @Nullable PythonReactiveContext callerContext() {
        return PropagatedContext.find()
            .map(propagatedContext -> new PythonReactiveContext(null, propagatedContext))
            .orElse(null);
    }

    private static void schedule(Context context, Value value, PythonCompletableFuture future, @Nullable PythonEventLoop eventLoop, @Nullable PythonReactiveContext reactiveContext) {
        try {
            scheduler(context).executeVoid(value, future, EXCEPTION_COMPLETER, eventLoop, TimeUnit.NANOSECONDS, EXECUTOR_ADAPTER, reactiveContext);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Log an event-loop callback failure reported by the Python loop's default exception handler.
     *
     * @param text The formatted report, traceback included
     */
    @Internal
    public static void reportLoopError(String text) {
        LOG.error("{}", text);
    }

    /**
     * Resolve a Java entry point of the {@code micronaut_asyncio} module, cached per context.
     *
     * @param context The context
     * @param name The module-level function name
     * @return The function
     */
    @Internal
    public static Value asyncioHelper(Context context, String name) {
        Map<String, Value> helpers = PythonContextRegistry.state(context).helpers;
        String key = ASYNCIO_MODULE_NAME + "." + name;
        Value helper = helpers.get(key);
        if (helper != null) {
            return helper;
        }
        helper = asyncioModule(context).getMember(name);
        if (helper == null || helper.isNull()) {
            throw new IllegalStateException("The " + ASYNCIO_MODULE_NAME + " module does not define [" + name + "]");
        }
        Value existing = helpers.putIfAbsent(key, helper);
        return existing == null ? helper : existing;
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
        Value bindings = context.getBindings(PythonContextRuntime.PYTHON);
        if (!bindings.hasMember(ASYNCIO_MODULE_BINDING)) {
            importAsyncioModule(context);
        }
        return bindings.getMember(ASYNCIO_MODULE_BINDING);
    }

    private static void importAsyncioModule(Context context) {
        try {
            context.eval(IMPORT_ASYNCIO_MODULE_SOURCE);
        } catch (PolyglotException e) {
            if (!PythonContextRuntime.isModuleNotFound(e)) {
                throw e;
            }
            loadAsyncioModuleSource(context);
        }
    }

    private static void loadAsyncioModuleSource(Context context) {
        // the virtual file system of the context does not carry the module: serve it from the classpath
        // resource and import it again, so concurrent first imports wait for the complete module
        PythonContextRuntime.installRuntimeModuleFinder(context, ASYNCIO_MODULE_NAME, ASYNCIO_MODULE_SOURCE, ASYNCIO_FALLBACK_SOURCE);
        context.eval(IMPORT_ASYNCIO_MODULE_SOURCE);
    }

    private static void completeAwaitable(Context context, Value future, @Nullable Object result, @Nullable Throwable throwable) {
        // a Java stage may complete after the coroutine that awaited it returned: the completion is
        // guest work of its own, tracked by a frame and skipped once the context is closing
        if (!PythonContextRegistry.tryWithExecutionFrame(context, () -> awaitableCompleter(context).executeVoid(future, result, throwable == null ? null : awaitedFailure(context, throwable)))) {
            LOG.debug("Skipping the completion of an awaitable whose Python context is closing");
        }
    }

    /**
     * The failure of a Java stage as the Python code awaiting it sees it. The stage of an intercepted
     * {@code async def} fails with the Python exception of the coroutine, wrapped by the interceptor
     * chain in a {@link CompletionException} around the {@link PolyglotException} (or the generated
     * Java exception) that carried it through Java: the awaiting code gets the Python exception object
     * back, so {@code except MyError} matches. A Java failure is unwrapped from the completion
     * wrappers, so the awaiting code sees the exception the Java code threw.
     *
     * @param context The context of the awaiting code
     * @param throwable The failure of the stage
     * @return The Python exception of this context, or the Java throwable
     */
    static Object awaitedFailure(Context context, Throwable throwable) {
        Throwable failure = throwable;
        while (true) {
            if ((failure instanceof CompletionException || failure instanceof ExecutionException) && failure.getCause() != null) {
                failure = failure.getCause();
            } else if (failure instanceof PolyglotException polyglotException && polyglotException.isHostException()) {
                failure = polyglotException.asHostException();
            } else {
                break;
            }
        }
        Value guest = null;
        if (failure instanceof PolyglotException polyglotException && polyglotException.isGuestException()) {
            guest = polyglotException.getGuestObject();
        } else if (failure instanceof ValueCoercible coercible) {
            // the generated Java exception of a Python exception class extending a Java one
            guest = coercible.asPolyglotValue();
        }
        // a Python exception of another context cannot be raised here: the awaiting code gets the Java view
        if (guest != null && guest.isException() && PythonCoercion.isValueInContext(guest, context)) {
            return guest;
        }
        return failure;
    }

    /**
     * Complete a Python callback with a Java stage's outcome on the event loop, inside an execution
     * frame of the callback's context; the callback is skipped once the context is closing. This is
     * how the asyncio module observes Java stages: a Python callable is never registered with a
     * {@link CompletionStage} directly, which would run it on the completing thread outside any frame.
     *
     * @param stage The Java stage
     * @param eventLoop The loop to complete on, or null to complete on the completing thread
     * @param callback A Python callable taking the value and the throwable
     */
    @Internal
    public static void completeOnLoop(CompletionStage<?> stage, @Nullable PythonEventLoop eventLoop, Value callback) {
        Context context = callback.getContext();
        stage.whenComplete((value, throwable) -> {
            Runnable completion = () -> {
                if (!PythonContextRegistry.tryWithExecutionFrame(context, () -> callback.executeVoid(value, throwable))) {
                    LOG.debug("Skipping a stage completion whose Python context is closing");
                }
            };
            if (eventLoop == null) {
                completion.run();
            } else {
                try {
                    eventLoop.execute(completion);
                } catch (RuntimeException e) {
                    LOG.debug("The event loop refused a stage completion", e);
                }
            }
        });
    }

    /**
     * Completes futures with Java exceptions from Python exception data.
     */
    @Experimental
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

        /**
         * Complete a future exceptionally with the Java view of a Python exception object.
         * <p>
         * Java exceptions raised inside the coroutine keep their type, generated Python exception
         * wrappers are instantiated, and other Python exceptions surface as a {@link PolyglotException}.
         *
         * @param future The future.
         * @param exception The Python exception object.
         */
        public void completeExceptionally(CompletableFuture<?> future, Value exception) {
            Throwable throwable;
            try {
                throwable = GraalPyExceptionHandler.toHostThrowable(exception);
            } catch (RuntimeException e) {
                throwable = e;
            }
            future.completeExceptionally(throwable);
        }
    }

    /**
     * Runs {@code asyncio.run_in_executor(None, ...)} callbacks on Micronaut's blocking executor.
     */
    @Experimental
    public static final class ExecutorAdapter {
        private ExecutorAdapter() {
        }

        /**
         * Run a Python callback on the configured blocking executor and complete a loop future on the event loop.
         *
         * @param future The Python asyncio future.
         * @param callback The Python callback.
         * @param eventLoop The current event loop, or {@code null} for a Python loop running on another
         *                  thread, which the completion of the future hands the result to.
         */
        public void run(Value future, Value callback, @Nullable PythonEventLoop eventLoop) {
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
                        // the worker runs guest code of its own: tracked, and refused once the context is closing
                        result = PythonContextRegistry.withTrackedExecutionFrame(context, () -> executorResult(callback.execute()));
                    } catch (Throwable e) {
                        failure = e;
                    }
                    @Nullable Object completedResult = result;
                    @Nullable Throwable completedFailure = failure;
                    if (eventLoop == null) {
                        completeAwaitable(context, future, completedResult, completedFailure);
                        return;
                    }
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

        /**
         * Whether a Micronaut blocking executor is available to {@link #run}.
         *
         * @return {@code true} when {@code run_in_executor(None, ...)} can use the blocking executor
         */
        public boolean isAvailable() {
            return blockingExecutor() != null;
        }

        static @Nullable ExecutorService blockingExecutor() {
            RuntimeState runtimeState = state();
            ExecutorService executor = runtimeState.executorService();
            if (executor != null) {
                return executor;
            }
            BeanProvider<ExecutorService> provider = runtimeState.executorServiceProvider();
            if (provider != null && provider.isResolvable()) {
                return provider.get();
            }
            return null;
        }
    }

    /**
     * CompletableFuture variant that can propagate Java cancellation to a Python task.
     */
    @Experimental
    public static final class PythonCompletableFuture extends CompletableFuture<Object> {
        private static final Runnable CANCELLED = new CancelledCallback();
        private final AtomicReference<@Nullable Runnable> cancelCallback = new AtomicReference<>();

        /**
         * Set a callback invoked when this future is cancelled.
         *
         * @param cancelCallback The cancellation callback.
         */
        public void setCancelCallback(Runnable cancelCallback) {
            if (!this.cancelCallback.compareAndSet(null, cancelCallback)) {
                if (this.cancelCallback.get() == CANCELLED) {
                    cancelCallback.run();
                }
                return;
            }
            if (isCancelled() && this.cancelCallback.compareAndSet(cancelCallback, CANCELLED)) {
                cancelCallback.run();
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            if (cancelled) {
                Runnable callback = cancelCallback.getAndSet(CANCELLED);
                if (callback != null && callback != CANCELLED) {
                    callback.run();
                }
            }
            return cancelled;
        }
    }

    private static final class CancelledCallback implements Runnable {
        @Override
        public void run() {
        }
    }

    /**
     * Detects the non-blocking threads (Netty event loops) Micronaut marks with Reactor's
     * {@link NonBlocking}; Reactor is optional, and without it no thread is detected.
     */
    private static final class NonBlockingThreads {
        private static volatile boolean reactorAvailable = true;

        private NonBlockingThreads() {
        }

        static boolean isNonBlockingThread() {
            if (!reactorAvailable) {
                return false;
            }
            try {
                return Thread.currentThread() instanceof NonBlocking || Schedulers.isInNonBlockingThread();
            } catch (LinkageError e) {
                reactorAvailable = false;
                return false;
            }
        }
    }

    private record RuntimeState(boolean enabled,
                                List<PythonEventLoopProvider> eventLoopProviders,
                                @Nullable ExecutorService executorService,
                                @Nullable BeanProvider<ExecutorService> executorServiceProvider,
                                int maxEventLoops,
                                Set<PythonEventLoop> admittedLoops,
                                Set<PythonEventLoop> refusedLoops) {
    }
}
