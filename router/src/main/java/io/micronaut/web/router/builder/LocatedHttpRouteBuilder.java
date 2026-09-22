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
import io.micronaut.http.uri.RouteTemplate;

import io.micronaut.http.PathVariables;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The builder of the routes of located targets of a type, see {@link LocatedRoutes}: besides the
 * routes of an {@link HttpRouteBuilder}, whose handlers read the target with
 * {@link LocatedRoutes#locatedTarget(PathVariables, Class)}, it routes to handlers that receive the target as an
 * argument, of the type of the routes, {@link LocatedRoutes#targetType()}. The URIs are relative
 * to the prefix of the locator.
 *
 * <pre>{@code
 * public void routes(LocatedHttpRouteBuilder<Order> items) {
 *     items.GET("/items/{item}").handle((request, pathVariables, order) ->
 *         HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     items.POST("/items").body(Item.class).handle((request, pathVariables, order, item) ->
 *         HttpResponse.created(order.add(item)));
 * }
 * }</pre>
 *
 * <p>The creators of the builder return a {@link LocatedHttpRouteSpec}: besides the terminals
 * of an {@link HttpRouteSpec}, whose handlers read the target with
 * {@link LocatedRoutes#locatedTarget(PathVariables, Class)}, its terminals take a handler that
 * receives the target, after the settings and the body stage of the route. The router checks the
 * target a locator located against the type of the routes it chose for it: a target that is not
 * an instance of the type fails the request, answered by the error routes like a failed
 * controller method. The handlers of the groups of the routes, see {@link #group}, read the
 * target with {@link LocatedRoutes#locatedTarget(PathVariables, Class)}.</p>
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpRouteBuilder<T> extends HttpRouteBuilder permits DefaultLocatedHttpRouteBuilder {

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
     * Declare a route of the requests of a method with a template of any registered route
     * template engine, relative to the prefix of the locator, see {@link #route(HttpMethod, String)}.
     *
     * @param method   The HTTP method
     * @param template The template, relative to the prefix of the locator
     * @return The pending route
     * @see HttpRouteBuilder#route(HttpMethod, RouteTemplate)
     */
    @Override
    LocatedHttpRouteSpec<T> route(HttpMethod method, RouteTemplate template);

    /**
     * Declare a route of the requests of a method by its name with a template of any registered
     * route template engine, relative to the prefix of the locator, see
     * {@link #route(HttpMethod, String)}.
     *
     * @param httpMethodName The name of the HTTP method, a token
     * @param template       The template, relative to the prefix of the locator
     * @return The pending route
     * @see HttpRouteBuilder#route(String, RouteTemplate)
     */
    @Override
    LocatedHttpRouteSpec<T> route(String httpMethodName, RouteTemplate template);

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
}
