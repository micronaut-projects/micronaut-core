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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * A source of routes that are resolved at runtime, next to the routes the application {@link Router}
 * was built with. Implement it as a bean: when the application router matches no route for a
 * request, the route sources are consulted in {@link Ordered order}, and the first match is used.
 * <p>A match from a route source is a real route match: server filters, security, CORS and error
 * handling apply to it as they apply to controller routes, and a source's routes that match the
 * path but not the method make the server respond {@code 405 Method Not Allowed}.
 * <p>The routes of a source can change at any time, e.g. on a refresh event, without rebuilding the
 * application router. A simple way to implement a source is a {@link DefaultRouter} built from a
 * {@link DefaultRouteBuilder} whose routes point to an executable method of a bean, replaced when
 * the routes change, see {@link #of(Supplier)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteSource extends Ordered {

    /**
     * Find the routes that match the request (method, path and conditions) most closely, like
     * {@link Router#findAllClosest(HttpRequest)}.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return The closest matches, or an empty list
     */
    <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request);

    /**
     * Find the routes that match the path of the request, with any method, like
     * {@link Router#findAny(HttpRequest)}.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return The matches, or an empty list
     */
    <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request);

    /**
     * Find the route that matches the request most closely, like
     * {@link Router#findClosest(HttpRequest)}.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return The match, or {@code null}
     * @throws DuplicateRouteException if several routes match equally closely
     */
    default <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        List<UriRouteMatch<T, R>> uriRoutes = findAllClosest(request);
        if (uriRoutes.size() > 1) {
            uriRoutes = ImplicitHeadRoutes.preferExplicit(uriRoutes);
        }
        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(request.getPath(), (List) uriRoutes);
        }
        return uriRoutes.isEmpty() ? null : uriRoutes.get(0);
    }

    /**
     * A route source backed by a router that may be replaced at any time, e.g. a
     * {@link DefaultRouter} rebuilt when the routes change.
     *
     * @param router Supplies the current router
     * @return The route source
     */
    static RouteSource of(Supplier<? extends Router> router) {
        Objects.requireNonNull(router, "router");
        return new RouteSource() {
            @Override
            public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
                return router.get().findAllClosest(request);
            }

            @Override
            public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
                return router.get().findAny(request);
            }

            @Override
            public <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request) {
                return router.get().findClosest(request);
            }
        };
    }
}
