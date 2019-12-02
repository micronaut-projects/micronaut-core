/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.cache.*;
import io.micronaut.cache.annotation.CacheConfig;
import io.micronaut.cache.annotation.CacheInvalidate;
import io.micronaut.cache.annotation.CachePut;
import io.micronaut.cache.annotation.Cacheable;
import io.micronaut.cache.exceptions.CacheSystemException;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.async.annotation.SingleResult;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.MutableArgumentValue;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.TaskExecutors;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Maybe;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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

    private static final String MEMBER_CACHE_NAMES = "cacheNames";
    private static final String MEMBER_ASYNC = "async";
    private static final Logger LOG = LoggerFactory.getLogger(CacheInterceptor.class);
    private static final String MEMBER_ATOMIC = "atomic";
    private static final String MEMBER_PARAMETERS = "parameters";
    private static final String MEMBER_ALL = "all";
    private static final String MEMBER_KEY_GENERATOR = "keyGenerator";

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
            if (CompletionStage.class.isAssignableFrom(returnType)) {
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

        AnnotationValue<Cacheable> cacheConfig = cacheOperation.cacheable;
        if (cacheConfig != null) {
            CacheKeyGenerator defaultKeyGenerator = cacheOperation.defaultKeyGenerator;
            CacheKeyGenerator keyGenerator = resolveKeyGenerator(defaultKeyGenerator, cacheConfig);
            Object[] parameterValues = resolveParams(context, cacheConfig.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
            Object key = keyGenerator.generateKey(context, parameterValues);
            Argument returnArgument = returnTypeObject.asArgument();
            if (cacheConfig.getRequiredValue(MEMBER_ATOMIC, Boolean.class)) {
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

        List<AnnotationValue<CachePut>> cachePuts = cacheOperation.putOperations;
        if (cachePuts != null) {

            for (AnnotationValue<CachePut> cachePut : cachePuts) {
                boolean async = cachePut.get(MEMBER_ASYNC, Boolean.class, false);
                if (async) {
                    ioExecutor.submit(() ->
                            processCachePut(context, wrapper, cachePut, cacheOperation)
                    );
                } else {
                    processCachePut(context, wrapper, cachePut, cacheOperation);
                }
            }
        }

        List<AnnotationValue<CacheInvalidate>> cacheInvalidates = cacheOperation.invalidateOperations;
        if (cacheInvalidates != null) {
            for (AnnotationValue<CacheInvalidate> cacheInvalidate : cacheInvalidates) {
                boolean async = cacheInvalidate.get(MEMBER_ASYNC, Boolean.class, false);
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
        AnnotationValue<Cacheable> cacheable = cacheOperation.cacheable;
        CompletableFuture<Object> returnFuture;
        if (cacheable != null) {
            AsyncCache<?> asyncCache = cacheManager.getCache(cacheOperation.cacheableCacheName).async();
            CacheKeyGenerator keyGenerator = resolveKeyGenerator(cacheOperation.defaultKeyGenerator, cacheable);
            Object[] params = resolveParams(context, cacheable.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
            Object key = keyGenerator.generateKey(context, params);
            CompletableFuture<Object> thisFuture = new CompletableFuture<>();
            Argument<?> firstTypeVariable = returnTypeObject.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);
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
                                    BiConsumer<Boolean, Throwable> completionHandler = (aBoolean, throwable1) -> {
                                        if (throwable1 == null) {
                                            thisFuture.complete(o1);
                                        } else {
                                            thisFuture.completeExceptionally(throwable1);
                                        }
                                    };
                                    if (o1 != null) {
                                        asyncCache.put(key, o1).whenComplete(completionHandler);
                                    } else {
                                        asyncCache.invalidate(key).whenComplete(completionHandler);
                                    }

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
    protected List<AnnotationValue<CachePut>> putOperations(MethodInvocationContext context) {
        return context.getAnnotationValuesByType(CachePut.class);
    }

    /**
     * Evict from the cache.
     *
     * @param context Extended version of {@link io.micronaut.aop.InvocationContext} for {@link MethodInterceptor} instances
     * @return The operations to cause the eviction of the given caches
     */
    protected List<AnnotationValue<CacheInvalidate>> invalidateOperations(MethodInvocationContext context) {
        return context.getAnnotationValuesByType(CacheInvalidate.class);
    }

    private Object interceptPublisher(MethodInvocationContext<Object, Object> context, ReturnType returnTypeObject, Class returnType) {
        if (!Publishers.isSingle(returnType) && !context.isAnnotationPresent(SingleResult.class)) {
            throw new CacheSystemException("Only Reactive types that emit a single result can currently be cached. Use either Single, Maybe or Mono for operations that cache.");
        }
        CacheOperation cacheOperation = new CacheOperation(context, returnType);
        AnnotationValue<Cacheable> cacheable = cacheOperation.cacheable;
        if (cacheable != null) {

            Publisher<Object> publisher = buildCacheablePublisher(context, returnTypeObject, cacheOperation, cacheable);
            return Publishers.convertPublisher(publisher, returnType);
        } else {
            final List<AnnotationValue<CachePut>> putOperations = cacheOperation.putOperations;
            if (CollectionUtils.isNotEmpty(putOperations)) {
                final Publisher<Object> publisher = buildCachePutPublisher(context, cacheOperation, putOperations);
                return Publishers.convertPublisher(publisher, returnType);
            } else {
                final List<AnnotationValue<CacheInvalidate>> invalidateOperations = cacheOperation.invalidateOperations;
                if (CollectionUtils.isNotEmpty(invalidateOperations)) {
                    final Publisher<Object> publisher = buildCacheInvalidatePublisher(context, cacheOperation, invalidateOperations);
                    return Publishers.convertPublisher(publisher, returnType);
                } else {
                    return context.proceed();
                }
            }
        }
    }

    private Publisher<Object> buildCacheInvalidatePublisher(
            MethodInvocationContext<Object, Object> context,
            CacheOperation cacheOperation,
            List<AnnotationValue<CacheInvalidate>> invalidateOperations) {
        final Flowable<Object> originalFlowable = Publishers.convertPublisher(context.proceed(), Flowable.class);

        return originalFlowable.flatMap((o) -> {
            List<Flowable<?>> cacheInvalidates = new ArrayList<>();
            for (AnnotationValue<CacheInvalidate> invalidateOperation : invalidateOperations) {
                String[] cacheNames = cacheOperation.getCacheInvalidateNames(invalidateOperation);

                if (ArrayUtils.isNotEmpty(cacheNames)) {
                    boolean invalidateAll = invalidateOperation.getRequiredValue(MEMBER_ALL, Boolean.class);
                    boolean isAsync = invalidateOperation.get(MEMBER_ASYNC, Boolean.class, false);
                    if (isAsync) {
                        if (invalidateAll) {
                            for (String cacheName : cacheNames) {
                                AsyncCache<?> asyncCache = cacheManager.getCache(cacheName).async();
                                asyncCache.invalidateAll().whenCompleteAsync((aBoolean, throwable) -> {
                                    if (throwable != null) {
                                        asyncCacheErrorHandler.handleInvalidateError(asyncCache, asRuntimeException(throwable));
                                    }
                                }, ioExecutor);
                            }
                        } else {
                            CacheKeyGenerator keyGenerator = cacheOperation.getCacheInvalidateKeyGenerator(invalidateOperation);
                            String[] parameterNames = invalidateOperation.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY);
                            Object[] parameterValues = resolveParams(context, parameterNames);
                            Object key = keyGenerator.generateKey(context, parameterValues);
                            for (String cacheName : cacheNames) {
                                AsyncCache<?> asyncCache = cacheManager.getCache(cacheName).async();
                                asyncCache.invalidate(key).whenCompleteAsync((aBoolean, throwable) -> {
                                    if (throwable != null) {
                                        asyncCacheErrorHandler.handleInvalidateError(asyncCache, asRuntimeException(throwable));
                                    }
                                }, ioExecutor);
                            }
                        }
                    } else {

                        final Flowable<Object> cacheInvalidateFlowable = Flowable.create(emitter -> {
                            if (invalidateAll) {
                                final CompletableFuture<Void> allFutures = buildInvalidateAllFutures(cacheNames);
                                allFutures.whenCompleteAsync((aBoolean, throwable) -> {
                                    if (throwable != null) {
                                        SyncCache cache = cacheManager.getCache(cacheNames[0]);
                                        if (asyncCacheErrorHandler.handleInvalidateError(cache, asRuntimeException(throwable))) {
                                            emitter.onError(throwable);
                                            return;
                                        }
                                        emitter.onNext(true);
                                        emitter.onComplete();
                                    } else {
                                        emitter.onNext(o);
                                        emitter.onComplete();
                                    }
                                }, ioExecutor);
                            } else {
                                CacheKeyGenerator keyGenerator = cacheOperation.getCacheInvalidateKeyGenerator(invalidateOperation);
                                String[] parameterNames = invalidateOperation.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY);
                                Object[] parameterValues = resolveParams(context, parameterNames);
                                Object key = keyGenerator.generateKey(context, parameterValues);
                                final CompletableFuture<Void> allFutures = buildInvalidateFutures(cacheNames, key);
                                allFutures.whenCompleteAsync((aBoolean, throwable) -> {
                                    if (throwable != null) {
                                        SyncCache cache = cacheManager.getCache(cacheNames[0]);
                                        if (asyncCacheErrorHandler.handleInvalidateError(cache, key, asRuntimeException(throwable))) {
                                            emitter.onError(throwable);
                                            return;
                                        }
                                    }
                                    emitter.onNext(o);
                                    emitter.onComplete();
                                }, ioExecutor);
                            }
                        }, BackpressureStrategy.ERROR);
                        cacheInvalidates.add(cacheInvalidateFlowable);
                    }
                }
            }
            if (!cacheInvalidates.isEmpty()) {
                return Flowable.merge(cacheInvalidates).lastOrError().toFlowable();
            } else {
                return Flowable.just(o);
            }
        });
    }

    private Publisher<Object> buildCachePutPublisher(
            MethodInvocationContext<Object, Object> context,
            CacheOperation cacheOperation,
            List<AnnotationValue<CachePut>> putOperations) {
        final Flowable<?> originalFlowable = Publishers.convertPublisher(context.proceed(), Flowable.class);
        return originalFlowable.flatMap((Function<Object, Publisher<?>>) o -> {
            List<Flowable<?>> cachePuts = new ArrayList<>();
            for (AnnotationValue<CachePut> putOperation : putOperations) {
                String[] cacheNames = cacheOperation.getCachePutNames(putOperation);

                if (ArrayUtils.isNotEmpty(cacheNames)) {
                    boolean isAsync = putOperation.get(MEMBER_ASYNC, Boolean.class, false);
                    if (isAsync) {
                        putResultAsync(context, cacheOperation, putOperation, cacheNames, o);
                    } else {
                        final Flowable<Object> cachePutFlowable = Flowable.create(emitter -> {
                            CacheKeyGenerator keyGenerator = cacheOperation.getCachePutKeyGenerator(putOperation);
                            Object[] parameterValues = resolveParams(context, putOperation.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
                            Object key = keyGenerator.generateKey(context, parameterValues);
                            CompletableFuture<Void> putOperationFuture = buildPutFutures(cacheNames, o, key);
                            putOperationFuture.whenComplete((aVoid, throwable) -> {
                                if (throwable == null) {
                                    emitter.onNext(o);
                                    emitter.onComplete();
                                } else {
                                    SyncCache cache = cacheManager.getCache(cacheNames[0]);
                                    if (errorHandler.handlePutError(cache, key, o, asRuntimeException(throwable))) {
                                        emitter.onError(throwable);
                                    } else {
                                        emitter.onNext(o);
                                        emitter.onComplete();
                                    }
                                }
                            });
                        }, BackpressureStrategy.ERROR);
                        cachePuts.add(cachePutFlowable);
                    }
                }
            }

            if (!cachePuts.isEmpty()) {
                return Flowable.merge(cachePuts).lastOrError().toFlowable();
            } else {
                return Flowable.just(o);
            }
        });
    }

    private Publisher<Object> buildCacheablePublisher(
            MethodInvocationContext<Object, Object> context,
            ReturnType returnTypeObject,
            CacheOperation cacheOperation,
            AnnotationValue<Cacheable> cacheable) {
        AsyncCache<?> asyncCache = cacheManager.getCache(cacheOperation.cacheableCacheName).async();
        CacheKeyGenerator keyGenerator = resolveKeyGenerator(cacheOperation.defaultKeyGenerator, cacheable);
        Object[] params = resolveParams(context, cacheable.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
        Object key = keyGenerator.generateKey(context, params);
        Argument<?> firstTypeVariable = returnTypeObject.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT);

        Maybe<Object> maybe = Maybe.create(emitter -> {
            asyncCache.get(key, firstTypeVariable).whenComplete((opt, throwable) -> {
               if (throwable != null) {
                   if (errorHandler.handleLoadError(asyncCache, key, asRuntimeException(throwable))) {
                       emitter.onError(throwable);
                   } else {
                       emitter.onComplete();
                   }
                   emitter.onError(throwable);
               } else if (opt.isPresent()) {
                   if (LOG.isDebugEnabled()) {
                       LOG.debug("Value found in cache [" + asyncCache.getName() + "] for invocation: " + context);
                   }
                   emitter.onSuccess(opt.get());
               } else {
                   emitter.onComplete();
               }
            });
        });

        return maybe.isEmpty().flatMapPublisher(empty -> {
            if (empty) {
               return Publishers.convertPublisher(
                       context.proceed(), Flowable.class)
                       .flatMap(o -> {
                           return Single.create(emitter -> {
                               BiConsumer<Boolean, Throwable> completionHandler = (aBoolean, throwable1) -> {
                                   if (throwable1 == null) {
                                       emitter.onSuccess(o);
                                   } else {
                                       if (errorHandler.handleLoadError(asyncCache, key, asRuntimeException(throwable1))) {

                                           emitter.onError(throwable1);
                                       } else {
                                           emitter.onSuccess(o);
                                       }
                                   }
                               };
                               if (o != null) {
                                   asyncCache.put(key, o).whenComplete(completionHandler);
                               } else {
                                   asyncCache.invalidate(key).whenComplete(completionHandler);
                               }
                           }).toFlowable();
                       });
            } else {
                return maybe.toFlowable();
            }
        });
    }

    private CompletableFuture<Object> processFuturePutOperations(MethodInvocationContext<Object, Object> context, CacheOperation cacheOperation, CompletableFuture<Object> returnFuture) {
        List<AnnotationValue<CachePut>> putOperations = cacheOperation.putOperations;
        if (putOperations != null) {
            for (AnnotationValue<CachePut> putOperation : putOperations) {
                String[] cacheNames = cacheOperation.getCachePutNames(putOperation);

                if (ArrayUtils.isNotEmpty(cacheNames)) {
                    boolean isAsync = putOperation.get(MEMBER_ASYNC, Boolean.class, false);
                    if (!isAsync) {
                        CompletableFuture<Object> newFuture = new CompletableFuture<>();
                        returnFuture.whenComplete((result, throwable) -> {
                            if (throwable == null) {
                                try {
                                    CacheKeyGenerator keyGenerator = cacheOperation.getCachePutKeyGenerator(putOperation);
                                    Object[] parameterValues = resolveParams(context, putOperation.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
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
                                putResultAsync(context, cacheOperation, putOperation, cacheNames, result);
                            }
                        }, ioExecutor);
                    }
                }
            }
        }
        return returnFuture;
    }

    private void putResultAsync(MethodInvocationContext<Object, Object> context, CacheOperation cacheOperation, AnnotationValue<CachePut> putOperation, String[] cacheNames, Object result) {
        try {
            CacheKeyGenerator keyGenerator = cacheOperation.getCachePutKeyGenerator(putOperation);
            Object[] parameterValues = resolveParams(context, putOperation.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY));
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

    /**
     * Resolve the cache key generator from the give type.
     *
     * @param type The key generator
     * @return The cache key generator
     */
    protected CacheKeyGenerator resolveKeyGenerator(Class<? extends CacheKeyGenerator> type) {
        if (type == null) {
            type = DefaultCacheKeyGenerator.class;
        }

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
            if (result != null) {
                futures.add(asyncCache.put(key, result));
            } else {
                futures.add(asyncCache.invalidate(key));
            }
        }
        CompletableFuture[] futureArray = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futureArray);
    }

    private CompletableFuture<Void> buildInvalidateFutures(String[] cacheNames, Object key) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String cacheName : cacheNames) {
            AsyncCache<?> asyncCache = cacheManager.getCache(cacheName).async();
            futures.add(asyncCache.invalidate(key));
        }
        CompletableFuture[] futureArray = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futureArray);
    }

    private CompletableFuture<Void> buildInvalidateAllFutures(String[] cacheNames) {
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (String cacheName : cacheNames) {
            AsyncCache<?> asyncCache = cacheManager.getCache(cacheName).async();
            futures.add(asyncCache.invalidateAll());
        }
        CompletableFuture[] futureArray = futures.toArray(new CompletableFuture[0]);
        return CompletableFuture.allOf(futureArray);
    }

    private CacheKeyGenerator resolveKeyGenerator(CacheKeyGenerator defaultKeyGenerator, AnnotationValue<Cacheable> cacheConfig) {
        CacheKeyGenerator keyGenerator = defaultKeyGenerator;
        final CacheKeyGenerator altKeyGenInstance = cacheConfig.get(MEMBER_KEY_GENERATOR, CacheKeyGenerator.class).orElse(null);
        if (altKeyGenInstance != null) {
            return altKeyGenInstance;
        } else {
            Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.get(MEMBER_KEY_GENERATOR, Class.class).orElse(null);
            if (alternateKeyGen != null && keyGenerator.getClass() != alternateKeyGen) {
                keyGenerator = resolveKeyGenerator(alternateKeyGen);
            }
            if (keyGenerator == null) {
                return new DefaultCacheKeyGenerator();
            }
            return keyGenerator;
        }

    }

    private String[] resolveCacheNames(AnnotationValue<CacheConfig> defaultConfig, AnnotationValue<Cacheable> cacheConfig) {
        String[] cacheNames = cacheConfig.get(MEMBER_CACHE_NAMES, String[].class).orElse(null);
        if (ArrayUtils.isEmpty(cacheNames)) {
            cacheNames = defaultConfig.get(MEMBER_CACHE_NAMES, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
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

    private void processCachePut(MethodInvocationContext<?, ?> context, ValueWrapper wrapper, AnnotationValue<CachePut> cacheConfig, CacheOperation cacheOperation) {
        String[] cacheNames = cacheOperation.getCachePutNames(cacheConfig);
        CacheKeyGenerator keyGenerator = cacheOperation.getCachePutKeyGenerator(cacheConfig);
        String[] parameterNames = cacheConfig.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY);
        Object[] parameterValues = resolveParams(context, parameterNames);
        boolean isAsync = cacheConfig.get(MEMBER_ASYNC, Boolean.class, false);


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
                            CompletableFuture<Boolean> putOrInvalidateFuture = v != null ? asyncCache.put(key, v) : asyncCache.invalidate(key);
                            putOrInvalidateFuture.whenCompleteAsync((aBoolean, throwable) -> {
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
                if (value != null) {
                    syncCache.put(key, value);
                } else {
                    syncCache.invalidate(key);
                }
            } catch (RuntimeException e) {
                if (errorHandler.handlePutError(syncCache, key, value, e)) {
                    throw e;
                }
            }
        }
    }

    private void processCacheEvict(
            MethodInvocationContext context,
            AnnotationValue<CacheInvalidate> cacheConfig,
            CacheOperation cacheOperation,
            boolean async) {

        String[] cacheNames = cacheOperation.getCacheInvalidateNames(cacheConfig);
        CacheKeyGenerator keyGenerator = cacheOperation.getCacheInvalidateKeyGenerator(cacheConfig);
        boolean invalidateAll = cacheConfig.getRequiredValue(MEMBER_ALL, Boolean.class);
        Object key = null;
        String[] parameterNames = cacheConfig.get(MEMBER_PARAMETERS, String[].class, StringUtils.EMPTY_STRING_ARRAY);
        Object[] parameterValues = resolveParams(context, parameterNames);

        if (!invalidateAll) {
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
        final AnnotationValue<CacheConfig> defaultConfig;
        String cacheableCacheName;
        AnnotationValue<Cacheable> cacheable;
        List<AnnotationValue<CachePut>> putOperations;
        List<AnnotationValue<CacheInvalidate>> invalidateOperations;

        CacheOperation(MethodInvocationContext<?, ?> context, Class returnType) {
            this.context = context;
            this.returnType = returnType;

            this.defaultConfig = context.getAnnotation(CacheConfig.class);
            this.defaultKeyGenerator = resolveKeyGenerator(defaultConfig.get(MEMBER_KEY_GENERATOR, Class.class).orElse(null));
            boolean isVoid = isVoid();
            this.putOperations = isVoid ? null : putOperations(context);
            this.invalidateOperations = invalidateOperations(context);
            if (!isVoid && context.hasStereotype(Cacheable.class)) {
                AnnotationValue<Cacheable> cacheable = context.getAnnotation(Cacheable.class);
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

        String[] getCachePutNames(AnnotationValue<CachePut> cacheConfig) {
            return getCacheNames(cacheConfig.get(MEMBER_CACHE_NAMES, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY));
        }

        String[] getCacheInvalidateNames(AnnotationValue<CacheInvalidate> cacheConfig) {
            return getCacheNames(cacheConfig.get(MEMBER_CACHE_NAMES, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY));
        }

        CacheKeyGenerator getCacheInvalidateKeyGenerator(AnnotationValue<CacheInvalidate> cacheConfig) {
            return cacheConfig.get(MEMBER_KEY_GENERATOR, CacheKeyGenerator.class).orElseGet(() ->
                getKeyGenerator(cacheConfig.get(MEMBER_KEY_GENERATOR, Class.class).orElse(null))
            );
        }

        CacheKeyGenerator getCachePutKeyGenerator(AnnotationValue<CachePut> cacheConfig) {
            return cacheConfig.get(MEMBER_KEY_GENERATOR, CacheKeyGenerator.class).orElseGet(() ->
                getKeyGenerator(cacheConfig.get(MEMBER_KEY_GENERATOR, Class.class).orElse(null))
            );
        }

        private String[] getCacheNames(String[] cacheNames) {
            if (ArrayUtils.isEmpty(cacheNames)) {
                return defaultConfig.get(MEMBER_CACHE_NAMES, String[].class).orElse(StringUtils.EMPTY_STRING_ARRAY);
            } else {
                return cacheNames;
            }
        }

        private CacheKeyGenerator getKeyGenerator(Class<? extends CacheKeyGenerator> alternateKeyGen) {
            CacheKeyGenerator keyGenerator = defaultKeyGenerator;
            if (alternateKeyGen != null && defaultKeyGenerator.getClass() != alternateKeyGen) {
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
