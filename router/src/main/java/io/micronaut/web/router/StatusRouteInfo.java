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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;

import java.util.Optional;

/**
 * Represents a {@link RouteInfo} that matches a status.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface StatusRouteInfo<T, R> extends MethodBasedRouteInfo<T, R>, RequestMatcher {
    /**
     * @return The type the exception originates from. Null if the error route is global.
     */
    @Nullable
    Class<?> originatingType();

    /**
     * @return The status
     */
    HttpStatus status();

    /**
     * Match the given HTTP status.
     *
     * @param status The status to match
     * @return The route match
     */
    Optional<RouteMatch<R>> match(HttpStatus status);

    /**
     * Match the given HTTP status.
     *
     * @param originatingClass The class where the error originates from
     * @param status The status to match
     * @return The route match
     */
    Optional<RouteMatch<R>> match(Class<?> originatingClass, HttpStatus status);

}
