/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.cache.annotation;

import io.micronaut.cache.interceptor.CacheInterceptor;
import io.micronaut.cache.interceptor.CacheKeyGenerator;
import io.micronaut.cache.interceptor.DefaultCacheKeyGenerator;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;
import io.micronaut.core.annotation.InstantiatedMember;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>An annotation that can be applied at the type or method level to indicate that the return value of the method
 * should be cached for
 * the configured {@link #cacheNames()}.</p>
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
     *
     * @return The cache names
     */
    @AliasFor(member = "cacheNames")
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] value() default {};

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     *
     * @return The cache names
     */
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] cacheNames() default {};

    /**
     * Limit the automatic {@link CacheKeyGenerator} to the given parameter names. Mutually exclusive with
     * {@link #keyGenerator()}
     *
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};

    /**
     * Alias for {@link CacheConfig#keyGenerator}.
     *
     * @return The cache key generator class
     */
    @AliasFor(annotation = CacheConfig.class, member = "keyGenerator")
    @InstantiatedMember
    Class<? extends CacheKeyGenerator> keyGenerator() default DefaultCacheKeyGenerator.class;

    /**
     * <p>Whether an atomic operation should be attempted to retrieve the cache value. This will call
     * {@link io.micronaut.cache.SyncCache#get(Object, Class, Supplier)} if set to <tt>true</tt> otherwise
     * {@link io.micronaut.cache.SyncCache#get(Object, Class)} will be called which is non-atomic</p>
     * <p>
     * <p>Note that atomic operations will pick the first cache name specified and ignore the remaining.</p>
     *
     * @return True if an atomic operation should be attempted
     */
    boolean atomic() default false;
}
