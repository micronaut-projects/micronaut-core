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
package io.micronaut.http.annotation;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.async.annotation.SingleResult;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation that can be applied to method to signify the method receives a {@link io.micronaut.http.HttpMethod#GET}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface Get {

    /**
     * @return The URI of the GET route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String value() default UriMapping.DEFAULT_URI;

    /**
     * @return The URI of the GET route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String uri() default UriMapping.DEFAULT_URI;

    /**
     * Only to be used in the context of a server.
     *
     * @return The URIs of the GET route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "uris")
    @AliasFor(annotation = UriMapping.class, member = "uris")
    String[] uris() default {UriMapping.DEFAULT_URI};

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

    /**
     * @return True if a HEAD route should also be registered for the same method
     */
    boolean headRoute() default true;
}
