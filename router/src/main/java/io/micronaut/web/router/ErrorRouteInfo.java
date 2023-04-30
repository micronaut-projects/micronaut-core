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
package io.micronaut.web.router;

import java.util.Optional;

/**
 * Represents a {@link Route} that matches an exception.
 * @param <T> The target
 * @param <R> The result
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ErrorRouteInfo<T, R> extends MethodBasedRouteInfo<T, R>, RequestMatcher {

    /**
     * @return The type the exception originates from. Null if the error route is global.
     */
    Class<?> originatingType();

    /**
     * @return The type of exception
     */
    Class<? extends Throwable> exceptionType();

    /**
     * Match the given exception.
     *
     * @param exception The exception to match
     * @return The route match
     */
    Optional<RouteMatch<R>> match(Throwable exception);

    /**
     * Match the given exception.
     *
     * @param originatingClass The class where the error originates from
     * @param exception        The exception to match
     * @return The route match
     */
    Optional<RouteMatch<R>> match(Class<?> originatingClass, Throwable exception);

}
