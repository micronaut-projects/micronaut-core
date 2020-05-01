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

import io.micronaut.aop.Around;
import io.micronaut.cache.interceptor.CacheInterceptor;
import io.micronaut.cache.interceptor.CacheKeyGenerator;
import io.micronaut.cache.interceptor.DefaultCacheKeyGenerator;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Type;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>An annotation that can be used on either a type or an annotation stereotype to configure common caching
 * behaviour.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Around
@Type(CacheInterceptor.class)
public @interface CacheConfig {
    /**
     * @return Same as {@link #cacheNames()}
     */
    @AliasFor(member = "cacheNames")
    String[] value() default {};

    /**
     * Specifies one or many cache names to store cache operation values in. If specified at the type
     * level, can be overridden at the method level.
     *
     * @return The names of the caches to to store values in
     */
    String[] cacheNames() default {};

    /**
     * @return The default bean type of the key generator
     */
    Class<? extends CacheKeyGenerator> keyGenerator() default DefaultCacheKeyGenerator.class;

    /**
     * @return True if values returned from the cache should be converted to the method return type.
     */
    boolean convert() default true;
}
