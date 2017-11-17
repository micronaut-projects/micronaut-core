/*
 * Copyright 2017 original authors
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
package org.particleframework.cache.interceptor;

import org.particleframework.aop.InterceptPhase;
import org.particleframework.aop.MethodInterceptor;
import org.particleframework.aop.MethodInvocationContext;
import org.particleframework.cache.*;
import org.particleframework.cache.annotation.*;
import org.particleframework.context.BeanContext;
import org.particleframework.core.reflect.InstantiationUtils;
import org.particleframework.core.type.MutableArgumentValue;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.runtime.executor.IOExecutorService;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * <p>An AOP {@link MethodInterceptor} implementation for the Cache annotations {@link Cacheable}, {@link CachePut} and {@link CacheInvalidate}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class CacheInterceptor implements MethodInterceptor {
    public static final int POSITION = InterceptPhase.CACHE.getPosition();
    private static final CacheInvalidate[] ZERO_CACHE_EVICTS = new CacheInvalidate[0];
    private static final CachePut[] ZERO_CACHE_PUTS = new CachePut[0];
    private final CacheManager cacheManager;
    private final Map<Class<? extends CacheKeyGenerator>, CacheKeyGenerator> keyGenerators = new ConcurrentHashMap<>();
    private final BeanContext beanContext;
    private final ExecutorService ioExecutor;
    private final CacheErrorHandler errorHandler;
    private final AsyncCacheErrorHandler asyncCacheErrorHandler;

    public CacheInterceptor(CacheManager cacheManager,
                            CacheErrorHandler errorHandler,
                            AsyncCacheErrorHandler asyncCacheErrorHandler,
                            @Named(IOExecutorService.NAME) ExecutorService ioExecutor,
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
    public Object intercept(MethodInvocationContext context) {
        if (context.hasStereotype(CacheConfig.class)) {
            Class returnType = context.getReturnType().getType();
            if (CompletableFuture.class.isAssignableFrom(returnType)) {
                // TODO: async caching
                return context.proceed();
            } else {
                return interceptSync(context, returnType);
            }
        } else {
            return context.proceed();
        }
    }

    protected Object interceptSync(MethodInvocationContext context, Class returnType) {
        final ValueWrapper wrapper = new ValueWrapper();
        CacheConfig defaultConfig = context.getAnnotation(CacheConfig.class);
        CacheKeyGenerator defaultKeyGenerator = getKeyGenerator(defaultConfig.keyGenerator());
        boolean isVoid = returnType == void.class;
        CachePut[] cachePuts = isVoid ? ZERO_CACHE_PUTS : putOperations(context);
        CacheInvalidate[] cacheInvalidates = invalidateOperations(context);

        if (!isVoid && context.hasStereotype(Cacheable.class)) {
            Cacheable cacheConfig = context.getAnnotation(Cacheable.class);
            String[] cacheNames = cacheConfig.cacheNames();
            if (ArrayUtils.isEmpty(cacheNames)) {
                cacheNames = defaultConfig.cacheNames();
            }

            if (ArrayUtils.isEmpty(cacheNames)) {
                doProceed(context, wrapper);
            } else {
                CacheKeyGenerator keyGenerator = defaultKeyGenerator;
                Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
                if (keyGenerator.getClass() != alternateKeyGen) {
                    keyGenerator = getKeyGenerator(alternateKeyGen);
                }

                SyncCache syncCache = cacheManager.getCache(cacheNames[0]);
                Object[] parameterValues = resolveParams(context, cacheConfig.parameters());
                Object key = keyGenerator.generateKey(context, parameterValues);
                try {
                    wrapper.value = syncCache.get(key, context.getReturnType().getType(), ()-> {
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
                    throw errorHandler.handleLoadError(syncCache, key, e);
                }
            }
        } else {
            doProceed(context, wrapper);
        }

        for (CachePut cachePut : cachePuts) {
            boolean async = cachePut.async();
            if (async) {
                ioExecutor.submit(() ->
                        processCachePut(context, wrapper, cachePut, defaultConfig, defaultKeyGenerator)
                );
            } else {
                processCachePut(context, wrapper, cachePut, defaultConfig, defaultKeyGenerator);
            }
        }

        for (CacheInvalidate cacheInvalidate : cacheInvalidates) {
            boolean async = cacheInvalidate.async();
            if (async) {
                ioExecutor.submit(() ->
                        processCacheEvict(context, defaultConfig, defaultKeyGenerator, cacheInvalidate, async)
                );
            } else {
                processCacheEvict(context, defaultConfig, defaultKeyGenerator, cacheInvalidate, async);
            }
        }

        return wrapper.optional ? Optional.ofNullable(wrapper.value) : wrapper.value;
    }

    private void doProceed(MethodInvocationContext context, ValueWrapper wrapper) {
        Object result = context.proceed();
        if(result instanceof Optional) {
            Optional optional = (Optional) result;
            wrapper.optional = true;
            if(optional.isPresent()) {
                wrapper.value = optional.get();
            }
        }
        else {
            wrapper.value = result;
        }
    }

    CachePut[] putOperations(MethodInvocationContext context) {
        if (context.hasStereotype(CachePut.class)) {
            return new CachePut[]{context.getAnnotation(CachePut.class)};
        } else if (context.hasStereotype(PutOperations.class)) {
            return context.getAnnotation(PutOperations.class).value();
        } else {
            return ZERO_CACHE_PUTS;
        }
    }

    CacheInvalidate[] invalidateOperations(MethodInvocationContext context) {
        if (context.hasStereotype(CacheInvalidate.class)) {
            return new CacheInvalidate[]{context.getAnnotation(CacheInvalidate.class)};
        } else if (context.hasStereotype(InvalidateOperations.class)) {
            return context.getAnnotation(InvalidateOperations.class).value();
        } else {
            return ZERO_CACHE_EVICTS;
        }
    }


    protected CacheKeyGenerator getKeyGenerator(Class<? extends CacheKeyGenerator> type) {
        return keyGenerators.computeIfAbsent(type, aClass -> {
            if (beanContext.containsBean(aClass)) {
                return beanContext.getBean(aClass);
            }
            return InstantiationUtils.instantiate(aClass);
        });
    }

    private void processCachePut(MethodInvocationContext<?, ?> context, ValueWrapper wrapper, CachePut cacheConfig, CacheConfig defaultConfig, CacheKeyGenerator keyGenerator) {
        String[] cacheNames = cacheConfig.cacheNames();
        if (ArrayUtils.isEmpty(cacheNames)) {
            cacheNames = defaultConfig.cacheNames();
        }
        Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
        if (keyGenerator.getClass() != alternateKeyGen) {
            keyGenerator = getKeyGenerator(alternateKeyGen);
        }
        String[] parameterNames = cacheConfig.parameters();
        Object[] parameterValues = resolveParams(context, parameterNames);


        if (!ArrayUtils.isEmpty(cacheNames)) {
            Object v = wrapper.value;
            if (cacheConfig.async()) {
                CacheKeyGenerator finalKeyGenerator = keyGenerator;
                String[] finalCacheNames = cacheNames;
                ioExecutor.submit(() -> {
                    Object key = finalKeyGenerator.generateKey(context, parameterValues);
                    for (String cacheName : finalCacheNames) {
                        SyncCache cache = cacheManager.getCache(cacheName);
                        AsyncCache<?> asyncCache = cache.async();
                        asyncCache.put(key, v).exceptionally(throwable -> {
                                    asyncCacheErrorHandler.handlePutError(cache, key, v, asRuntimeException(throwable));
                                    return false;
                                }
                        );
                    }
                });
            } else {
                Object key = keyGenerator.generateKey(context, parameterValues);
                for (String cacheName : cacheNames) {
                    SyncCache syncCache = cacheManager.getCache(cacheName);
                    try {
                        syncCache.put(key, v);
                    } catch (RuntimeException e) {
                        errorHandler.handlePutError(syncCache, key, v, e);
                    }
                }
            }
        }
    }

    private void processCacheEvict(
            MethodInvocationContext context,
            CacheConfig defaultConfig,
            CacheKeyGenerator defaultKeyGenerator,
            CacheInvalidate cacheConfig,
            boolean async) {
        String[] cacheNames = cacheConfig.cacheNames();
        if (ArrayUtils.isEmpty(cacheNames)) {
            cacheNames = defaultConfig.cacheNames();
        }
        boolean invalidateAll = cacheConfig.all();
        CacheKeyGenerator keyGenerator = defaultKeyGenerator;
        Object key = null;
        String[] parameterNames = cacheConfig.parameters();
        Object[] parameterValues = resolveParams(context, parameterNames);

        if (!invalidateAll) {
            Class<? extends CacheKeyGenerator> alternateKeyGen = cacheConfig.keyGenerator();
            if (keyGenerator.getClass() != alternateKeyGen) {
                keyGenerator = getKeyGenerator(alternateKeyGen);
            }
            key = keyGenerator.generateKey(context, parameterValues);
        }

        if (!ArrayUtils.isEmpty(cacheNames)) {
            for (String cacheName : cacheNames) {
                SyncCache syncCache = cacheManager.getCache(cacheName);
                if (async) {
                    AsyncCache<?> asyncCache = syncCache.async();
                    if (invalidateAll) {
                        asyncCache.invalidateAll().exceptionally(throwable -> {
                                    asyncCacheErrorHandler.handleInvalidateError(syncCache, asRuntimeException(throwable));
                                    return null;

                                }
                        );
                    } else {
                        Object finalKey = key;
                        asyncCache.invalidate(key).exceptionally(throwable -> {
                                    asyncCacheErrorHandler.handleInvalidateError(syncCache, finalKey, asRuntimeException(throwable));
                                    return null;
                                }
                        );
                    }
                } else {
                    if (invalidateAll) {
                        try {
                            syncCache.invalidateAll();
                        } catch (RuntimeException e) {
                            errorHandler.handleInvalidateError(syncCache, e);
                        }
                    } else {
                        try {
                            syncCache.invalidate(key);
                        } catch (RuntimeException e) {
                            errorHandler.handleInvalidateError(syncCache, key, e);
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

    private class ValueWrapper {
        Object value;
        boolean optional;

    }
}
