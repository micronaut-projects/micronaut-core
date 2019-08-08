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
package io.micronaut.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.async.annotation.SingleResult;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * This annotation is designed for non-standard http methods, that you
 * can provide by specifying the required {@link #method()} property.
 * @author spirit-1984
 * @since 1.2.1
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface CustomHttpMethod {
    /**
     * @return The URI of the GET route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String value() default UriMapping.DEFAULT_URI;

    /**
     *
     * @return The name of the non-standard http method.
     */
    String method();

    /**
     * @return The URI of the REPORT route if not specified inferred from the method name and arguments
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String uri() default UriMapping.DEFAULT_URI;

    /**
     * @return The default produces, otherwise override from controller
     */
    @AliasFor(annotation = Produces.class, member = "value")
    String[] produces() default {};

    /**
     * The default consumes. Ignored for server request which never a consume a value for a GET request.
     *
     * @return The default consumes, otherwise override from controller
     */
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] consumes() default {};


    /**
     * Shortcut that allows setting both the {@link #consumes()} and {@link #produces()} settings to the same media type.
     *
     * @return The media type this method processes
     */
    @AliasFor(annotation = Produces.class, member = "value")
    @AliasFor(annotation = Consumes.class, member = "value")
    String[] processes() default {};

    /**
     * Shortcut that allows setting both the {@link Consumes} and {@link Produces} single settings.
     *
     * @return Whether a single or multiple items are produced/consumed
     */
    @AliasFor(annotation = Produces.class, member = "single")
    @AliasFor(annotation = Consumes.class, member = "single")
    @AliasFor(annotation = SingleResult.class, member = "value")
    boolean single() default false;
}
