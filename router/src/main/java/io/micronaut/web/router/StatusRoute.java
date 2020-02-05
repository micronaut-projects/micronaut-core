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
package io.micronaut.web.router;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Represents a {@link Route} that matches a status.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface StatusRoute extends MethodBasedRoute {
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
     * @param <T>    The matched route
     * @return The route match
     */
    <T> Optional<RouteMatch<T>> match(HttpStatus status);

    /**
     * Match the given HTTP status.
     *
     * @param originatingClass The class where the error originates from
     * @param status The status to match
     * @param <T>    The matched route
     * @return The route match
     */
    <T> Optional<RouteMatch<T>> match(Class originatingClass, HttpStatus status);

    @Override
    StatusRoute consumes(MediaType... mediaType);

    @Override
    StatusRoute nest(Runnable nested);

    @Override
    StatusRoute where(Predicate<HttpRequest<?>> condition);
}
