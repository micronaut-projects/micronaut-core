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
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Provides a pool of GraalPy {@link Context} instances built on a shared {@link Engine}.
 * <p>
 * The first context (primary) is created synchronously and is not part of the pool. It is exposed
 * via {@link PythonContextRuntime#getContext()} and used for non-pooled operations. Remaining pooled
 * contexts are created on a background thread to avoid blocking startup.
 */
@Singleton
@io.micronaut.context.annotation.Context
@Internal
final class PythonPool implements BeanDestroyedEventListener<Context>, GracefulShutdownCapable, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(PythonPool.class);
    private final Engine engine;
    private final HostAccess hostAccess;
    private final ApplicationContext applicationContext;
    private final boolean syncInit;
    private final long warnThresholdMs;

    private final Context primaryContext;
    private final BlockingDeque<Context> pooledQueue = new LinkedBlockingDeque<>();
    private final List<Context> pooledContexts = new ArrayList<>();
    private final Map<PythonEventLoop, Context> eventLoopContexts = new ConcurrentHashMap<>();
    private final Map<Context, Map<String, Value>> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> scriptInjections = new ConcurrentHashMap<>();
    private final Map<String, java.util.Set<String>> asyncScriptInjections = new ConcurrentHashMap<>();

    private final AtomicInteger size = new AtomicInteger(0);
    private final AtomicReference<Thread> warmupThread = new AtomicReference<>();
    private final AtomicBoolean gracefulShutdownStarted = new AtomicBoolean();
    private final CompletableFuture<Void> gracefulShutdown = new CompletableFuture<>();
    private final int targetSize;
    private volatile boolean closed;

    @Inject
    PythonPool(@Named(GraalPyRuntimeUtil.PYTHON) Engine engine,
               @Named(GraalPyRuntimeUtil.PYTHON) HostAccess hostAccess,
               @Named(GraalPyRuntimeUtil.PYTHON) Context primaryContext,
               ApplicationContext applicationContext,
               PythonPoolConfiguration configuration) {
        this.engine = engine;
        this.primaryContext = primaryContext;
        this.hostAccess = hostAccess;
        this.applicationContext = applicationContext;
        int configuredPoolSize = configuration.size();
        this.syncInit = configuration.syncInit();
        this.warnThresholdMs = configuration.warnWaitMs();
        int processors = Runtime.getRuntime().availableProcessors();
        int defaultSize = Math.max(1, processors * 2);
        this.targetSize = configuration.enabled() ? (configuredPoolSize > 0 ? configuredPoolSize : defaultSize) : 0;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    /**
     * Initializes the pool and primary context.
     * The first (primary) context is created synchronously and exposed to {@link PythonContextRuntime}.
     * Pooled contexts are created synchronously when 'micronaut.python.pool.sync-init' is true,
     * otherwise they are created asynchronously on a background thread.
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
        final int toCreate = targetSize;
        if (syncInit) {
            for (int i = 0; i < toCreate; i++) {
                addPooledContext();
            }
        } else {
            LOG.debug("Initial Python Context pool size is {}", toCreate);
            addPooledContext();

            Thread t = new Thread(() -> {
                try {
                    for (int i = 1; i < toCreate; i++) {
                        if (closed) {
                            return;
                        }
                        addPooledContext();
                    }
                } catch (PolyglotException e) {
                    if (e.isCancelled() && closed) {
                        LOG.debug("Python pool warmup cancelled during shutdown", e);
                    } else {
                        LOG.warn("Unexpected Python pool warmup failure", e);
                        throw e;
                    }
                } catch (RuntimeException e) {
                    LOG.warn("Unexpected Python pool warmup failure", e);
                    throw e;
                }
            }, "python-pool-warmup");
            t.setDaemon(true);
            warmupThread.set(t);
            t.start();
        }
    }

    Context borrow() {
        long waitedMs = 0L;
        try {
            while (true) {
                Context ctx = pooledQueue.pollFirst();
                if (ctx != null) {
                    if (waitedMs >= warnThresholdMs && LOG.isWarnEnabled()) {
                        LOG.warn("Borrowed context after waiting {} ms (pool size: {}, queue: {})", waitedMs, size.get(), pooledQueue.size());
                    }
                    return ctx;
                }
                Thread.sleep(100);
                waitedMs += 100;
                if (warnThresholdMs > 0 && waitedMs % warnThresholdMs == 0 && LOG.isWarnEnabled()) {
                    LOG.warn("No available Python contexts; waiting {} ms so far (pool target: {}, current: {}, queue: {})", waitedMs, targetSize, size.get(), pooledQueue.size());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    void release(Context c) {
        pooledQueue.addLast(c);
    }

    <T> T withClass(@Nullable String packageName, String simpleName, java.util.function.Function<Value, T> fn) {
        Context c = borrow();
        try {
            Value v = getOrCreateClass(c, packageName, simpleName);
            return fn.apply(v);
        } finally {
            release(c);
        }
    }

    <T> T withScript(String packageName, String scriptName, java.util.function.Function<Value, T> fn) {
        Context c = borrow();
        try {
            Value v = getOrCreateScript(c, packageName, scriptName);
            return fn.apply(v);
        } finally {
            release(c);
        }
    }

    Value getAnyClass(@Nullable String packageName, String simpleName) {
        Context c = pooledQueue.peekFirst();
        if (c == null) {
            c = primaryContext;
        }
        return getOrCreateClass(c, packageName, simpleName);
    }

    Value getClass(Context context, @Nullable String packageName, String simpleName) {
        return getOrCreateClass(context, packageName, simpleName);
    }

    Value getAnyScript(String packageName, String scriptName) {
        Context c = pooledQueue.peekFirst();
        if (c == null) {
            c = primaryContext;
        }
        return getOrCreateScript(c, packageName, scriptName);
    }

    Value getScript(Context context, String packageName, String scriptName) {
        return getOrCreateScript(context, packageName, scriptName);
    }

    Value getEventLoopClass(PythonEventLoop eventLoop, @Nullable String packageName, String simpleName) {
        return getOrCreateClass(getOrCreateEventLoopContext(eventLoop), packageName, simpleName);
    }

    Value getEventLoopScript(PythonEventLoop eventLoop, String packageName, String scriptName) {
        return getOrCreateScript(getOrCreateEventLoopContext(eventLoop), packageName, scriptName);
    }

    void injectScript(String packageName, String scriptName, String attribute, Object value) {
        injectScript(packageName, scriptName, attribute, value, false);
    }

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

    private Context getOrCreateEventLoopContext(PythonEventLoop eventLoop) {
        Context existing = eventLoopContexts.get(eventLoop);
        if (existing != null) {
            return existing;
        }
        synchronized (eventLoopContexts) {
            existing = eventLoopContexts.get(eventLoop);
            if (existing != null) {
                return existing;
            }
            Context created = pooledQueue.pollFirst();
            if (created != null) {
                pooledContexts.remove(created);
                size.decrementAndGet();
                replenishPooledContextAsync();
            } else {
                created = createContext();
            }
            eventLoopContexts.put(eventLoop, created);
            cache.put(created, new ConcurrentHashMap<>());
            return created;
        }
    }

    private synchronized void addPooledContext() {
        if (closed || size.get() >= targetSize) {
            return;
        }
        Context c = createContext();
        if (closed) {
            try {
                GraalPyContextFactory.closeContext(c, true);
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
            return;
        }
        pooledContexts.add(c);
        cache.put(c, new ConcurrentHashMap<>());
        pooledQueue.addLast(c);
        size.incrementAndGet();
    }

    private void replenishPooledContextAsync() {
        if (closed || size.get() >= targetSize) {
            return;
        }
        Thread t = new Thread(() -> {
            try {
                addPooledContext();
            } catch (PolyglotException e) {
                if (e.isCancelled() && closed) {
                    LOG.debug("Python pool replenishment cancelled during shutdown", e);
                } else {
                    LOG.warn("Unexpected Python pool replenishment failure", e);
                    throw e;
                }
            } catch (RuntimeException e) {
                LOG.warn("Unexpected Python pool replenishment failure", e);
                throw e;
            }
        }, "python-pool-replenish");
        t.setDaemon(true);
        t.start();
    }

    private Context createContext() {
        try {
            return GraalPyContextFactory.buildContext(
                hostAccess,
                engine,
                applicationContext.getClassLoader()
            );
        } catch (IOException e) {
            throw new ApplicationStartupException("Failed to create Python context: " + e.getMessage(), e);
        }
    }

    private List<Context> snapshot() {
        return new ArrayList<>(pooledContexts);
    }

    private List<Context> snapshotIncludingPrimary() {
        List<Context> contexts = new ArrayList<>(pooledContexts.size() + eventLoopContexts.size() + 1);
        contexts.add(primaryContext);
        contexts.addAll(pooledContexts);
        contexts.addAll(eventLoopContexts.values());
        return contexts;
    }

    private Value getOrCreateClass(Context c, @Nullable String packageName, String simpleName) {
        Map<String, Value> m = cache.computeIfAbsent(c, k -> new ConcurrentHashMap<>());
        String key = classInstanceKey(packageName, simpleName);
        return m.computeIfAbsent(key, k -> {
            Value cls = loadClass(c, packageName, simpleName);
            if (cls != null && cls.canInstantiate()) {
                return cls.newInstance();
            }
            return cls;
        });
    }

    private Value getOrCreateScript(Context c, String packageName, String scriptName) {
        Map<String, Value> m = cache.computeIfAbsent(c, k -> new ConcurrentHashMap<>());
        String key = scriptKey(packageName, scriptName);
        return m.computeIfAbsent(key, k -> {
            Value script = loadScript(c, packageName, scriptName);
            scriptInjections.getOrDefault(key, Map.of())
                .forEach((attribute, value) -> script.putMember(
                    attribute,
                    coerceInjectedValue(script, value, asyncScriptInjections.getOrDefault(key, java.util.Set.of()).contains(attribute))
                ));
            return script;
        });
    }

    private static @Nullable Object coerceInjectedValue(Value script, Object value, boolean async) {
        return async
            ? GraalPyRuntimeUtil.asyncMemberValue(script, value)
            : GraalPyRuntimeUtil.coerceToContext(value, script.getContext());
    }

    private static String classInstanceKey(@Nullable String pkg, String simple) {
        return "class-instance:" + Objects.toString(pkg, PYTHON) + "." + simple;
    }

    private static String scriptKey(String pkg, String script) {
        return "script:" + Objects.toString(pkg, PYTHON) + ":" + script;
    }

    private static Value loadClass(Context ctx, @Nullable String packageName, String simpleName) {
        return PythonContextRuntime.findClass(packageName, simpleName, ctx);
    }

    private static Value loadScript(Context ctx, String packageName, String scriptName) {
        return PythonContextRuntime.findScript(packageName, scriptName, ctx);
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Context> event) {
        PythonContextRuntime.onNoActiveExecutionsAfterCurrentFrame(event.getBean(), () -> closePool(true));
    }

    @Override
    public CompletionStage<?> shutdownGracefully() {
        if (gracefulShutdownStarted.compareAndSet(false, true)) {
            stopWarmup();
            PythonContextRuntime.onNoActiveExecutions(snapshotIncludingPrimary(), () -> gracefulShutdown.complete(null));
        }
        return gracefulShutdown;
    }

    private void stopWarmup() {
        closed = true;
        Thread thread = warmupThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void closePool(boolean cancelIfExecuting) {
        stopWarmup();
        List<Context> snapshot = snapshot();
        pooledContexts.removeAll(snapshot);
        pooledQueue.removeAll(snapshot);
        List<Context> eventLoopSnapshot = new ArrayList<>(eventLoopContexts.values());
        eventLoopContexts.clear();
        cache.clear();
        scriptInjections.clear();
        asyncScriptInjections.clear();
        List<Context> contexts = new ArrayList<>(snapshot.size() + eventLoopSnapshot.size());
        contexts.addAll(snapshot);
        contexts.addAll(eventLoopSnapshot);
        for (Context context : contexts) {
            GraalPyContextFactory.closeContext(context, cancelIfExecuting);
        }
    }
}
