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

import org.particleframework.cache.interceptor.DefaultKeyGenerator;
import org.particleframework.cache.interceptor.KeyGenerator;
import org.particleframework.context.annotation.AliasFor;

import java.lang.annotation.*;

/**
 * <p>An annotation that can be applied at the type or method level to indicate that the annotated operation should cause the
 * return value to be cached within the given cache name. Unlike {@link Cacheable} this annotation will never skip the original invocation.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@CacheConfig
public @interface CachePut {

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     */
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] value() default {};

    /**
     * Alias for {@link CacheConfig#cacheNames}.
     */
    @AliasFor(annotation = CacheConfig.class, member = "cacheNames")
    String[] cacheNames() default {};


    /**
     * Alias for {@link CacheConfig#keyGenerator}.
     */
    @AliasFor(annotation = CacheConfig.class, member = "keyGenerator")
    Class<? extends KeyGenerator> keyGenerator() default DefaultKeyGenerator.class;
}
