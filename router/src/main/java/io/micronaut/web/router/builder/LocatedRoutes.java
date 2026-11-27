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
import io.micronaut.http.uri.RouteCaptures;
import org.jspecify.annotations.Nullable;

import java.util.List;

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
 *         items.GET("/items/{item}").handle((request, pathVariables, order) ->
 *             HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     }
 * };
 * routes.locate("/orders/{id}", (request, pathVariables) -> orders.find(pathVariables.getLong("id")), itemRoutes);
 * }</pre>
 *
 * <p>The router declares the routes of an instance once, when a locator locates the first target
 * that the instance routes, and keeps them for as long as the instance is reachable: an instance
 * describes the routes of every target of its type, not of one target, which reaches the
 * handlers as an argument or through {@link LocatedRoutes#locatedTarget(PathVariables)}. Locators that return
 * the same instance share its routes. Declare the routes of a type once and reuse the instance,
 * e.g. in a field, rather than creating an instance per target.</p>
 *
 * <p>A located route inherits what a route declared in the groups enclosing its locator route
 * has of them, and its own settings, and those of the groups of its {@code LocatedRoutes}, take
 * precedence: the annotations, see {@link HttpRouteGroup#annotate(io.micronaut.core.annotation.AnnotationValue)},
 * e.g. a filter bound to a {@code @FilterMatcher} annotation of such a group runs for it, and
 * the {@code @Produces}, {@code @Consumes}, {@code @Status} and {@code @ExecuteOn} of the group
 * apply to it; the attributes, see {@link RouteSpec#attribute(String, Object)}; and the media
 * types and the executor, see {@link RouteSpec#produces(io.micronaut.http.MediaType...)} and
 * {@link ExecutionSpec#executeOn(String)}. The routes a locator route locates decide which media
 * types the request may have and accept, with what they inherit, not the locator route.</p>
 *
 * <p>Error and status routes declared in a group of the builder, see
 * {@link LocatedHttpRouteBuilder#group(java.util.function.Consumer)}, are local to its routes and
 * supported; global ones, declared on the builder itself, and ports are not: they belong to the
 * application routes, and declaring them fails, when the routes are declared. Routes that fail
 * to declare fail every request routed to them, and are declared again for the next one. The
 * builder has no server filters, see {@link HttpRouteBuilder#serverFilter(String...)}.</p>
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

    /**
     * The captures of the templates of the locator routes while a locator runs, given the path
     * variables a {@link LocatorHandler} received: of the locator routes that located the running
     * one, outermost first, then of the running locator route itself. The list holds only the
     * locator routes whose templates report captures, see
     * {@link io.micronaut.http.uri.RouteTemplate#match(String)}: a route of a template that does
     * not, e.g. a {@link io.micronaut.http.uri.UriMatchTemplate}, is left out, so the position of
     * a capture in the list does not tell the nesting level. Use
     * {@link #locatorRouteCapture(PathVariables)} for the running locator's own captures.
     *
     * <p>The captures are the same as {@link io.micronaut.http.uri.UriMatchInfo#getLocatorRouteCaptures()}
     * of the routes the locator locates. The {@link RouteCaptures#path() path} of each is what its
     * locator route matched: the request path for the outermost locator, the rest after the
     * prefixes of the outer locators for a nested one. The rest of a locator is its last variable
     * and the path of a nested locator keeps its leading slash, which the rest of the parent
     * starts after, so an inner capture {@code i} is at
     * {@code parent.start(restIndex) - 1 + child.start(i)} in the path of the parent.</p>
     *
     * @param pathVariables The path variables a locator received
     * @return The captures of the locator routes, empty if these are not the variables of a
     * running locator or no template of its locator routes reports them
     * @since 5.3.0
     */
    static List<RouteCaptures> locatorRouteCaptures(PathVariables pathVariables) {
        return pathVariables instanceof DefaultPathVariables routeVariables ? routeVariables.locatorRouteCaptures() : List.of();
    }

    /**
     * The captures of the template of the running locator route, given the path variables a
     * {@link LocatorHandler} received, e.g. to tell where the variables of its prefix are in the
     * path it matched.
     *
     * <pre>{@code
     * // offsetTemplate: a RouteTemplate of an engine whose captures report offsets, with the single variable "id"
     * builder.locate(offsetTemplate("/users/:id"), (request, pathVariables) -> {
     *     RouteCaptures own = LocatedRoutes.locatorRouteCapture(pathVariables);
     *     if (own == null || own.start(0) < 0) {
     *         return null; // no captures, or no offset of the value of "id"
     *     }
     *     return users.find(own.path().substring(own.start(0), own.end(0)));
     * }, routesOf);
     * }</pre>
     *
     * @param pathVariables The path variables a locator received
     * @return The captures, or {@code null} if these are not the variables of a running locator
     * or its template does not report them
     * @since 5.3.0
     */
    static @Nullable RouteCaptures locatorRouteCapture(PathVariables pathVariables) {
        return pathVariables instanceof DefaultPathVariables routeVariables ? routeVariables.locatorRouteCapture() : null;
    }
}
