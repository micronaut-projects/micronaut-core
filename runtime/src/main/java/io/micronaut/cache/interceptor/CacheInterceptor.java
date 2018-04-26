/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.cache.interceptor;

import io.micronaut.aop.InterceptPhase;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.cache.AsyncCache;
import io.micronaut.cache.AsyncCacheErrorHandler;
import io.micronaut.cache.CacheErrorHandler;
import io.micronaut.cache.CacheManager;
import io.micronaut.cache.SyncCache;
import io.micronaut.cache.annotation.CacheConfig;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.CachePut;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.cache.annotation.InvalidateOperations;
import io.micronaut.cache.annotation.PutOperations;
import io.micronaut.cache.exceptions.CacheSystemException;
import io.micronaut.context.BeanContext;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.publisher.SingleSubscriberPublisher;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.scheduling.TaskExecutors;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * <p>An AOP {@link MethodInterceptor} implementation for the Cache annotations {@link Cacheable},
 * {@link CachePut} and {@link CacheInvalidate}.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton

public class CacheInterceptor implements MethodInterceptor<Object, Object> {
    /**
     * The position on the interceptor in the chain.
     */
    public static final int POSITION = InterceptPhase.CACHE.getPosition();

    private static final Logger LOG = LoggerFactory.getLogger(CacheInterceptor.class);

    private final CacheManager cacheManager;
    private final Map<Class<? extends CacheKeyGenerator>, CacheKeyGenerator> keyGenerators = new ConcurrentHashMap<>();
    private final BeanContext beanContext;
    private final ExecutorService ioExecutor;
    private final CacheErrorHandler errorHandler;
    private final AsyncCacheErrorHandler asyncCacheErrorHandler;

    /**
     * Create Cache Interceptor with given arguments.
     *
     * @param cacheManager           The cache manager
     * @param errorHandler           Cache error handler
     * @param asyncCacheErrorHandler Async cache error handlers
     * @param ioExecutor             The executor to create tasks
     * @param beanContext            The bean context to allow DI
     */
    public CacheInterceptor(CacheManager cacheManager,
                            CacheErrorHandler errorHandler,
                            AsyncCacheErrorHandler asyncCacheErrorHandler,
                            @Named(TaskExecutors.IO) ExecutorService ioExecutor,
                            BeanContext beanContext) {
        this.cacheManager = cacheManager;
        this.errorHandler = errorHandler;
        this.asyncCacheErrorHandler = asyncCacheErrorHandler;
        this.beanContext = beanContext;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        if (context.hasStereotype(CacheConfig.class)) {
            ReturnType returnTypeObject = context.getReturnType();
            Class returnType = returnTypeObject.getType();
            if (CompletableFuture.class.isAssignableFrom(returnType)) {
                return interceptCompletableFuture(context, returnTypeObject, returnType);
            } else if (Publishers.isConvertibleToPublisher(returnType)) {
                return interceptPublisher(context, returnTypeObject, returnType);
            } else {
                return interceptSync(context, returnTypeObject, returnType);
            }
        } else {
            return context.proceed();
        }
    }

    /**
     * Intercept the annotated method invocation with sync.
     *
     * @param context          Contains information about method invocation
     * @param returnTypeObject The return type of the method in Micronaut
     * @param returnType       The return type class
     * @return The value from the cache
     */
    protected Object interceptSync(MethodInvocationContext context, ReturnType returnTypeObject, Class returnType) {
        final ValueWrapper wrapper = new ValueWrapper();
        CacheOperation cacheOperation = new CacheOperation(context, returnType);

        Cacheable cacheConfig = cacheOperation.cacheable;
        if (cacheConfig != null) {
            CacheKeyGenerator defaultKeyGenerator = cacheOperation.defaultKeyGenerator;
            CacheKeyGenerator keyGenerator = resolveKeyGenerator(defaultKeyGenerator, cacheConfig);
            Object[] parameterValues = resolveParams(context, cacheConfig.parameters());
            Object key = keyGenerator.generateKey(context, parameterValues);
            Argument returnArgument = returnTypeObject.asArgument();
            if (cacheConfig.atomic()) {
                SyncCache syncCache = cacheManager.getCache(cacheOperation.cacheableCacheName);

                try {
                    wrapper.value = syncCache.get(key, returnArgument, () -> {
                        try {
                            doProceed(context, wrapper);
                            return wrapper.value;
                        } catch (RuntimeException e) {
                            throw new ValueSupplierException(key, e);
                        }
                    });
                } catch (ValueSupplierException e) {
                    throw e.getCause();
                } catch (RuntimeException e) {
                    errorHandler.handleLoadError(syncCache, key, e);
                    throw e;
                }
            } else {
                String[] cacheNames = resolveCacheNames(cacheOperation.defaultConfig, cacheConfig);
                boolean cacheHit = false;
                for (String cacheName : cacheNames) {
                    SyncCache syncCache = cacheManager.getCache(cacheName);
                    try {
                        Optional optional = syncCache.get(key, returnArgument);
                        if (optional.isPresent()) {
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("Value found in cache [" + cacheName + "] for invocation: " + context);
                            }
                            cacheHit = true;
                            wrapper.value = optional.get();
                            break;
                        }
                    } catch (RuntimeException e) {
                        if (errorHandler.handleLoadError(syncCache, key, e)) {
                            throw e;
                        }
                    }
                }
                if (!cacheHit) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value not found in cache for invocation: " + context);
                    }
                    doProceed(context, wrapper);
                    syncPut(cacheNames, key, wrapper.value);
                }
            }
        } else {
            if (!cacheOperation.hasWriteOperations()) {
                return context.proceed();
            } else {
                doProceed(context, wrapper);
            }
        }

        CachePut[] cachePuts = cacheOperation.putOperations;
        if (cachePuts != null) {

            for (CachePut cachePut : cachePuts) {
                boolean async = cachePut.async();
                if (async) {
                    ioExecutor.submit(() ->
                            processCachePut(context, wrapper, cachePut, cacheOperation)
                    );
                } else {
                    processCachePut(context, wrapper, cachePut, cacheOperation);
                }
            }
        }

        CacheInvalidate[] cacheInvalidates = cacheOperation.invalidateOperations;
        if (cacheInvalidates != null) {
            for (CacheInvalidate cacheInvalidate : cacheInvalidates) {
                boolean async = cacheInvalidate.async();
                if (async) {
                    ioExecutor.submit(() -> {
                                try {
                                    processCacheEvict(context, cacheInvalidate, cacheOperation, async);
                                } catch (Exception e) {
                                    throw new CacheSystemException("Cache invalidate operation failed: " + e.getMessage(), e);
                                }
                            }
                    );
                } else {
                    processCacheEvict(context, cacheInvalidate, cacheOperation, async);
                }
            }
        }

        return wrapper.optional ? Optional.ofNullable(wrapper.value) : wrapper.value;
    }

    /**
     * Intercept the aync method invocation.
     *
     * @param context          Contains information about method invocation
     * @param returnTypeObject The return type of the method in Micronaut
     * @param returnType       The return type class
     * @return The value from the cache
     */
    protected Object interceptCompletableFuture(MethodInvocationContext<Object, Object> context, ReturnType<?> returnTypeObject, Class returnType) {
        CacheOperation cacheOperation = new CacheOperation(context, returnType);
        Cacheable cacheable = cacheOperation.cacheable;
        CompletableFuture<Object> returnFuture;
        if (cacheable != null) {
            AsyncCache<?> asyncCache = cacheManager.getCache(cacheOperation.cacheableCacheName).async();
            CacheKeyGenerator keyGenerator = resolveKeyGenerator(cacheOperation.defaultKeyGenerator, cacheable);
            Object[] params = resolveParams(context, cacheable.parameters());
            Object key = keyGenerator.generateKey(context, params);
            CompletableFuture<Object> thisFuture = new CompletableFuture<>();
            Argument<?> firstTypeVariable = returnTypeObject.getFirstTypeVariable().orElse(Argument.of(Object.class));
            asyncCache.get(key, firstTypeVariable).whenComplete((BiConsumer<Optional<?>, Throwable>) (o, throwable) -> {
                if (throwable == null && o.isPresent()) {
                    // cache hit, return result
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Value found in cache [" + asyncCache.getName() + "] for invocation: " + context);
                    }
                    thisFuture.complete(o.get());
                } else {
                    // cache miss proceed with original future
                    try {
                        if (throwable != null) {
                            if (errorHandler.handleLoadError(asyncCache, key, asRuntimeException(throwable))) {
                                thisFuture.completeExceptionally(throwable);
                                return;
                            }
                        }
                        CompletableFuture<?> completableFuture = (CompletableFuture) context.proceed();
                        if (completableFuture == null) {
                            thisFuture.complete(null);
                        } else {
                            completableFuture.whenComplete((BiConsumer<Object, Throwable>) (o1, t2) -> {
                                if (t2 != null) {
                                    thisFuture.completeExceptionally(t2);
                                } else {
                                    // new cacheable result, cache it
                                    asyncCache.put(key, o1).whenComplete((aBoolean, throwable1) -> {
                                        if (throwable1 == null) {
                                            thisFuture.complete(o1);
                                        } else {
                                            thisFuture.completeExceptionally(throwable1);
                                        }
                                    });

                                }
                            });
                        }
                    } catch (RuntimeException e) {
                        thisFuture.completeExceptionally(e);
                    }
                }
            });
            returnFuture = thisFuture;
        } else {
            returnFuture = (CompletableFuture<Object>) context.proceed();
        }
        if (cacheOperation.hasWriteOperations()) {
            returnFuture = processFuturePutOperations(context, cacheOperation, returnFuture);
        }
        return returnFuture;
    }

    /**
     * Saving inside the cache.
     *
     * @param context Contains information about method invocation
     * @return The operations to cause the return value to be cached within the given cache name.
     */
    CachePut[] putOperations(MethodInvocationContext context) {
        if (context.hasStereotype(CachePut.class)) {
            return new CachePut[]{context.getAnnotation(CachePut.class)};
        } else if (context.hasStereotype(PutOperations.class)) {
            return context.getAnnotation(PutOperations.class).value();
        } else {
            return null;
        }
    }

    /**
     * Evict from the cache.
     *
     * @param context Extended version of {@link io.micronaut.aop.InvocationContext} for {@link MethodInterceptor} instances
     * @return The operations to cause the eviction of the given caches
     */
    CacheInvalidate[] invalidateOperations(MethodInvocationContext context) {
        if (context.hasStereotype(CacheInvalidate.class)) {
            return new CacheInvalidate[]{context.getAnnotation(CacheInvalidate.class)};
        } else if (context.hasStereotype(InvalidateOperations.class)) {
            return context.getAnnotation(InvalidateOperations.class).value();
        } else {
            return null;
        }
    }

    private Object interceptPublisher(MethodInvocationContext<Object, Object> context, ReturnType returnTypeObject, Class returnType) {
        CacheOperation cacheOperation = new CacheOperation(context, returnType);
        Cacheable cacheable = cacheOperation.cacheable;
        if (cacheable != null) {

            SingleSubscriberPublisher<Object> publisher = buildPublisher(context, returnTypeObject, cacheOperation, cacheable);
            Optional converted = ConversionService.SHARED.convert(publisher, ConversionContext.of(returnTypeObject.asArgument()));
            if (converted.isPresent()) {
                return converted.get();
            } else {
                throw new UnsupportedOperationException("Cannot convert publisher into target type: " + returnType);
            }
        } else {
            return context.proceed();
        }
    }

    private SingleSubscriberPublisher<Object> buildPublisher(MethodInvocationContext<Object, Object> context, ReturnType returnTypeObject, CacheOperation cacheOperation, Cacheable cacheable) {
        return new SingleSubscriberPublisher<Object>() {

            @Override
            protected void doSubscribe(Subscriber<? super Object> subscriber) {
                subscriber.onSubscribe(new Subscription() {
                    CompletableFuture future = null; // for cancellation

                    @Override
                    public void request(long n) {
                        if (n > 0) {
                            AsyncCache<?> asyncCache = cacheManager.getCache(cacheOperation.cacheableCacheName).async();
                            CacheKeyGenerator keyGenerator = resolveKeyGenerator(cacheOperation.defaultKeyGenerator, cacheable);
                            Object[] params = resolveParams(context, cacheable.parameters());
                            Object key = keyGenerator.generateKey(context, params);
                            Argument<?> firstTypeVariable = returnTypeObject.getFirstTypeVariable().orElse(Argument.of(Object.class));
                            future = asyncCache.get(key, firstTypeVariable).whenComplete((BiConsumer<Optional<?>, Throwable>) (o, throwable) -> {
                                if (throwable == null && o.isPresent()) {
                                    // cache hit, return to original subscriber
                                    if (LOG.isDebugEnabled()) {
                                        LOG.debug("Value found in cache [" + asyncCache.getName() + "] for invocation: " + context);
                                    }
                                    subscriber.onNext(o.get());
                                    subscriber.onComplete();
                                } else {
                                    if (throwable != null) {
                                        if (errorHandler.handleLoadError(asyncCache, key, asRuntimeException(throwable))) {
                                            subscriber.onError(throwable);
                                            return;
                                        }
                                    }

                                    Publisher<?> actualPublisher = (Publisher) context.proceed();
                                    if (actualPublisher == null) {
                                        // no publisher, simply complete
                                        subscriber.onComplete();
                                    } else {
                                        // cache miss, subscribe to original publisher
                                        actualPublisher.subscribe(new Subscriber<Object>() {
                                            boolean hasData = false;

                                            @Override
                                            public void onSubscribe(Subscription s) {
                                                s.request(n);
                                            }

                                            @Override
                                            public void onNext(Object o) {
                                                hasData = true;
                                                // got result, cache it
                                                asyncCache.put(key, o).whenComplete((aBoolean, throwable1) -> {
                                                    if (throwable1 == null) {
                                                        subscriber.onNext(o);
                                                        subscriber.onComplete();
                                                    } else {
                                                        subscriber.onError(throwable1);
                                                    }
                                                });
                                            }

                                            @Override
                                            public void onError(Throwable t) {
                                                subscriber.onError(t);
                                            }

                                            @Override
                                            public void onComplete() {
                                                if (!hasData) {
                                                    subscriber.onComplete();
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }

                    @Override
                    public void cancel() {
                        if (future != null) {
                            future.cancel(false);
                        }
                    }
                });
            }
        };
    }

    private CompletableFuture<Object> processFuturePutOperations(MethodInvocationContext<Object, Object> context, CacheOperation cacheOperation, CompletableFuture<Object> returnFuture) {
        CachePut[] putOperations = cacheOperation.putOperations;
        if (putOperations != null) {
            for (CachePut putOperation : putOperations) {
                String[] cacheNames = cacheOperation.getCacheNames(putOperation);

                if (ArrayUtils.isNotEmpty(cacheNames)) {
                    boolean isAsync = putOperation.async();
                    if (!isAsync) {
                        CompletableFuture<Object> newFuture = new CompletableFuture<>();
                        returnFuture.whenComplete((result, throwable) -> {
                            if (throwable == null) {
                                try {
                                    CacheKeyGenerator keyGenerator = cacheOperation.getKeyGenerator(putOperation);
                                    Object[] parameterValues = resolveParams(context, putOperation.parameters());
                                    Object key = keyGenerator.generateKey(context, parameterValues);
                                    CompletableFuture<Void> putOperationFuture = buildPutFutures(cacheNames, result, key);

                                    putOperationFuture.whenComplete((aVoid, error) -> {
                                        if (error != null) {
                                            SyncCache cache = cacheManager.getCache(cacheNames[0]);
                                            if (errorHandler.handlePutError(cache, key, result, asRuntimeException(error))) {
                                                newFuture.completeExceptionally(error);
                                            } else {
                                                newFuture.complete(result);
                                            }
                                        } else {
                                            newFuture.complete(result);
                                        }
                                    });
                                } catch (Exception e) {
                                    newFuture.completeExceptionally(e);
                                }
                            } else {
                                newFuture.completeExceptionally(throwable);
                            }
                        });
                        returnFuture = newFuture;
                    } else {
                        returnFuture.whenCompleteAsync((result, throwable) -> {
                            if (throwable == null) {
                                try {
                                    CacheKeyGenerator keyGenerator = cacheOperation.getKeyGenerator(putOperation);
                                    Object[] parameterValues = resolveParams(context, putOperation.parameters());
                                    Object key = keyGenerator.generateKey(context, parameterValues);
                                    CompletableFuture<Void> putOperationFuture = buildPutFutures(cacheNames, result, key);

                                    putOperationFuture.whenComplete((aVoid, error) -> {
                                        if (error != null) {
                                            SyncCache cache = cacheManager.getCache(cacheNames[0]);
                                            asyncCacheErrorHandler.handlePutError(cache, key, result, asRuntimeException(error));
                                        }
                                    });
                                } catch (Exception e) {
                                    if (LOG.isErrorEnabled()) {
                                        LOG.error("Cache put operation failed: " + e.getMessage(), e);
                                    }
                                }
                            }
                        }, ioExecutor);
                    }
                }
            }
        }
        return returnFuture;
    }

    /**
     * Resolve the cache key generator from the give type.
     *
     * @param type The key generator
     * @return The cache key generator
     */
    protected CacheKeyGenerator resolveKeyGenerator(Class<? extends CacheKeyGenerator> type) {
        return keyGenerators.computeIfAbsent(type, aClass -> {
            if (beanContext.containsBean(aClass)) {
                return beanContext.getBean(aClass);
            }
            return InstantiationUtils.instantiate(aClass);
        });
    }

    private CompletableFuture<Void> buildPutFutures(String[] cacheNames, Object result, Object key) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String cacheName : cacheNames) {
            AsyncCache<?> asyncCache = cacheManager.getCache(cacheName).async();
            futures.add(asyncCache.put(key, result));
        }
        CompletableFuture[] futureArray = futures.toArray(new CompletableFuture[futures.size()]);
        return CompletableFuture.allOf(futureArray);
    }

    private CacheKeyGenerator resolveKeyGenerator(CacheKeyGenerator defaultKeyGenerator, Cacheable cacheConfig) {
        CacheKeyGenerator keyGenerator = defaultKeyGenerator;
        Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
        if (keyGenerator.getClass() != alternateKeyGen) {
            keyGenerator = resolveKeyGenerator(alternateKeyGen);
        }
        return keyGenerator;
    }

    private String[] resolveCacheNames(CacheConfig defaultConfig, Cacheable cacheConfig) {
        String[] cacheNames = cacheConfig.cacheNames();
        if (ArrayUtils.isEmpty(cacheNames)) {
            cacheNames = defaultConfig.cacheNames();
        }
        return cacheNames;
    }

    private void doProceed(MethodInvocationContext context, ValueWrapper wrapper) {
        Object result = context.proceed();
        if (result instanceof Optional) {
            Optional optional = (Optional) result;
            wrapper.optional = true;
            if (optional.isPresent()) {
                wrapper.value = optional.get();
            }
        } else {
            wrapper.value = result;
        }
    }

    private void processCachePut(MethodInvocationContext<?, ?> context, ValueWrapper wrapper, CachePut cacheConfig, CacheOperation cacheOperation) {
        String[] cacheNames = cacheOperation.getCacheNames(cacheConfig);
        CacheKeyGenerator keyGenerator = cacheOperation.getKeyGenerator(cacheConfig);
        String[] parameterNames = cacheConfig.parameters();
        Object[] parameterValues = resolveParams(context, parameterNames);
        boolean isAsync = cacheConfig.async();


        processCachePut(context, wrapper, cacheNames, keyGenerator, parameterValues, isAsync);
    }

    private void processCachePut(MethodInvocationContext<?, ?> context, ValueWrapper wrapper, String[] cacheNames, CacheKeyGenerator keyGenerator, Object[] parameterValues, boolean isAsync) {
        if (!ArrayUtils.isEmpty(cacheNames)) {
            Object v = wrapper.value;
            if (isAsync) {
                ioExecutor.submit(() -> {
                    try {
                        Object key = keyGenerator.generateKey(context, parameterValues);
                        for (String cacheName : cacheNames) {
                            SyncCache cache = cacheManager.getCache(cacheName);
                            AsyncCache<?> asyncCache = cache.async();
                            CompletableFuture<Boolean> putFuture = asyncCache.put(key, v);
                            putFuture.whenCompleteAsync((aBoolean, throwable) -> {
                                if (throwable != null) {
                                    asyncCacheErrorHandler.handlePutError(cache, key, v, asRuntimeException(throwable));
                                }
                            }, ioExecutor);
                        }
                    } catch (Exception e) {
                        throw new CacheSystemException("Cache put operation failed: " + e.getMessage(), e);
                    }
                });
            } else {
                Object key = keyGenerator.generateKey(context, parameterValues);
                syncPut(cacheNames, key, v);
            }
        }
    }

    private void syncPut(String[] cacheNames, Object key, Object value) {
        for (String cacheName : cacheNames) {
            SyncCache syncCache = cacheManager.getCache(cacheName);
            try {
                syncCache.put(key, value);
            } catch (RuntimeException e) {
                if (errorHandler.handlePutError(syncCache, key, value, e)) {
                    throw e;
                }
            }
        }
    }

    private void processCacheEvict(
            MethodInvocationContext context,
            CacheInvalidate cacheConfig,
            CacheOperation cacheOperation,
            boolean async) {

        String[] cacheNames = cacheOperation.getCacheNames(cacheConfig);
        CacheKeyGenerator keyGenerator = cacheOperation.getKeyGenerator(cacheConfig);
        boolean invalidateAll = cacheConfig.all();
        Object key = null;
        String[] parameterNames = cacheConfig.parameters();
        Object[] parameterValues = resolveParams(context, parameterNames);

        if (!invalidateAll) {
            Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
            if (keyGenerator.getClass() != alternateKeyGen) {
                keyGenerator = resolveKeyGenerator(alternateKeyGen);
            }
            key = keyGenerator.generateKey(context, parameterValues);
        }

        if (!ArrayUtils.isEmpty(cacheNames)) {
            for (String cacheName : cacheNames) {
                SyncCache syncCache = cacheManager.getCache(cacheName);
                if (async) {
                    AsyncCache<?> asyncCache = syncCache.async();
                    if (invalidateAll) {
                        CompletableFuture<Boolean> future = asyncCache.invalidateAll();
                        future.whenCompleteAsync((aBoolean, throwable) -> {
                            if (throwable != null) {
                                asyncCacheErrorHandler.handleInvalidateError(syncCache, asRuntimeException(throwable));
                            }
                        }, ioExecutor);
                    } else {
                        Object finalKey = key;
                        CompletableFuture<Boolean> future = asyncCache.invalidate(key);
                        future.whenCompleteAsync((aBoolean, throwable) -> {
                            if (throwable != null) {
                                asyncCacheErrorHandler.handleInvalidateError(syncCache, finalKey, asRuntimeException(throwable));
                            }
                        }, ioExecutor);
                    }
                } else {
                    if (invalidateAll) {
                        try {
                            syncCache.invalidateAll();
                        } catch (RuntimeException e) {
                            if (errorHandler.handleInvalidateError(syncCache, e)) {
                                throw e;
                            }
                        }
                    } else {
                        try {
                            syncCache.invalidate(key);
                        } catch (RuntimeException e) {
                            if (errorHandler.handleInvalidateError(syncCache, key, e)) {
                                throw e;
                            }
                        }
                    }
                }
            }
        }
    }

    private RuntimeException asRuntimeException(Throwable throwable) {
        if (throwable instanceof RuntimeException) {
            return (RuntimeException) throwable;
        } else {
            return new RuntimeException(throwable);
        }
    }

    private Object[] resolveParams(MethodInvocationContext<?, ?> context, String[] parameterNames) {
        Object[] parameterValues;
        if (ArrayUtils.isEmpty(parameterNames)) {
            parameterValues = context.getParameterValues();
        } else {
            List list = new ArrayList();
            Map<String, MutableArgumentValue<?>> parameters = context.getParameters();
            for (String name : parameterNames) {
                list.add(parameters.get(name).getValue());
            }
            parameterValues = list.toArray();
        }
        return parameterValues;
    }

    /**
     *
     */
    private class CacheOperation {
        final Class returnType;
        final MethodInvocationContext<?, ?> context;
        final CacheKeyGenerator defaultKeyGenerator;
        final CacheConfig defaultConfig;
        String cacheableCacheName;
        Cacheable cacheable;
        CachePut[] putOperations;
        CacheInvalidate[] invalidateOperations;

        CacheOperation(MethodInvocationContext<?, ?> context, Class returnType) {
            this.context = context;
            this.returnType = returnType;

            this.defaultConfig = context.getAnnotation(CacheConfig.class);
            this.defaultKeyGenerator = resolveKeyGenerator(defaultConfig.keyGenerator());
            boolean isVoid = isVoid();
            this.putOperations = isVoid ? null : putOperations(context);
            this.invalidateOperations = invalidateOperations(context);
            if (!isVoid && context.hasStereotype(Cacheable.class)) {
                Cacheable cacheable = context.getAnnotation(Cacheable.class);
                String[] names = resolveCacheNames(defaultConfig, cacheable);
                if (ArrayUtils.isNotEmpty(names)) {
                    this.cacheableCacheName = names[0];
                    this.cacheable = cacheable;
                }
            }
        }

        boolean hasWriteOperations() {
            return putOperations != null || invalidateOperations != null;
        }

        boolean isVoid() {
            return void.class == returnType;
        }

        String[] getCacheNames(CachePut cacheConfig) {
            String[] cacheNames = cacheConfig.cacheNames();
            return getCacheNames(cacheNames);
        }

        String[] getCacheNames(CacheInvalidate cacheConfig) {
            String[] cacheNames = cacheConfig.cacheNames();
            return getCacheNames(cacheNames);
        }

        CacheKeyGenerator getKeyGenerator(CacheInvalidate cacheConfig) {
            Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
            return getKeyGenerator(alternateKeyGen);
        }

        CacheKeyGenerator getKeyGenerator(CachePut cacheConfig) {
            Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
            return getKeyGenerator(alternateKeyGen);
        }

        private String[] getCacheNames(String[] cacheNames) {
            if (ArrayUtils.isEmpty(cacheNames)) {
                cacheNames = defaultConfig.cacheNames();
            }
            return cacheNames;
        }

        private CacheKeyGenerator getKeyGenerator(Class<? extends CacheKeyGenerator> alternateKeyGen) {
            CacheKeyGenerator keyGenerator = defaultKeyGenerator;
            if (defaultKeyGenerator.getClass() != alternateKeyGen) {
                keyGenerator = resolveKeyGenerator(alternateKeyGen);
            }
            return keyGenerator;
        }
    }

    /**
     * The value wrapper.
     */
    private class ValueWrapper {
        Object value;
        boolean optional;
    }
}
