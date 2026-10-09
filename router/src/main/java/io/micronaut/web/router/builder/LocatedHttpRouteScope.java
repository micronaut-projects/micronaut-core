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
import io.micronaut.http.HttpMethod;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The declarations of the routes of located targets of a type, see {@link LocatedRoutes}, that
 * the {@link LocatedHttpRouteBuilder} and its groups, {@link LocatedHttpRouteGroup}, have in
 * common: besides the declarations of an {@link HttpRouteScope}, routes whose terminals take a
 * handler that receives the target, locators that receive the target, and groups of the routes
 * of the same targets.
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@SuppressWarnings("MethodName")
public sealed interface LocatedHttpRouteScope<T> extends HttpRouteScope permits LocatedHttpRouteBuilder, LocatedHttpRouteGroup {

    /**
     * @return The type of the located targets of the routes, see {@link LocatedRoutes#targetType()}
     */
    Argument<T> targetType();

    @Override
    default LocatedHttpRouteSpec<T> GET(String uri) {
        return route(HttpMethod.GET, uri);
    }

    @Override
    default LocatedHttpRouteSpec<T> POST(String uri) {
        return route(HttpMethod.POST, uri);
    }

    @Override
    default LocatedHttpRouteSpec<T> PUT(String uri) {
        return route(HttpMethod.PUT, uri);
    }

    @Override
    default LocatedHttpRouteSpec<T> PATCH(String uri) {
        return route(HttpMethod.PATCH, uri);
    }

    @Override
    default LocatedHttpRouteSpec<T> DELETE(String uri) {
        return route(HttpMethod.DELETE, uri);
    }

    /**
     * Declare a route of the requests of a method, relative to the prefix of the locator: the
     * pending route, whose terminals also take a handler that receives the located target.
     *
     * @param method The HTTP method
     * @param uri    The URI template, relative to the prefix of the locator
     * @return The pending route
     * @see HttpRouteBuilder#route(HttpMethod, String)
     */
    @Override
    LocatedHttpRouteSpec<T> route(HttpMethod method, String uri);

    /**
     * Declare the routes of the requests of several methods, relative to the prefix of the
     * locator, see {@link #route(HttpMethod, String)}.
     *
     * @param methods The HTTP methods
     * @param uri     The URI template, relative to the prefix of the locator
     * @return The pending routes, to configure together
     * @see HttpRouteBuilder#route(Set, String)
     */
    @Override
    LocatedHttpRouteSpec<T> route(Set<HttpMethod> methods, String uri);

    /**
     * Declare a route of the requests of a method by its name, relative to the prefix of the
     * locator, see {@link #route(HttpMethod, String)}.
     *
     * @param httpMethodName The name of the HTTP method, a token
     * @param uri            The URI template, relative to the prefix of the locator
     * @return The pending route
     * @see HttpRouteBuilder#route(String, String)
     */
    @Override
    LocatedHttpRouteSpec<T> route(String httpMethodName, String uri);

    /**
     * Declare the routes of the requests of any method, relative to the prefix of the locator,
     * see {@link #route(HttpMethod, String)}.
     *
     * @param uri The URI template, relative to the prefix of the locator
     * @return The pending routes, to configure together
     * @see HttpRouteBuilder#any(String)
     */
    @Override
    LocatedHttpRouteSpec<T> any(String uri);

    /**
     * Route the requests under a prefix to the routes of a target that a locator locates from the
     * located target of these routes, e.g. a child of a node of a tree.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of these routes
     * @param locator   Locates the target from the target of these routes, or answers {@code null} for {@code 404}
     * @param routesOf  The routes of a located target
     * @param <U>       The type of the target the locator locates
     * @see #locate(String, LocatorHandler, Function)
     */
    <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator, Function<? super U, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix to one set of routes of the targets that a locator locates
     * from the located target of these routes, see {@link #locate(String, LocatedLocatorHandler, Function)}.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of these routes
     * @param locator   Locates the target from the target of these routes, or answers {@code null} for {@code 404}
     * @param routes    The routes of every located target
     * @param <U>       The type of the target the locator locates
     */
    default <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator, LocatedRoutes<U> routes) {
        Objects.requireNonNull(routes, "routes");
        locate(prefixUri, locator, target -> routes);
    }

    /**
     * Route the requests under a prefix to the routes of a target that a locator locates later
     * from the located target of these routes.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of these routes
     * @param locator   Locates the target later from the target of these routes, or completes with {@code null} for {@code 404}
     * @param routesOf  The routes of a located target
     * @param <U>       The type of the target the locator locates
     * @see #locateAsync(String, AsyncLocatorHandler, Function)
     */
    <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator,
                         Function<? super U, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix to one set of routes of the targets that a locator locates
     * later from the located target of these routes, see
     * {@link #locateAsync(String, LocatedAsyncLocatorHandler, Function)}.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of these routes
     * @param locator   Locates the target later from the target of these routes, or completes with {@code null} for {@code 404}
     * @param routes    The routes of every located target
     * @param <U>       The type of the target the locator locates
     */
    default <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator, LocatedRoutes<U> routes) {
        Objects.requireNonNull(routes, "routes");
        locateAsync(prefixUri, locator, target -> routes);
    }

    /**
     * Declare a group of the routes of the located targets, whose filters and settings apply to
     * every route declared in the lambda, see {@link HttpRouteGroup}: the group declares the
     * routes of the same targets, so its creators return a {@link LocatedHttpRouteSpec}, whose
     * terminals take a handler that receives the target, and its locators receive the target.
     * Its error and status routes are local to its routes.
     *
     * <pre>{@code
     * order.group(items -> {
     *     items.error(NoSuchElementException.class, (request, error) -> HttpResponse.notFound());
     *     items.GET("/items/{item}").handle((request, pathVariables, order) ->
     *         HttpResponse.ok(order.item(pathVariables.getInt("item"))));
     * });
     * }</pre>
     *
     * @param routes Declares the routes and the filters of the group
     * @see HttpRouteBuilder#group(Consumer)
     */
    void group(Consumer<LocatedHttpRouteGroup<T>> routes);

    /**
     * Declare a group of the routes of the located targets under a prefix, relative to the
     * prefix of the locator, see {@link #group(Consumer)} and {@link HttpRouteBuilder#path(String, Consumer)}.
     *
     * @param prefix The prefix of the URI templates of the routes of the group
     * @param routes Declares the routes and the filters of the group
     */
    void path(String prefix, Consumer<LocatedHttpRouteGroup<T>> routes);
}
