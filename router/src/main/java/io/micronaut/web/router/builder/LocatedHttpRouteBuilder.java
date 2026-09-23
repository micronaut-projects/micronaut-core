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
 *     items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
 *         HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     items.handle(HttpMethod.POST, "/items", Argument.of(Item.class), (request, pathVariables, order, item) ->
 *         HttpResponse.created(order.add(item)));
 * }
 * }</pre>
 *
 * <p>The router checks the target a locator located against the type of the routes it chose for
 * it: a target that is not an instance of the type fails the request, answered by the error
 * routes like a failed controller method. The typed handlers have no shortcuts such as
 * {@code GET}: {@code POST(uri, (request, pathVariables, form) -> ...)} would be ambiguous with
 * the form handlers. The asynchronous typed handler receives the body too, see
 * {@link LocatedAsyncBodyRequestHandler}: {@code (request, pathVariables, target) -> ...} would be
 * ambiguous with an {@link AsyncBodyRequestHandler}. The handlers of the groups of the routes, see
 * {@link #group}, read the target with {@link LocatedRoutes#locatedTarget(PathVariables, Class)}.</p>
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

    /**
     * Route requests to a handler function that receives the located target.
     *
     * @param method  The HTTP method
     * @param uri     The URI template, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handle(HttpMethod, String, RequestHandler)
     */
    HttpRouteSpec handle(HttpMethod method, String uri, LocatedRequestHandler<T> handler);

    /**
     * Route requests to a handler function that receives the located target and the body decoded
     * to the given type.
     *
     * @param method   The HTTP method
     * @param uri      The URI template, relative to the prefix of the locator
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @see #handle(HttpMethod, String, Argument, BodyRequestHandler)
     */
    <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, LocatedBodyRequestHandler<T, B> handler);

    /**
     * Like the variant taking an {@link Argument}, with the body type as a class: {@code Argument.of(bodyType)}.
     *
     * @param method   The HTTP method
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @since 5.3.0
     */
    default <B> HttpRouteSpec handle(HttpMethod method, String uri, Class<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        return handle(method, uri, Argument.of(Objects.requireNonNull(bodyType, "bodyType")), handler);
    }

    /**
     * Route requests with a submitted form to a handler function that receives the located target
     * and the whole form.
     *
     * @param method  The HTTP method
     * @param uri     The URI template, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleForm(HttpMethod, String, FormRequestHandler)
     */
    HttpRouteSpec handleForm(HttpMethod method, String uri, LocatedFormRequestHandler<T> handler);

    /**
     * Route requests to a handler function that receives the located target and the body of the
     * request, which it reads, and completes the response later.
     *
     * @param method  The HTTP method
     * @param uri     The URI template, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncBodyRequestHandler)
     */
    HttpRouteSpec handleAsync(HttpMethod method, String uri, LocatedAsyncBodyRequestHandler<T> handler);

    /**
     * Bind a handler function that receives the located target to a declared route.
     *
     * @param route   The declared route, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    HttpRouteSpec handle(RouteDeclaration route, LocatedRequestHandler<T> handler);

    /**
     * Bind a handler function that receives the located target and the decoded body to a
     * declared route.
     *
     * @param route    The declared route, relative to the prefix of the locator
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @see #handle(RouteDeclaration, Argument, BodyRequestHandler)
     */
    <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, LocatedBodyRequestHandler<T, B> handler);

    /**
     * Like the variant taking an {@link Argument}, with the body type as a class: {@code Argument.of(bodyType)}.
     *
     * @param route    The declaration of the route
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @since 5.3.0
     */
    default <B> HttpRouteSpec handle(RouteDeclaration route, Class<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        return handle(route, Argument.of(Objects.requireNonNull(bodyType, "bodyType")), handler);
    }

    /**
     * Bind a form handler function that receives the located target to a declared route.
     *
     * @param route   The declared route, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleForm(RouteDeclaration, FormRequestHandler)
     */
    HttpRouteSpec handleForm(RouteDeclaration route, LocatedFormRequestHandler<T> handler);

    /**
     * Bind a handler function that receives the located target and the body of the request, and
     * completes the response later, to a declared route.
     *
     * @param route   The declared route, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleAsync(RouteDeclaration, AsyncBodyRequestHandler)
     */
    HttpRouteSpec handleAsync(RouteDeclaration route, LocatedAsyncBodyRequestHandler<T> handler);

    /**
     * Like {@link #handle(HttpMethod, String, LocatedRequestHandler)}, at the path of the scope, like a controller method mapped without a URI, e.g. {@code @Get}: the prefix of the {@link HttpRouteBuilder#path(String, java.util.function.Consumer) group}, the prefix of the locator in the routes of a located target, or {@code /} at the root, under the context path.
     *
     * @param method         The HTTP method
     * @param handler        The handler
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec handle(HttpMethod method, LocatedRequestHandler<T> handler) {
        return handle(method, "/", handler);
    }

    /**
     * Like {@link #handle(HttpMethod, String, Argument, LocatedBodyRequestHandler)}, at the path of the scope, like a controller method mapped without a URI, e.g. {@code @Get}: the prefix of the {@link HttpRouteBuilder#path(String, java.util.function.Consumer) group}, the prefix of the locator in the routes of a located target, or {@code /} at the root, under the context path.
     *
     * @param method         The HTTP method
     * @param bodyType       The body type
     * @param handler        The handler
     * @param <B>            The body type
     * @return The route
     * @since 5.3.0
     */
    default <B> HttpRouteSpec handle(HttpMethod method, Argument<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        return handle(method, "/", bodyType, handler);
    }

    /**
     * Like {@link #handle(HttpMethod, String, Class, LocatedBodyRequestHandler)}, at the path of the scope, like a controller method mapped without a URI, e.g. {@code @Get}: the prefix of the {@link HttpRouteBuilder#path(String, java.util.function.Consumer) group}, the prefix of the locator in the routes of a located target, or {@code /} at the root, under the context path.
     *
     * @param method         The HTTP method
     * @param bodyType       The body type
     * @param handler        The handler
     * @param <B>            The body type
     * @return The route
     * @since 5.3.0
     */
    default <B> HttpRouteSpec handle(HttpMethod method, Class<B> bodyType, LocatedBodyRequestHandler<T, B> handler) {
        return handle(method, "/", bodyType, handler);
    }

    /**
     * Like {@link #handleForm(HttpMethod, String, LocatedFormRequestHandler)}, at the path of the scope, like a controller method mapped without a URI, e.g. {@code @Get}: the prefix of the {@link HttpRouteBuilder#path(String, java.util.function.Consumer) group}, the prefix of the locator in the routes of a located target, or {@code /} at the root, under the context path.
     *
     * @param method         The HTTP method
     * @param handler        The handler
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec handleForm(HttpMethod method, LocatedFormRequestHandler<T> handler) {
        return handleForm(method, "/", handler);
    }

    /**
     * Like {@link #handleAsync(HttpMethod, String, LocatedAsyncBodyRequestHandler)}, at the path of the scope, like a controller method mapped without a URI, e.g. {@code @Get}: the prefix of the {@link HttpRouteBuilder#path(String, java.util.function.Consumer) group}, the prefix of the locator in the routes of a located target, or {@code /} at the root, under the context path.
     *
     * @param method         The HTTP method
     * @param handler        The handler
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec handleAsync(HttpMethod method, LocatedAsyncBodyRequestHandler<T> handler) {
        return handleAsync(method, "/", handler);
    }

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
     * Route the requests under a prefix of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template engine} to the routes
     * of a target that a locator locates from the located target of these routes.
     *
     * @param prefix   The template of the prefix, relative to the prefix of the locator of these routes
     * @param locator  Locates the target from the target of these routes, or answers {@code null} for {@code 404}
     * @param routesOf The routes of a located target
     * @param <U>      The type of the target the locator locates
     * @see #locate(RouteTemplate, LocatorHandler, Function)
     */
    <U> void locate(RouteTemplate prefix, LocatedLocatorHandler<T, ? extends U> locator, Function<? super U, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template engine} to one set of
     * routes of the targets that a locator locates from the located target of these routes, see
     * {@link #locate(RouteTemplate, LocatedLocatorHandler, Function)}.
     *
     * @param prefix  The template of the prefix, relative to the prefix of the locator of these routes
     * @param locator Locates the target from the target of these routes, or answers {@code null} for {@code 404}
     * @param routes  The routes of every located target
     * @param <U>     The type of the target the locator locates
     */
    default <U> void locate(RouteTemplate prefix, LocatedLocatorHandler<T, ? extends U> locator, LocatedRoutes<U> routes) {
        Objects.requireNonNull(routes, "routes");
        locate(prefix, locator, target -> routes);
    }

    /**
     * Route the requests under a prefix of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template engine} to the routes
     * of a target that a locator locates later from the located target of these routes.
     *
     * @param prefix   The template of the prefix, relative to the prefix of the locator of these routes
     * @param locator  Locates the target later from the target of these routes, or completes with {@code null} for {@code 404}
     * @param routesOf The routes of a located target
     * @param <U>      The type of the target the locator locates
     * @see #locateAsync(RouteTemplate, AsyncLocatorHandler, Function)
     */
    <U> void locateAsync(RouteTemplate prefix, LocatedAsyncLocatorHandler<T, ? extends U> locator,
                         Function<? super U, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix of any registered
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine route template engine} to one set of
     * routes of the targets that a locator locates later from the located target of these routes,
     * see {@link #locateAsync(RouteTemplate, LocatedAsyncLocatorHandler, Function)}.
     *
     * @param prefix  The template of the prefix, relative to the prefix of the locator of these routes
     * @param locator Locates the target later from the target of these routes, or completes with {@code null} for {@code 404}
     * @param routes  The routes of every located target
     * @param <U>     The type of the target the locator locates
     */
    default <U> void locateAsync(RouteTemplate prefix, LocatedAsyncLocatorHandler<T, ? extends U> locator, LocatedRoutes<U> routes) {
        Objects.requireNonNull(routes, "routes");
        locateAsync(prefix, locator, target -> routes);
    }
}
