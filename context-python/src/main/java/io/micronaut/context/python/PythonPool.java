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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.event.BeanDestroyedEvent;
import io.micronaut.context.event.BeanDestroyedEventListener;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.order.Ordered;
import io.micronaut.runtime.exceptions.ApplicationStartupException;
import io.micronaut.runtime.graceful.GracefulShutdownCapable;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.micronaut.context.python.PythonContextRuntime.PYTHON;

/**
 * Provides a pool of GraalPy {@link Context} instances built on a shared {@link Engine}.
 * <p>
 * The first context (primary) is created synchronously and is not part of the pool. It is exposed
 * via {@link PythonContextRuntime#getContext()} and used for non-pooled operations. Remaining pooled
 * contexts are created lazily by generated bridge calls that borrow from the pool.
 */
@Singleton
@io.micronaut.context.annotation.Context
@Internal
final class PythonPool implements PythonContextExecutor, BeanDestroyedEventListener<Context>, GracefulShutdownCapable, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(PythonPool.class);
    private final Engine engine;
    private final HostAccess hostAccess;
    private final ApplicationContext applicationContext;
    private final GraalPyContextConfiguration contextConfiguration;
    private final @Nullable Duration warnThreshold;

    private final Context primaryContext;
    private final PythonApplicationRuntime runtime;

    private @Nullable Thread creatingContext = null;
    private final Queue<Context> pooledQueue = new ArrayDeque<>();
    private final List<Context> pooledContexts = new CopyOnWriteArrayList<>();
    private final Map<PythonEventLoop, Context> eventLoopContexts = new ConcurrentHashMap<>();
    private final Map<Context, Map<String, Value>> cache = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> scriptInjections = new ConcurrentHashMap<>();
    /** Bumped by every injection; a context applies the injections of a script when it is behind. */
    private final AtomicLong injectionVersion = new AtomicLong();
    /** The injection version each context has applied, per script. */
    private final Map<Context, Map<String, Long>> appliedInjections = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<String>> asyncScriptInjections = new ConcurrentHashMap<>();

    private final AtomicInteger size = new AtomicInteger(0);
    private final AtomicLong borrows = new AtomicLong();
    private final AtomicLong waits = new AtomicLong();
    private final AtomicLong totalWaitNanos = new AtomicLong();
    private final AtomicLong maxWaitNanos = new AtomicLong();
    private final AtomicBoolean gracefulShutdownStarted = new AtomicBoolean();
    private final CompletableFuture<Void> gracefulShutdown = new CompletableFuture<>();
    private final int targetSize;
    private volatile boolean closed;

    /**
     * Create the pool coordinator around the primary context and shared engine.
     * <p>
     * The primary context is supplied by the context factory and remains outside the borrow/release
     * queue. Additional contexts created by this class share the engine and host access settings.
     *
     * @param engine The shared GraalPy engine
     * @param hostAccess The host access policy used for newly created contexts
     * @param primaryContext The primary context used by non-pooled runtime calls
     * @param runtime The runtime of the application that registers the pool
     * @param applicationContext The application context used to obtain the class loader
     * @param configuration The Python pool configuration
     */
    @Inject
    PythonPool(@Named(PythonContextRuntime.PYTHON) Engine engine,
               @Named(PythonContextRuntime.PYTHON) HostAccess hostAccess,
               @Named(PythonContextRuntime.PYTHON) Context primaryContext,
               PythonApplicationRuntime runtime,
               ApplicationContext applicationContext,
               GraalPyContextConfiguration contextConfiguration,
               PythonPoolConfiguration configuration) {
        this.engine = engine;
        this.runtime = runtime;
        this.primaryContext = primaryContext;
        this.hostAccess = hostAccess;
        this.applicationContext = applicationContext;
        this.contextConfiguration = contextConfiguration;
        int configuredPoolSize = configuration.size();
        this.warnThreshold = configuration.warnWait();
        this.targetSize = configuration.enabled() ? (configuredPoolSize > 0 ? configuredPoolSize : computeDefaultSize()) : 0;
    }

    private static int computeDefaultSize() {
        int processors = Runtime.getRuntime().availableProcessors();
        return Math.max(1, processors * 2);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Initialize the optional pool around the already-created primary context.
     * <p>
     * When pooling is disabled or context reuse is enabled this method deliberately unregisters
     * the pool from the {@link PythonApplicationRuntime}. Otherwise it exposes the pool immediately and
     * prepares the primary context cache. Pooled contexts are created lazily by {@link #borrow()}.
     */
    @PostConstruct
    void init() {
        // If reuseContext is enabled, skip pool initialization entirely
        if (PythonContextRuntime.isReuseContext()) {
            LOG.debug("Context reuse enabled; skipping Python context pool initialization");
            runtime.pool(null);
            return;
        }
        // Register pool and prepare caches if enabled
        if (targetSize <= 0) {
            LOG.debug("Python context pool disabled via configuration; skipping initialization");
            runtime.pool(null);
            return;
        }
        runtime.pool(this);
        cache.put(primaryContext, new ConcurrentHashMap<>());
    }

    /**
     * Borrow a pooled context, creating one lazily while the pool is below its target size.
     * <p>
     * Borrowed contexts must be returned with {@link #release(Context)} so the bounded pool does
     * not starve subsequent generated bridge calls.
     *
     * @return A pooled context ready for exclusive use by the caller
     */
    Context borrow() {
        return borrow0(true);
    }

    /**
     * Return a borrowed context to the tail of the available queue.
     *
     * @param c The context previously obtained from {@link #borrow()}
     */
    void release(Context c) {
        boolean closeNow;
        synchronized (this) {
            if (closed) {
                // The pool is closing or closed. A context still tracked in pooledContexts is closed by
                // closePool once idle; one that was already removed would otherwise leak.
                closeNow = !pooledContexts.contains(c);
            } else {
                closeNow = false;
                pooledQueue.add(c);
                notifyAll();
            }
        }
        if (closeNow) {
            closeContext(c);
        }
    }

    int pooledContextCount() {
        return size.get();
    }

    int availableContextCount() {
        synchronized (this) {
            return pooledQueue.size();
        }
    }

    @Override
    public <T extends @Nullable Object> T withContext(Function<Context, T> callback) {
        Objects.requireNonNull(callback, "callback");
        if (PythonContextRuntime.isReuseContext() || targetSize <= 0) {
            return PythonContextRuntime.withPrimaryContext(callback);
        }
        PythonEventLoop eventLoop = PythonAsyncioRuntime.currentEventLoopForContext();
        if (eventLoop != null) {
            Context eventLoopContext = getEventLoopContext(eventLoop);
            // the GraalPy GIL serialises guest execution; a Java monitor held across it can deadlock.
            // A tracked frame: a closing event-loop context refuses the call instead of being revived.
            return PythonContextRegistry.withTrackedExecutionFrame(eventLoopContext, () -> callback.apply(eventLoopContext));
        }
        Context borrowed = borrow();
        try {
            return PythonContextRegistry.withExecutionFrame(borrowed, () -> callback.apply(borrowed));
        } finally {
            release(borrowed);
        }
    }

    /**
     * Borrow a context, resolve a cached class value in that context, and release the context after the callback completes.
     *
     * @param classReference The Python class reference
     * @param fn The callback that receives the context-local class value
     * @param <T> The callback result type
     * @return The callback result
     */
    <T> T withClass(PythonContextRuntime.PythonClassReference classReference, java.util.function.Function<Value, T> fn) {
        Context c = borrow();
        try {
            return PythonContextRegistry.withExecutionFrame(c, () -> fn.apply(getOrCreateClass(c, classReference)));
        } finally {
            release(c);
        }
    }

    /**
     * Borrow a context, resolve a cached script/module value in that context, and release the
     * context after the callback completes.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @param fn The callback that receives the context-local script value
     * @param <T> The callback result type
     * @return The callback result
     */
    <T> T withScript(String packageName, String scriptName, java.util.function.Function<Value, T> fn) {
        Context c = borrow();
        try {
            return PythonContextRegistry.withExecutionFrame(c, () -> fn.apply(getOrCreateScript(c, packageName, scriptName)));
        } finally {
            release(c);
        }
    }

    /**
     * Borrow a context, resolve a cached class value in it and keep the context leased until the
     * stage the callback returns completes.
     * <p>
     * A coroutine returned by a bridge call runs on the context that created it, so that context is
     * not idle, and must not be lent to another caller, until the coroutine is done.
     *
     * @param classReference The Python class reference
     * @param fn The callback that receives the context-local class value and returns the stage
     * @return The stage
     */
    CompletionStage<?> withClassUntilComplete(PythonContextRuntime.PythonClassReference classReference, Function<Value, CompletionStage<?>> fn) {
        return leaseUntilComplete(c -> fn.apply(getOrCreateClass(c, classReference)));
    }

    /**
     * Borrow a context, resolve a cached script value in it and keep the context leased until the
     * stage the callback returns completes; see {@link #withClassUntilComplete}.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @param fn The callback that receives the context-local script value and returns the stage
     * @return The stage
     */
    CompletionStage<?> withScriptUntilComplete(String packageName, String scriptName, Function<Value, CompletionStage<?>> fn) {
        return leaseUntilComplete(c -> fn.apply(getOrCreateScript(c, packageName, scriptName)));
    }

    private CompletionStage<?> leaseUntilComplete(Function<Context, CompletionStage<?>> fn) {
        Context c = borrow();
        CompletionStage<?> stage = null;
        try {
            stage = Objects.requireNonNull(PythonContextRegistry.withExecutionFrame(c, () -> fn.apply(c)), "stage");
        } finally {
            if (stage == null) {
                // the coroutine was never produced: the lease ends with the failure
                release(c);
            }
        }
        AtomicBoolean released = new AtomicBoolean();
        stage.whenComplete((ignored, ignoredFailure) -> {
            if (released.compareAndSet(false, true)) {
                release(c);
            }
        });
        return stage;
    }

    /**
     * Borrow a context, resolve a cached evaluated value in that context, and release the context
     * after the callback completes.
     *
     * @param expression The Python expression or statements to evaluate
     * @param fn The callback that receives the context-local value
     * @param <T> The callback result type
     * @return The callback result
     */
    <T> T withValue(String expression, java.util.function.Function<Value, T> fn) {
        Context c = borrow();
        try {
            return PythonContextRegistry.withExecutionFrame(c, () -> fn.apply(getOrCreateValue(c, expression)));
        } finally {
            release(c);
        }
    }

    /**
     * Resolve a class instance for a caller that owns no context.
     * <p>
     * The value comes from the primary context, which the pool never hands out for exclusive use: a
     * value taken from an idle pooled context would be shared with whichever thread borrows that
     * context next. Callers that need the value in a pooled context re-coerce it there through
     * {@link PooledValueCoercible#asPolyglotValue(Context)}.
     *
     * @param classReference The class reference
     * @return A class value of the primary context
     */
    Value getAnyClass(PythonContextRuntime.PythonClassReference classReference) {
        // the primary context is shared: the load runs inside a frame of it
        return PythonContextRegistry.withExecutionFrame(primaryContext, () -> getOrCreateClass(primaryContext, classReference));
    }

    /**
     * Resolve a cached class instance in a caller-selected context.
     *
     * @param context The context that should own the value
     * @param classReference The Python class reference
     * @return The context-local class value
     */
    Value getClass(Context context, PythonContextRuntime.PythonClassReference classReference) {
        return getOrCreateClass(context, classReference);
    }

    /**
     * Resolve a script/module for a caller that owns no context; see {@link #getAnyClass}.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @return A script value of the primary context
     */
    Value getAnyScript(String packageName, String scriptName) {
        // the primary context is shared: the load and any pending injection run inside a frame of it
        return PythonContextRegistry.withExecutionFrame(primaryContext, () -> getOrCreateScript(primaryContext, packageName, scriptName));
    }

    /**
     * Resolve a cached script/module value in a caller-selected context.
     *
     * @param context The context that should own the value
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @return The context-local script value
     */
    Value getScript(Context context, String packageName, String scriptName) {
        return getOrCreateScript(context, packageName, scriptName);
    }

    /**
     * Resolve a cached evaluated value in a caller-selected context.
     *
     * @param context The context that should own the value
     * @param expression The Python expression or statements to evaluate
     * @return The context-local value
     */
    Value getValue(Context context, String expression) {
        return getOrCreateValue(context, expression);
    }

    /**
     * Resolve a class instance in the dedicated context associated with an asyncio event loop.
     *
     * @param eventLoop The Python event loop that owns the context
     * @param classReference The Python class reference
     * @return The event-loop-local class value
     */
    Value getEventLoopClass(PythonEventLoop eventLoop, PythonContextRuntime.PythonClassReference classReference) {
        return getOrCreateClass(getOrCreateEventLoopContext(eventLoop), classReference);
    }

    /**
     * Resolve a script/module in the dedicated context associated with an asyncio event loop.
     *
     * @param eventLoop The Python event loop that owns the context
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @return The event-loop-local script value
     */
    Value getEventLoopScript(PythonEventLoop eventLoop, String packageName, String scriptName) {
        return getOrCreateScript(getOrCreateEventLoopContext(eventLoop), packageName, scriptName);
    }

    /**
     * Record and apply a host value injection for a script/module across existing and future
     * pooled contexts.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @param attribute The script member to set
     * @param value The host value to coerce into each target context
     */
    void injectScript(String packageName, String scriptName, String attribute, Object value) {
        injectScript(packageName, scriptName, attribute, value, false);
    }

    /**
     * Record and apply an async-aware host value injection for a script/module across existing and
     * future pooled contexts.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @param attribute The script member to set
     * @param value The host value to adapt for async/event-loop use
     */
    void injectScriptAsync(String packageName, String scriptName, String attribute, Object value) {
        injectScript(packageName, scriptName, attribute, value, true);
    }

    private void injectScript(String packageName, String scriptName, String attribute, Object value, boolean async) {
        String key = scriptKey(packageName, scriptName);
        scriptInjections.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(attribute, value);
        if (async) {
            asyncScriptInjections.computeIfAbsent(key, ignored -> ConcurrentHashMap.newKeySet()).add(attribute);
        }
        // recorded only: a pooled context may be borrowed by another thread and an event-loop context
        // belongs to its loop's thread, so each context applies the injection itself, inside its
        // owner's execution frame, the next time the script is asked for
        injectionVersion.incrementAndGet();
    }

    /**
     * Apply the injections recorded for a script that this context has not applied yet. Called by the
     * context's owner: a borrowed pooled context, an event-loop context on its loop, or the primary
     * context inside a frame.
     */
    private void applyPendingInjections(Context context, String key, Value script) {
        Map<String, Long> applied = appliedInjections.computeIfAbsent(context, ignored -> new ConcurrentHashMap<>());
        long version = injectionVersion.get();
        Long done = applied.get(key);
        if (done != null && done == version) {
            return;
        }
        // the version is read before the injections, so one recorded meanwhile is applied again later
        applyInjections(key, script);
        applied.put(key, version);
    }

    Context getEventLoopContext(PythonEventLoop eventLoop) {
        return getOrCreateEventLoopContext(eventLoop);
    }

    private Context getOrCreateEventLoopContext(PythonEventLoop eventLoop) {
        Context existing = eventLoopContexts.get(eventLoop);
        if (existing != null) {
            return existing;
        }
        Context created = borrow0(false);
        Context result;
        boolean poolClosed;
        synchronized (this) {
            poolClosed = closed;
            if (poolClosed) {
                result = created;
            } else {
                existing = eventLoopContexts.putIfAbsent(eventLoop, created);
                result = existing == null ? created : existing;
            }
        }
        if (poolClosed) {
            closeContext(created);
            throw new IllegalStateException("Pool closed");
        }
        if (!result.equals(created)) {
            cache.remove(created);
            closeContext(created);
        }
        return result;
    }

    /**
     * @param pooled When {@code true}, this context should be counted towards {@link #size} and
     *               included in {@link #pooledContexts}.
     */
    private Context borrow0(boolean pooled) {
        long start = System.nanoTime();
        long lastWarned = start;
        borrows.incrementAndGet();
        boolean waited = false;
        synchronized (this) {
            while (true) {
                if (closed) {
                    throw new IllegalStateException("Pool closed");
                }
                Context polled = pooledQueue.poll();
                if (polled != null) {
                    if (!pooled) {
                        size.decrementAndGet();
                        pooledContexts.remove(polled);
                    }
                    recordWait(start, waited);
                    return polled;
                }
                if (creatingContext == null && (!pooled || size.get() < targetSize)) {
                    // Serialize creation to bound the transient CPU and memory cost of GraalPy startup.
                    creatingContext = Thread.currentThread();
                    break;
                }
                waited = true;
                try {
                    // Another thread is creating a context; wait for it or for a release.
                    if (warnThreshold == null || !warnThreshold.isPositive() || !LOG.isWarnEnabled()) {
                        wait();
                    } else {
                        long now = System.nanoTime();
                        Duration timeUntilWarn = Duration.ofNanos(lastWarned + warnThreshold.toNanos() - now);
                        if (!timeUntilWarn.isPositive()) {
                            LOG.warn("No available Python contexts; waiting {} so far (pool target: {}, current: {}, queue: {})", Duration.ofNanos(now - start), targetSize, size.get(), pooledQueue.size());
                            lastWarned = now;
                            timeUntilWarn = warnThreshold;
                        }
                        // Object.wait expects whole milliseconds plus the remaining nanoseconds.
                        wait(timeUntilWarn.toMillis(), timeUntilWarn.toNanosPart() % 1_000_000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while waiting for a Python context", e);
                }
                // We were notified, retry polling after checking the closed state.
            }
        }
        recordWait(start, waited);
        assert creatingContext == Thread.currentThread();
        try {
            Context created = createBorrowedPooledContext(pooled);
            if (created == null) {
                throw new IllegalStateException("Pool closed");
            }
            return created;
        } finally {
            synchronized (this) {
                creatingContext = null;
                notifyAll();
            }
        }
    }

    private void recordWait(long start, boolean waited) {
        if (!waited) {
            return;
        }
        long elapsed = System.nanoTime() - start;
        waits.incrementAndGet();
        totalWaitNanos.addAndGet(elapsed);
        maxWaitNanos.accumulateAndGet(elapsed, Math::max);
    }

    @Override
    public PythonPoolStatistics statistics() {
        int idle;
        boolean poolClosed;
        synchronized (this) {
            idle = pooledQueue.size();
            poolClosed = closed;
        }
        return new PythonPoolStatistics(targetSize > 0, targetSize, size.get(), idle, eventLoopContexts.size(),
            borrows.get(), waits.get(), totalWaitNanos.get() / 1_000_000, maxWaitNanos.get() / 1_000_000, poolClosed);
    }

    private @Nullable Context createBorrowedPooledContext(boolean pooled) {
        assert Thread.currentThread() == creatingContext;

        if (closed) {
            return null;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Creating Pooled Python context {}", size.get());
        }
        Context c = createContext();
        // Registration and the closed check share the pool monitor with closePool's snapshot, so a
        // context created while the pool closes is either in that snapshot or closed right here.
        synchronized (this) {
            if (!closed) {
                cache.put(c, new ConcurrentHashMap<>());
                if (pooled) {
                    pooledContexts.add(c);
                    size.incrementAndGet();
                }
                return c;
            }
        }
        closeContext(c);
        return null;
    }

    private Context createContext() {
        assert Thread.currentThread() == creatingContext;

        try {
            return GraalPyContextFactory.buildContext(
                hostAccess,
                engine,
                applicationContext.getClassLoader(),
                contextConfiguration
            );
        } catch (IOException e) {
            throw new ApplicationStartupException("Failed to create Python context: " + e.getMessage(), e);
        }
    }

    private static void closeContext(Context context) {
        try {
            GraalPyContextFactory.closeContext(context);
        } catch (PolyglotException e) {
            if (e.isCancelled()) {
                LOG.debug("Python pool context was already cancelled while closing", e);
            } else {
                LOG.warn("Unexpected error while closing Python pool context", e);
                throw e;
            }
        } catch (RuntimeException | Error e) {
            LOG.warn("Unexpected error while closing Python pool context", e);
            throw e;
        }
    }

    private List<Context> snapshotIncludingPrimary() {
        List<Context> contexts = new ArrayList<>(pooledContexts.size() + eventLoopContexts.size() + 1);
        contexts.add(primaryContext);
        contexts.addAll(pooledContexts);
        contexts.addAll(eventLoopContexts.values());
        return contexts;
    }

    /*
     * Guest code never runs inside a ConcurrentHashMap remapping function: the map holds a bin lock
     * there, and Python code re-entering the runtime for the same key would fail with a recursive
     * update, or wait for the bin while its thread owns the GIL that the loading thread waits for.
     * A duplicate load settles with putIfAbsent instead.
     */
    private Value getOrCreateClass(Context c, PythonContextRuntime.PythonClassReference classReference) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = classReference.cacheKey();
        Value existing = m.get(key);
        if (existing != null) {
            return existing;
        }
        Value cls = loadClass(c, classReference);
        Value created = cls.canInstantiate() ? cls.newInstance() : cls;
        Value prior = m.putIfAbsent(key, created);
        return prior != null ? prior : created;
    }

    private Value getOrCreateScript(Context c, String packageName, String scriptName) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = scriptKey(packageName, scriptName);
        Value existing = m.get(key);
        if (existing == null) {
            Value script = loadScript(c, packageName, scriptName);
            Value prior = m.putIfAbsent(key, script);
            existing = prior != null ? prior : script;
        }
        applyPendingInjections(c, key, existing);
        return existing;
    }

    private void applyInjections(String key, Value script) {
        scriptInjections.getOrDefault(key, Map.of())
            .forEach((attribute, value) -> script.putMember(
                attribute,
                coerceInjectedValue(script, value, asyncScriptInjections.getOrDefault(key, java.util.Set.of()).contains(attribute))
            ));
    }

    private Value getOrCreateValue(Context c, String expression) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = valueKey(expression);
        Value existing = m.get(key);
        if (existing != null) {
            return existing;
        }
        Value created = c.eval(PYTHON, expression);
        Value prior = m.putIfAbsent(key, created);
        return prior != null ? prior : created;
    }

    private static @Nullable Object coerceInjectedValue(Value script, Object value, boolean async) {
        if (async) {
            return PythonCoercion.asyncMemberValue(script, value);
        }
        // Introduction and other stateful Python wrappers cannot be reconstructed from Java-side
        // state. Keep them as host proxies when injecting them into pooled modules, as they were
        // before context-local request argument conversion was introduced.
        if (value instanceof ValueCoercible && !(value instanceof PooledValueCoercible)) {
            return value;
        }
        return PythonCoercion.coerceToContext(value, script.getContext());
    }

    private static String scriptKey(String pkg, String script) {
        return "script:" + Objects.toString(pkg, PYTHON) + ":" + script;
    }

    private static String valueKey(String expression) {
        return "value:" + expression;
    }

    private static Value loadClass(Context ctx, PythonContextRuntime.PythonClassReference classReference) {
        return PythonContextRuntime.findClass(classReference, ctx);
    }

    private static Value loadScript(Context ctx, String packageName, String scriptName) {
        return PythonContextRuntime.findScript(packageName, scriptName, ctx);
    }

    @Override
    public void onDestroyed(BeanDestroyedEvent<Context> event) {
        PythonContextRegistry.onNoActiveExecutionsAfterCurrentFrame(event.getBean(), this::closePool);
    }

    /**
     * Complete once every pooled, event-loop and primary context is idle.
     * <p>
     * The pool keeps serving borrows while the application drains: requests that are still in flight
     * may need a context, and the contexts are only closed when the primary context bean is destroyed.
     */
    @Override
    public CompletionStage<?> shutdownGracefully() {
        if (gracefulShutdownStarted.compareAndSet(false, true)) {
            List<Context> contexts;
            synchronized (this) {
                contexts = snapshotIncludingPrimary();
            }
            PythonContextRegistry.onNoActiveExecutions(contexts, () -> gracefulShutdown.complete(null));
        }
        return gracefulShutdown;
    }

    /**
     * Stop handing out contexts and close them once none of them executes Python any more.
     */
    private void closePool() {
        List<Context> contexts;
        synchronized (this) {
            closed = true;
            notifyAll();
            contexts = snapshotIncludingPrimary();
        }
        PythonContextRegistry.closeWhenIdle(contexts, this::closeContexts);
    }

    private void closeContexts() {
        List<Context> snapshot;
        List<Context> eventLoopSnapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(pooledContexts);
            pooledContexts.removeAll(snapshot);
            pooledQueue.removeAll(snapshot);
            size.set(0);
            eventLoopSnapshot = new ArrayList<>(eventLoopContexts.values());
            eventLoopContexts.clear();
        }
        cache.clear();
        appliedInjections.clear();
        scriptInjections.clear();
        asyncScriptInjections.clear();
        List<Context> contexts = new ArrayList<>(snapshot.size() + eventLoopSnapshot.size());
        contexts.addAll(snapshot);
        contexts.addAll(eventLoopSnapshot);
        // every context is closed; the first failure is reported after the last close
        List<Runnable> closes = new ArrayList<>(contexts.size());
        for (Context context : contexts) {
            closes.add(() -> GraalPyContextFactory.closeContext(context));
        }
        PythonContextRegistry.runEach(closes);
    }
}
