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
import java.util.function.Function;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

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

    private @Nullable Thread creatingContext = null;
    private final Queue<Context> pooledQueue = new ArrayDeque<>();
    private final List<Context> pooledContexts = new CopyOnWriteArrayList<>();
    private final Map<PythonEventLoop, Context> eventLoopContexts = new ConcurrentHashMap<>();
    private final Map<Context, Map<String, Value>> cache = new ConcurrentHashMap<>();

    private final Map<String, Map<String, Object>> scriptInjections = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<String>> asyncScriptInjections = new ConcurrentHashMap<>();

    private final AtomicInteger size = new AtomicInteger(0);
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
     * @param applicationContext The application context used to obtain the class loader
     * @param configuration The Python pool configuration
     */
    @Inject
    PythonPool(@Named(GraalPyRuntimeUtil.PYTHON) Engine engine,
               @Named(GraalPyRuntimeUtil.PYTHON) HostAccess hostAccess,
               @Named(GraalPyRuntimeUtil.PYTHON) Context primaryContext,
               ApplicationContext applicationContext,
               GraalPyContextConfiguration contextConfiguration,
               PythonPoolConfiguration configuration) {
        this.engine = engine;
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
     * the pool from {@link PythonContextRuntime}. Otherwise it exposes the pool immediately and
     * prepares the primary context cache. Pooled contexts are created lazily by {@link #borrow()}.
     */
    @PostConstruct
    void init() {
        // If reuseContext is enabled, skip pool initialization entirely
        if (PythonContextRuntime.isReuseContext()) {
            LOG.debug("Context reuse enabled; skipping Python context pool initialization");
            PythonContextRuntime.setPythonPool(null);
            return;
        }
        // Register pool and prepare caches if enabled
        if (targetSize <= 0) {
            LOG.debug("Python context pool disabled via configuration; skipping initialization");
            PythonContextRuntime.setPythonPool(null);
            return;
        }
        PythonContextRuntime.setPythonPool(this);
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
        if (closed) {
            return;
        }
        synchronized (this) {
            pooledQueue.add(c);
            notify();
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
            return PythonContextRuntime.withContextLock(eventLoopContext,
                () -> PythonContextRuntime.withExecutionFrame(eventLoopContext, () -> callback.apply(eventLoopContext)));
        }
        Context borrowed = borrow();
        try {
            return PythonContextRuntime.withExecutionFrame(borrowed, () -> callback.apply(borrowed));
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
            Value v = getOrCreateClass(c, classReference);
            return fn.apply(v);
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
            Value v = getOrCreateScript(c, packageName, scriptName);
            return fn.apply(v);
        } finally {
            release(c);
        }
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
            Value v = getOrCreateValue(c, expression);
            return fn.apply(v);
        } finally {
            release(c);
        }
    }

    /**
     * Resolve a class instance from an available pooled context without borrowing it.
     * <p>
     * This is intended for callers that only need a cached class value and do not require exclusive
     * context ownership. The primary context is used as a fallback when the pool has not warmed yet.
     *
     * @param classReference The class reference
     * @return A context-local class value
     */
    Value getAnyClass(PythonContextRuntime.PythonClassReference classReference) {
        Context c;
        synchronized (this) {
            c = pooledQueue.peek();
        }
        if (c == null) {
            c = primaryContext;
        }
        return getOrCreateClass(c, classReference);
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
     * Resolve a script/module from an available pooled context without borrowing it.
     * <p>
     * The primary context is used as a fallback when no pooled context is currently available.
     *
     * @param packageName The Python package, or {@code python} for top-level scripts
     * @param scriptName The script/module name
     * @return A context-local script value
     */
    Value getAnyScript(String packageName, String scriptName) {
        Context c;
        synchronized (this) {
            c = pooledQueue.peek();
        }
        if (c == null) {
            c = primaryContext;
        }
        return getOrCreateScript(c, packageName, scriptName);
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
        for (Context context : snapshotIncludingPrimary()) {
            Map<String, Value> values = cache.get(context);
            if (values == null) {
                continue;
            }
            Value script = values.get(key);
            if (script != null) {
                script.putMember(attribute, coerceInjectedValue(script, value, async));
            }
        }
    }

    Context getEventLoopContext(PythonEventLoop eventLoop) {
        return getOrCreateEventLoopContext(eventLoop);
    }

    private Context getOrCreateEventLoopContext(PythonEventLoop eventLoop) {
        Context existing = eventLoopContexts.get(eventLoop);
        if (existing != null) {
            return existing;
        }
        return eventLoopContexts.computeIfAbsent(eventLoop, _ -> borrow0(false));
    }

    /**
     * @param pooled When {@code true}, this context should be counted towards {@link #size} and
     *               included in {@link #pooledContexts}.
     */
    private Context borrow0(boolean pooled) {
        long start = System.nanoTime();
        long lastWarned = start;
        boolean interrupted = false;
        try {
            synchronized (this) {
                while (true) {
                    Context polled = pooledQueue.poll();
                    if (polled != null) {
                        if (!pooled) {
                            size.decrementAndGet();
                            pooledContexts.remove(polled);
                        }
                        return polled;
                    }
                    if (creatingContext == null && (!pooled || size.get() < targetSize)) {
                        // we can create a context.
                        creatingContext = Thread.currentThread();
                        break;
                    }
                    try {
                        // another thread is creating a context, wait for it or wait for the queue to get a new item.
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
                            wait(timeUntilWarn.toMillis(), timeUntilWarn.toNanosPart() % 1_000_000);
                        }
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                    // we were notified, retry polling
                }
            }
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
                    notify();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
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
        if (closed) {
            try {
                GraalPyContextFactory.closeContext(c);
            } catch (PolyglotException e) {
                if (e.isCancelled()) {
                    LOG.debug("Python pool context close cancelled during shutdown", e);
                } else {
                    LOG.warn("Unexpected error while closing Python pool context", e);
                    throw e;
                }
            } catch (RuntimeException | Error e) {
                LOG.warn("Unexpected error while closing Python pool context", e);
                throw e;
            }
            return null;
        }
        cache.put(c, new ConcurrentHashMap<>());
        if (pooled) {
            pooledContexts.add(c);
            size.incrementAndGet();
        }
        return c;
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

    private List<Context> snapshotIncludingPrimary() {
        List<Context> contexts = new ArrayList<>(pooledContexts.size() + eventLoopContexts.size() + 1);
        contexts.add(primaryContext);
        contexts.addAll(pooledContexts);
        contexts.addAll(eventLoopContexts.values());
        return contexts;
    }

    private Value getOrCreateClass(Context c, PythonContextRuntime.PythonClassReference classReference) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = classReference.cacheKey();
        return m.computeIfAbsent(key, _ -> {
            Value cls = loadClass(c, classReference);
            if (cls.canInstantiate()) {
                return cls.newInstance();
            }
            return cls;
        });
    }

    private Value getOrCreateScript(Context c, String packageName, String scriptName) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = scriptKey(packageName, scriptName);
        return m.computeIfAbsent(key, _ -> {
            Value script = loadScript(c, packageName, scriptName);
            scriptInjections.getOrDefault(key, Map.of())
                .forEach((attribute, value) -> script.putMember(
                    attribute,
                    coerceInjectedValue(script, value, asyncScriptInjections.getOrDefault(key, java.util.Set.of()).contains(attribute))
                ));
            return script;
        });
    }

    private Value getOrCreateValue(Context c, String expression) {
        Map<String, Value> m = cache.computeIfAbsent(c, _ -> new ConcurrentHashMap<>());
        String key = valueKey(expression);
        return m.computeIfAbsent(key, _ -> PythonContextRuntime.withContextLock(c, () -> c.eval(PYTHON, expression)));
    }

    private static @Nullable Object coerceInjectedValue(Value script, Object value, boolean async) {
        return async
            ? GraalPyRuntimeUtil.asyncMemberValue(script, value)
            : GraalPyRuntimeUtil.coerceToContext(value, script.getContext());
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
        PythonContextRuntime.onNoActiveExecutionsAfterCurrentFrame(event.getBean(), this::closePool);
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        if (gracefulShutdownStarted.compareAndSet(false, true)) {
            List<Context> contexts;
            synchronized (this) {
                closed = true;
                notifyAll();
                contexts = snapshotIncludingPrimary();
            }
            PythonContextRuntime.onNoActiveExecutions(contexts, () -> gracefulShutdown.complete(null));
        }
        return gracefulShutdown;
    }

    private void closePool() {
        List<Context> snapshot;
        synchronized (this) {
            closed = true;
            notifyAll();
            snapshot = new ArrayList<>(pooledContexts);
            pooledContexts.removeAll(snapshot);
            pooledQueue.removeAll(snapshot);
        }
        List<Context> eventLoopSnapshot = new ArrayList<>(eventLoopContexts.values());
        eventLoopContexts.clear();
        cache.clear();
        scriptInjections.clear();
        asyncScriptInjections.clear();
        List<Context> contexts = new ArrayList<>(snapshot.size() + eventLoopSnapshot.size());
        contexts.addAll(snapshot);
        contexts.addAll(eventLoopSnapshot);
        for (Context context : contexts) {
            GraalPyContextFactory.closeContext(context);
        }
    }
}
