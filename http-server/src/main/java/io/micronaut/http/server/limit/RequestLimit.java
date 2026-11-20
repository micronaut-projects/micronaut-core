/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.server.limit;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies the named {@link RequestLimiter request limiters} to the routes of a controller or to
 * a route method, in addition to the limiters whose configured patterns match the path. A
 * functional route selects limiters with the {@link #ATTRIBUTE} attribute, a name or a list of
 * names, or with this annotation, see {@code RouteSpec#annotate}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Experimental
public @interface RequestLimit {

    /**
     * The route attribute that names the limiters of a functional route.
     */
    String ATTRIBUTE = "micronaut.server.request-limit";

    /**
     * @return The names of the limiters
     */
    String[] value();
}
