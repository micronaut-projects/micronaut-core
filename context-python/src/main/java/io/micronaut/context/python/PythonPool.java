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
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Engine;
import org.graalvm.polyglot.HostAccess;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static io.micronaut.context.python.GraalPyRuntimeUtil.PYTHON;

/**
 * Provides a pool of GraalPy {@link Context} instances built on a shared {@link Engine}.
 * <p>
 * The first context (primary) is created synchronously and is not part of the pool. It is exposed
 * via {@link ContextHolder#getContext()} and used for non-pooled operations. Remaining pooled
 * contexts are created on a background thread to avoid blocking startup.
 */
@Singleton
@io.micronaut.context.annotation.Context
@Internal
final class PythonPool implements BeanDestroyedEventListener<Context>, Ordered {
    private static final Logger LOG = LoggerFactory.getLogger(PythonPool.class);
    private final Engine engine;
    private final HostAccess hostAccess;
    private final ApplicationContext applicationContext;
    private final boolean syncInit;
    private final long warnThresholdMs;

    private final Context primaryContext;
    private final BlockingDeque<Context> pooledQueue = new LinkedBlockingDeque<>();
    private final List<Context> pooledContexts = new ArrayList<>();
    private final Map<Context, Map<String, Value>> cache = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> scriptInjections = new ConcurrentHashMap<>();

    private final AtomicInteger size = new AtomicInteger(0);
    private final AtomicReference<Thread> warmupThread = new AtomicReference<>();
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
     * The first (primary) context is created synchronously and exposed to {@link ContextHolder}.
     * Pooled contexts are created synchronously when 'micronaut.python.pool.sync-init' is true,
     * otherwise they are created asynchronously on a background thread.
     */
    @PostConstruct
    void init() {
        // If reuseContext is enabled, skip pool initialization entirely
        if (ContextHolder.isReuseContext()) {
            LOG.debug("Context reuse enabled; skipping Python context pool initialization");
            ContextHolder.setPythonPool(null);
            return;
        }
        // Register pool and prepare caches if enabled
        if (targetSize <= 0) {
            LOG.debug("Python context pool disabled via configuration; skipping initialization");
            ContextHolder.setPythonPool(null);
            return;
        }
        ContextHolder.setPythonPool(this);
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
                } catch (IllegalStateException ignored) {
                    // ignore during shutdown
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

    void injectScript(String packageName, String scriptName, String attribute, Object value) {
        String key = scriptKey(packageName, scriptName);
        scriptInjections.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>()).put(attribute, value);
        for (Context context : snapshotIncludingPrimary()) {
            Map<String, Value> values = cache.get(context);
            if (values == null) {
                continue;
            }
            Value script = values.get(key);
            if (script != null) {
                script.putMember(attribute, coerceInjectedValue(context, value));
            }
        }
    }

    private synchronized void addPooledContext() {
        if (closed || size.get() >= targetSize) {
            return;
        }
        Context c;
        try {
            c = GraalPyContextFactory.buildContext(
                hostAccess,
                engine,
                applicationContext.getClassLoader()
            );
        } catch (IOException e) {
            throw new ApplicationStartupException("Failed to create Python context: " + e.getMessage(), e);
        }
        if (closed) {
            try {
                c.close(false);
            } catch (Exception e) {
                LOG.warn("Error while closing context: " + e.getMessage(), e);
            }
            return;
        }
        pooledContexts.add(c);
        cache.put(c, new ConcurrentHashMap<>());
        pooledQueue.addLast(c);
        size.incrementAndGet();
    }

    private List<Context> snapshot() {
        return new ArrayList<>(pooledContexts);
    }

    private List<Context> snapshotIncludingPrimary() {
        List<Context> contexts = new ArrayList<>(pooledContexts.size() + 1);
        contexts.add(primaryContext);
        contexts.addAll(pooledContexts);
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
                .forEach((attribute, value) -> script.putMember(attribute, coerceInjectedValue(c, value)));
            return script;
        });
    }

    private static @Nullable Object coerceInjectedValue(Context context, Object value) {
        return GraalPyRuntimeUtil.coerceToContext(value, context);
    }

    private static String classInstanceKey(@Nullable String pkg, String simple) {
        return "class-instance:" + Objects.toString(pkg, PYTHON) + "." + simple;
    }

    private static String scriptKey(String pkg, String script) {
        return "script:" + Objects.toString(pkg, PYTHON) + ":" + script;
    }

    private static Value loadClass(Context ctx, @Nullable String packageName, String simpleName) {
        return ContextHolder.findClass(packageName, simpleName, ctx);
    }

    private static Value loadScript(Context ctx, String packageName, String scriptName) {
        return ContextHolder.findScript(packageName, scriptName, ctx);
    }

    @Override
    public void onDestroyed(@NonNull BeanDestroyedEvent<Context> event) {
        closed = true;
        Thread thread = warmupThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
        List<Context> snapshot = snapshot();
        pooledContexts.removeAll(snapshot);
        pooledQueue.removeAll(snapshot);
        cache.clear();
        scriptInjections.clear();
        for (Context context : snapshot) {
            try {
                context.close(false);
            } catch (Exception e) {
                LOG.warn("Error while closing context: " + e.getMessage(), e);
            }
        }
    }
}
