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
package io.micronaut.web.router.annotation;

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Precompile the HTTP routes of the application. Put it on one class of the application, e.g.
 * the {@code Application} class: the annotation processor then derives the URI routes of every
 * controller of the compilation, and of the controllers in the listed packages, and generates a
 * class with them. At runtime the router registers those routes without reading the controller
 * annotations or parsing the URI templates, and builds each route when it is first matched.
 * <p>Routes that cannot be precompiled are registered at runtime as usual, as are all routes when
 * the application uses a custom {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} or
 * a server context path. The routes are the same either way.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Documented
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface PrecompiledHttpRoutes {

    /**
     * @return Packages of compiled libraries whose controllers are precompiled too
     */
    String[] packages() default {};
}
