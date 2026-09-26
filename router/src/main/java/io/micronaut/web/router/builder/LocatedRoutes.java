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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;

/**
 * The routes of the targets of a type that a locator locates, see
 * {@link HttpRouteBuilder#locate(String, LocatorHandler, java.util.function.Function)}: their URIs
 * are relative to the prefix of the locator, and their handlers receive the located target.
 *
 * <pre>{@code
 * LocatedRoutes<Order> itemRoutes = new LocatedRoutes<>() {
 *     public Argument<Order> targetType() {
 *         return Argument.of(Order.class);
 *     }
 *
 *     public void routes(LocatedHttpRouteBuilder<Order> items) {
 *         items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
 *             HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     }
 * };
 * routes.locate("/orders/{id}", (request, pathVariables) -> orders.find(pathVariables.getLong("id")), itemRoutes);
 * }</pre>
 *
 * <p>The router declares the routes of an instance once, when a locator locates the first target
 * that the instance routes, and keeps them for as long as the application routes: an instance
 * describes the routes of every target of its type, not of one target, which reaches the
 * handlers as an argument or through {@link LocatedRoutes#locatedTarget(PathVariables)}. Locators that answer
 * the same instance share its routes. Declare the routes of a type once and reuse the instance,
 * e.g. in a field, rather than creating an instance per target.</p>
 *
 * <p>Error and status routes declared in a group of the builder are local to its routes and
 * supported; global ones, declared on the builder itself, server filters and ports are not: they
 * belong to the application routes, and declaring them fails the request that located the
 * first target.</p>
 *
 * @param <T> The type of the located targets
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface LocatedRoutes<T> {

    /**
     * The type of the targets these routes are for. A located target that is not an instance of
     * the type fails the request, answered by the error routes like a failed controller method.
     *
     * @return The type of the targets
     */
    Argument<T> targetType();

    /**
     * Declares the routes of a located target, relative to the locator's prefix.
     *
     * @param routes The builder of the routes, closed when this method returns
     */
    void routes(LocatedHttpRouteBuilder<T> routes);

    /**
     * The target a locator returned for the route of the path variables given to a handler, see
     * {@link HttpRouteBuilder#locate(String, LocatorHandler, java.util.function.Function)}: the
     * innermost one when locators locate each other.
     *
     * <pre>{@code
     * order.GET("/", (request, pathVariables) -> HttpResponse.ok(LocatedRoutes.locatedTarget(pathVariables, Order.class).id()));
     * }</pre>
     *
     * @param pathVariables The path variables a handler received
     * @return The target, or {@code null} for a route that was not located
     * @since 5.3.0
     */
    static @Nullable Object locatedTarget(PathVariables pathVariables) {
        return pathVariables instanceof DefaultPathVariables routeVariables ? routeVariables.resolvedTarget() : null;
    }

    /**
     * The target a locator returned for the route of the path variables given to a handler, of a
     * type, see {@link #locatedTarget(PathVariables)}.
     *
     * @param pathVariables The path variables a handler received
     * @param type          The type of the target
     * @param <T>           The type of the target
     * @return The target
     * @throws IllegalStateException if the route was not located, or the target is not of the type
     * @since 5.3.0
     */
    static <T> T locatedTarget(PathVariables pathVariables, Class<T> type) {
        Object target = locatedTarget(pathVariables);
        if (!type.isInstance(target)) {
            throw new IllegalStateException("The located target is not a " + type.getName() + ": " + target);
        }
        return type.cast(target);
    }
}
