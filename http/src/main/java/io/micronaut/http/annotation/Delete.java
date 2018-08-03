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

package io.micronaut.http.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to method to signify the method receives a {@link io.micronaut.http.HttpMethod#DELETE}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface Delete {

    /**
     * @return The URI of the DELETE route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    String value() default HttpMethodMapping.DEFAULT_URI;

    /**
     * @return The URI of the DELETE route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    String uri() default HttpMethodMapping.DEFAULT_URI;

    /**
     * @return The default consumes, otherwise override from controller
     */
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] consumes() default {};

    /**
     * @return The default produces, otherwise override from controller
     */
    @AliasFor(annotation = Produces.class, member = "value")
    String[] produces() default {};

    /**
     * Shortcut that allows setting both the {@link #consumes()} and {@link #produces()} settings to the same media type.
     *
     * @return The media type this method processes
     */
    @AliasFor(annotation = Produces.class, member = "value")
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] processes() default {};
}
