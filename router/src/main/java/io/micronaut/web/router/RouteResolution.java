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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of resolving the route of a request: a {@link Matched match}, {@link NotFound no
 * route}, or {@link Pending matches that depend on the body} of the request, which the server
 * chooses among once it read the body, see {@link PendingRouteMatch}.
 *
 * @param <T> The target type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public sealed interface RouteResolution<T, R> permits RouteResolution.Matched, RouteResolution.NotFound, RouteResolution.Pending {

    /**
     * @param match A match, or {@code null}
     * @param <T>   The target type
     * @param <R>   The result type
     * @return The resolution of the match: {@link NotFound} for {@code null}
     */
    static <T, R> RouteResolution<T, R> of(@Nullable UriRouteMatch<T, R> match) {
        return match == null ? notFound() : new Matched<>(match);
    }

    /**
     * @param <T> The target type
     * @param <R> The result type
     * @return No route
     */
    @SuppressWarnings("unchecked")
    static <T, R> RouteResolution<T, R> notFound() {
        return (RouteResolution<T, R>) NotFound.INSTANCE;
    }

    /**
     * @return The match of a {@link Matched} resolution, {@code null} for the others
     */
    default @Nullable UriRouteMatch<T, R> matchOrNull() {
        return this instanceof Matched<T, R> matched ? matched.match() : null;
    }

    /**
     * The route of the request.
     *
     * @param match The match
     * @param <T>   The target type
     * @param <R>   The result type
     */
    record Matched<T, R>(UriRouteMatch<T, R> match) implements RouteResolution<T, R> {
    }

    /**
     * No route matches the request.
     *
     * @param <T> The target type
     * @param <R> The result type
     */
    @SuppressWarnings("java:S2326") // the type parameters of the sealed interface
    record NotFound<T, R>() implements RouteResolution<T, R> {
        private static final NotFound<?, ?> INSTANCE = new NotFound<>();
    }

    /**
     * The route of the request depends on its body.
     *
     * @param pending The matches that depend on the body
     * @param <T>     The target type
     * @param <R>     The result type
     */
    record Pending<T, R>(PendingRouteMatch<T, R> pending) implements RouteResolution<T, R> {
    }
}
