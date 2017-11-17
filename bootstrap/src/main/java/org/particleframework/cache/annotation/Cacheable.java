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
package org.particleframework.cache.annotation;

import org.particleframework.cache.interceptor.CacheInterceptor;
import org.particleframework.cache.interceptor.DefaultCacheKeyGenerator;
import org.particleframework.cache.interceptor.CacheKeyGenerator;
import org.particleframework.context.annotation.AliasFor;
import org.particleframework.context.annotation.Type;

import java.lang.annotation.*;
import java.util.function.Supplier;

/**
 * <p>An annotation that can be applied at the type or method level to indicate that the return value of the method should be cached for
 * the configured {@link #cacheNames()}</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@CacheConfig
@Type(CacheInterceptor.class)
public @interface Cacheable {

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     */
    @AliasFor(member = "cacheNames")
    String[] value() default {};

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     */
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] cacheNames() default {};

    /**
     * Limit the automatic {@link CacheKeyGenerator} to the given parameter names. Mutually exclusive with {@link #keyGenerator()}
     *
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};
    /**
     * Alias for {@link CacheConfig#keyGenerator}.
     */
    @AliasFor(annotation = CacheConfig.class, member = "keyGenerator")
    Class<? extends CacheKeyGenerator> keyGenerator() default DefaultCacheKeyGenerator.class;

    /**
     * <p>Whether an atomic operation should be attempted to retrieve the cache value. This will call
     * {@link org.particleframework.cache.SyncCache#get(Object, Class, Supplier)} if set to <tt>true</tt> otherwise
     * {@link org.particleframework.cache.SyncCache#get(Object, Class)} will be called which is non-atomic</p>
     *
     * <p>Note that atomic operations will pick the first cache name specified and ignore the remaining.</p>
     *
     * @return True if an atomic operation should be attempted
     */
    boolean atomic() default false;
}
