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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>An annotation that can be applied at the type or method level to indicate that the annotated operation should
 * cause the eviction of the given caches. </p>
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
@Repeatable(InvalidateOperations.class)
public @interface CacheInvalidate {
    /**
     * Alias for {@link CacheConfig#cacheNames}.
     *
     * @return Cache names
     */
    @AliasFor(member = "cacheNames")
    String[] value() default {};

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     *
     * @return Cache names
     */
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] cacheNames() default {};


    /**
     * Alias for {@link CacheConfig#keyGenerator}.
     *
     * @return The key generator class
     */
    @AliasFor(annotation = CacheConfig.class, member = "keyGenerator")
    Class<? extends CacheKeyGenerator> keyGenerator() default DefaultCacheKeyGenerator.class;

    /**
     * Limit the automatic {@link CacheKeyGenerator} to the given parameter names. Mutually exclusive with
     * {@link #keyGenerator()}
     *
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};

    /**
     * @return Whether all values within the cache should be evicted or only those for the generated key
     */
    boolean all() default false;

    /**
     * Whether the cache operation should be performed asynchronously and not block the returning value. Note that
     * when set to <tt>true</tt> then any cache errors will not be propagated back to the client and will simply be
     * logged by default unless the return value itself is a non-blocking type such as
     * {@link java.util.concurrent.CompletableFuture}.
     *
     * @return True if should be done asynchronously
     */
    boolean async() default false;
}
