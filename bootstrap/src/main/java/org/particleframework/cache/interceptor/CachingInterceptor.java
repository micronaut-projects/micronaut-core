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
import org.particleframework.cache.AsyncCache;
import org.particleframework.cache.Cache;
import org.particleframework.cache.CacheManager;
import org.particleframework.cache.SyncCache;
import org.particleframework.cache.annotation.CacheConfig;
import org.particleframework.cache.annotation.CacheEvict;
import org.particleframework.cache.annotation.CachePut;
import org.particleframework.cache.annotation.Cacheable;
import org.particleframework.context.BeanContext;
import org.particleframework.core.reflect.InstantiationUtils;
import org.particleframework.core.util.ArrayUtils;
import org.particleframework.runtime.executor.IOExecutorService;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * <p>An AOP {@link MethodInterceptor} implementation for the Cache annotations {@link Cacheable}, {@link CachePut} and {@link CacheEvict}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class CachingInterceptor implements MethodInterceptor {
    public static final int POSITION = InterceptPhase.CACHE.getPosition();
    private static final String ATTRIBUTE_KEY_GENERATOR = "keyGenerator";
    private final CacheManager cacheManager;
    private final Map<Class<? extends CacheKeyGenerator>, CacheKeyGenerator> keyGenerators = new ConcurrentHashMap<>();
    private final BeanContext beanContext;
    private final ExecutorService ioExecutor;

    public CachingInterceptor(CacheManager cacheManager,
                              @Named(IOExecutorService.NAME) ExecutorService ioExecutor,
                              BeanContext beanContext) {
        this.cacheManager = cacheManager;
        this.beanContext = beanContext;
        this.ioExecutor = ioExecutor;
    }

    @Override
    public int getOrder() {
        return POSITION;
    }

    @Override
    public Object intercept(MethodInvocationContext context) {
        if(context.hasStereotype(CacheConfig.class)) {
            if(CompletableFuture.class.isAssignableFrom(context.getReturnType().getType())) {
                // TODO: async caching
                return context.proceed();
            }
            else {
                return interceptSync(context);
            }
        }
        else {
            return context.proceed();
        }
    }

    protected Object interceptSync(MethodInvocationContext context) {
        Object result;
        CacheConfig defaultConfig = context.getAnnotation(CacheConfig.class);
        CacheKeyGenerator defaultKeyGenerator = getKeyGenerator(defaultConfig.keyGenerator());
        if(context.hasStereotype(CachePut.class)) {
            CachePut cacheConfig = context.getAnnotation(CachePut.class);
            String[] cacheNames = resolveCachePutCacheNames(context, defaultConfig, cacheConfig);
            CacheKeyGenerator keyGenerator = defaultKeyGenerator;
            if(context.isPresent(CachePut.class, ATTRIBUTE_KEY_GENERATOR)) {
                keyGenerator = getKeyGenerator(cacheConfig.keyGenerator());
            }
            result = context.proceed();
            processCachePut(context, result, cacheConfig, cacheNames, keyGenerator);
        }
        else if(context.hasStereotype(Cacheable.class)) {
            Cacheable cacheConfig = context.getAnnotation(Cacheable.class);
            String[] cacheNames = context.isPresent(Cacheable.class, "cacheNames") ? cacheConfig.cacheNames() : defaultConfig.cacheNames();
            if(ArrayUtils.isEmpty(cacheNames)) {
                result = context.proceed();
            }
            else {
                CacheKeyGenerator keyGenerator = defaultKeyGenerator;
                if(context.isPresent(Cacheable.class, ATTRIBUTE_KEY_GENERATOR)) {
                    keyGenerator = getKeyGenerator(cacheConfig.keyGenerator());
                }

                SyncCache syncCache = cacheManager.getCache(cacheNames[0]);
                Object key = keyGenerator.generateKey(context);
                result = syncCache.get(key, context.getReturnType().getType(), context::proceed);
            }
        }
        else {
            result = context.proceed();
        }

        if(context.hasStereotype(CacheEvict.class)) {
            CacheEvict cacheConfig = context.getAnnotation(CacheEvict.class);
            boolean async = cacheConfig.async();
            if(async) {
                ioExecutor.submit(() ->
                        processCacheInvalidate(context, defaultConfig, defaultKeyGenerator, cacheConfig, async)
                );
            }
            else {
                processCacheInvalidate(context, defaultConfig, defaultKeyGenerator, cacheConfig, async);
            }

        }
        return result;
    }

    protected String[] resolveCachePutCacheNames(MethodInvocationContext context, CacheConfig defaultConfig, CachePut cacheConfig) {
        return context.isPresent(CachePut.class, "cacheNames") ? cacheConfig.cacheNames() : defaultConfig.cacheNames();
    }

    protected CacheKeyGenerator getKeyGenerator(Class<? extends CacheKeyGenerator> type) {
        return keyGenerators.computeIfAbsent(type, aClass -> {
                    if(beanContext.containsBean(aClass)) {
                        return beanContext.getBean(aClass);
                    }
                    return InstantiationUtils.instantiate(aClass);
                });
    }

    private void processCachePut(MethodInvocationContext context, Object result, CachePut cacheConfig, String[] cacheNames, CacheKeyGenerator keyGenerator) {
        if(!ArrayUtils.isEmpty(cacheNames)) {
            if(cacheConfig.async()) {
                CacheKeyGenerator finalKeyGenerator = keyGenerator;
                ioExecutor.submit(() -> {
                    Object key = finalKeyGenerator.generateKey(context);
                    for (String cacheName : cacheNames) {
                        AsyncCache asyncCache = cacheManager.getCache(cacheName).async();
                        asyncCache.put(key, result);
                    }
                });
            }
            else {
                Object key = keyGenerator.generateKey(context);
                for (String cacheName : cacheNames) {
                    SyncCache syncCache = cacheManager.getCache(cacheName);
                    syncCache.put(key, result);
                }
            }
        }
    }

    private void processCacheInvalidate(MethodInvocationContext context, CacheConfig defaultConfig, CacheKeyGenerator defaultKeyGenerator, CacheEvict cacheConfig, boolean async) {
        String[] cacheNames = context.isPresent(CacheEvict.class, "cacheNames") ? cacheConfig.cacheNames() : defaultConfig.cacheNames();
        boolean invalidateAll = cacheConfig.all();
        CacheKeyGenerator keyGenerator = defaultKeyGenerator;
        Object key = null;
        if(!invalidateAll) {
            if(context.isPresent(CacheEvict.class, ATTRIBUTE_KEY_GENERATOR)) {
                keyGenerator = getKeyGenerator(cacheConfig.keyGenerator());
            }
            key = keyGenerator.generateKey(context);
        }

        if(!ArrayUtils.isEmpty(cacheNames)) {

            for (String cacheName : cacheNames) {
                Cache syncCache = async ? cacheManager.getCache(cacheName).async() : cacheManager.getCache(cacheName);

                if(invalidateAll) {
                    syncCache.invalidateAll();
                }
                else {
                    syncCache.invalidate(key);
                }
            }
        }
    }
}
