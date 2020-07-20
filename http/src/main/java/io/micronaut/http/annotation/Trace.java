/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
 * Annotation that can be applied to method to signify the method receives a {@link io.micronaut.http.HttpMethod#TRACE}.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Documented
@Retention(RUNTIME)
@Target({ElementType.METHOD})
@HttpMethodMapping
public @interface Trace {

    /**
     * @return The URI of the TRACE route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String value() default UriMapping.DEFAULT_URI;

    /**
     * @return The URI of the TRACE route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "value")
    @AliasFor(annotation = UriMapping.class, member = "value")
    String uri() default UriMapping.DEFAULT_URI;

    /**
     * Only to be used in the context of a server.
     *
     * @return The URIs of the TRACE route
     */
    @AliasFor(annotation = HttpMethodMapping.class, member = "uris")
    @AliasFor(annotation = UriMapping.class, member = "uris")
    String[] uris() default {UriMapping.DEFAULT_URI};
}
