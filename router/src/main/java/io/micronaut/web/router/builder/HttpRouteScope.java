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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;

import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The declarations of handler routes, group routes, error routes and status routes that the
 * {@link HttpRouteBuilder} of an {@link HttpRoutes} bean, an {@link HttpRouteGroup} and the
 * {@link LocatedHttpRouteBuilder} of {@link LocatedRoutes} have in common. A route is declared in stages, see {@link HttpRouteSpec}. Only the builder declares
 * server filters, see {@link HttpRouteBuilder#serverFilter(String...)}: the filters of a group are
 * route filters, which apply to the routes of the group only.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@SuppressWarnings("MethodName")
public sealed interface HttpRouteScope permits HttpRouteBuilder, HttpRouteGroup, LocatedHttpRouteBuilder, AbstractHttpRouteBuilder {

    /**
     * Declare a route of {@code GET} requests, with an implicit {@code HEAD} route in an
     * {@link HttpRoutes} bean: the pending route, to configure and to end with a terminal, see
     * {@link HttpRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default HttpRouteSpec GET(String uri) {
        return route(HttpMethod.GET, uri);
    }

    /**
     * Declare a route of {@code POST} requests, see {@link #GET(String)}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default HttpRouteSpec POST(String uri) {
        return route(HttpMethod.POST, uri);
    }

    /**
     * Declare a route of {@code PUT} requests, see {@link #GET(String)}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default HttpRouteSpec PUT(String uri) {
        return route(HttpMethod.PUT, uri);
    }

    /**
     * Declare a route of {@code PATCH} requests, see {@link #GET(String)}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default HttpRouteSpec PATCH(String uri) {
        return route(HttpMethod.PATCH, uri);
    }

    /**
     * Declare a route of {@code DELETE} requests, see {@link #GET(String)}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default HttpRouteSpec DELETE(String uri) {
        return route(HttpMethod.DELETE, uri);
    }

    /**
     * Declare a route of the requests of a method. The route runs like a controller method:
     * filters, error routes, body readers and writers and executor selection apply as they do for
     * a controller method. Like a controller route it consumes JSON unless
     * {@link HttpRouteSpec#consumes} says otherwise, and in {@link HttpRoutes} its URI is under
     * {@code micronaut.server.context-path}. A {@code GET} route gets an implicit {@code HEAD}
     * route.
     *
     * @param method The HTTP method, not {@link HttpMethod#CUSTOM}: a route of a custom method
     *               is declared by its name, see {@link #route(String, String)}
     * @param uri    The URI template
     * @return The pending route
     * @throws IllegalArgumentException if the method is {@link HttpMethod#CUSTOM}
     */
    HttpRouteSpec route(HttpMethod method, String uri);

    /**
     * Declare the routes of the requests of several HTTP methods to one handler: a route per
     * method, which the pending route configures together.
     *
     * @param methods The HTTP methods, none {@link HttpMethod#CUSTOM}
     * @param uri     The URI template
     * @return The pending routes, to configure together
     * @throws IllegalArgumentException if there is no method, or one is {@link HttpMethod#CUSTOM}
     * @see #route(HttpMethod, String)
     */
    HttpRouteSpec route(Set<HttpMethod> methods, String uri);

    /**
     * Declare a route of the requests of a method by its name, including a custom HTTP method
     * such as {@code PROPFIND}, like a controller method annotated {@code @CustomHttpMethod}.
     *
     * <pre>{@code
     * routes.route("PROPFIND", "/items/{id}").handle((request, pathVariables) -> HttpResponse.ok(properties(pathVariables)));
     * }</pre>
     *
     * @param httpMethodName The name of the HTTP method, a token
     * @param uri            The URI template
     * @return The pending route
     * @throws IllegalArgumentException if the name is empty or not a token, e.g. blank
     * @see #route(HttpMethod, String)
     */
    HttpRouteSpec route(String httpMethodName, String uri);

    /**
     * Declare the routes of the requests of any HTTP method, standard or custom, to one handler:
     * a route per standard method and one for the custom methods, which the pending route
     * configures together.
     *
     * <ul>
     *     <li>A route of a specific method that matches a request as closely as the route of any
     *     method, e.g. {@code GET} on the same path, answers the request instead: the route of any
     *     method answers the other methods.</li>
     *     <li>The implicit {@code HEAD} route of a {@code GET} route on the same path answers a
     *     {@code HEAD} request before it, and a CORS preflight request is answered by the CORS
     *     filter as before: the route of any method answers the other {@code OPTIONS} and
     *     {@code HEAD} requests.</li>
     *     <li>A path the route matches is never answered with {@code 405}: every method has a
     *     route. A more specific route of another method on a path the route matches does not
     *     change that.</li>
     * </ul>
     *
     * @param uri The URI template
     * @return The pending routes, to configure together
     * @see #route(Set, String)
     * @since 5.3.0
     */
    HttpRouteSpec any(String uri);

    /**
     * Route {@code GET} requests to a handler function: a route with no settings, the same as
     * {@code GET(uri).handle(handler)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     */
    default void GET(String uri, RequestHandler handler) {
        GET(uri).handle(handler);
    }

    /**
     * Route {@code POST} requests to a handler function: the same as
     * {@code POST(uri).handle(handler)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     */
    default void POST(String uri, RequestHandler handler) {
        POST(uri).handle(handler);
    }

    /**
     * Route {@code PUT} requests to a handler function: the same as
     * {@code PUT(uri).handle(handler)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     */
    default void PUT(String uri, RequestHandler handler) {
        PUT(uri).handle(handler);
    }

    /**
     * Route {@code PATCH} requests to a handler function: the same as
     * {@code PATCH(uri).handle(handler)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     */
    default void PATCH(String uri, RequestHandler handler) {
        PATCH(uri).handle(handler);
    }

    /**
     * Route {@code DELETE} requests to a handler function: the same as
     * {@code DELETE(uri).handle(handler)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     */
    default void DELETE(String uri, RequestHandler handler) {
        DELETE(uri).handle(handler);
    }

    /**
     * Route the requests of any HTTP method to a handler function: the same as
     * {@code any(uri).handle(handler)}, see {@link #any(String)}.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @since 5.3.0
     */
    default void any(String uri, RequestHandler handler) {
        any(uri).handle(handler);
    }

    /**
     * Handle the exceptions of a type, and of its subtypes, with a handler function. Where it is
     * declared decides which routes it answers for:
     * <ul>
     *     <li>declared on the builder of an {@link HttpRoutes} bean, it is global, like an
     *     {@code @Error(global = true)} method: it answers requests to controller routes and
     *     handler routes that fail with such an exception;</li>
     *     <li>declared in a {@link HttpRouteGroup group}, it is local to the routes of the group
     *     and of its nested groups, like an {@code @Error}
     *     method of a controller is local to the routes of the controller.</li>
     * </ul>
     *
     * <pre>{@code
     * routes.error(NoSuchFileException.class, (request, error) -> HttpResponse.notFound()); // global
     * routes.path("/api", api -> {
     *     api.error(IllegalArgumentException.class, (request, error) -> HttpResponse.badRequest(error.getMessage())); // /api routes only
     *     api.GET("/orders/{id}", ordersHandler);
     * });
     * }</pre>
     *
     * <p>The error route of a failed handler route is looked up in its innermost group first,
     * then in the groups around it, then among the global error routes, the error routes of
     * controllers and of {@link HttpRoutes} beans; within each level, the error route of the
     * closest exception type answers. So a group's error route for a supertype wins over a global
     * error route for the exact type, like the local error route of a controller does. A route
     * that implements a bean method, see {@link HttpRouteSpec#annotationMetadata}, first has the
     * local error routes of the bean class. An error of a request no route matched, e.g. a
     * {@code 404} or a {@code 405}, is answered by the global error routes only.</p>
     *
     * @param type    The type of the exception
     * @param handler The handler
     * @param <E>     The type of the exception
     * @return The error route
     */
    <E extends Throwable> ErrorRouteSpec error(Class<E> type, ErrorRouteHandler<E> handler);

    /**
     * Handle the responses of a status with a handler function, e.g. to answer {@code 404}.
     * Declared on the builder of an {@link HttpRoutes} bean, it is global, like an
     * {@code @Error(status = ..., global = true)} method; declared in a
     * {@link HttpRouteGroup group}, it is local to the routes of the group, like an
     * {@code @Error(status = ...)} method of a controller: it answers the responses of that
     * status that a route of the group produces, or an {@link io.micronaut.http.exceptions.HttpStatusException}
     * of that status a route of the group throws, see {@link #error(Class, ErrorRouteHandler)}
     * for the order of the lookup. A request under the prefix of a group that no route matches,
     * a {@code 404}, is answered by the global status routes only, as no route of the group
     * matched it.
     *
     * @param status  The status
     * @param handler The handler
     * @return The status route
     */
    StatusRouteSpec status(HttpStatus status, StatusRouteHandler handler);

    /**
     * Handle the exceptions of a type, and of its subtypes, with a handler function that completes
     * the response later, like an {@code @Error} method returning a {@code CompletionStage}:
     * global on the builder, local to the routes of the group in a {@link HttpRouteGroup}. The
     * error route is selected like one added with
     * {@link #error(Class, ErrorRouteHandler)}: the error route of the closest exception type
     * answers, whether its handler is synchronous or not.
     *
     * @param type    The type of the exception
     * @param handler The handler
     * @param <E>     The type of the exception
     * @return The error route
     * @see AsyncErrorRouteHandler
     */
    <E extends Throwable> ErrorRouteSpec errorAsync(Class<E> type, AsyncErrorRouteHandler<E> handler);

    /**
     * Handle the responses of a status with a handler function that completes the response later,
     * like an {@code @Error(status = ...)} method returning a {@code CompletionStage}: global on
     * the builder, local to the routes of the group in a {@link HttpRouteGroup}, see
     * {@link #status(HttpStatus, StatusRouteHandler)}.
     *
     * @param status  The status
     * @param handler The handler
     * @return The status route
     * @see AsyncStatusRouteHandler
     */
    StatusRouteSpec statusAsync(HttpStatus status, AsyncStatusRouteHandler handler);

    /**
     * Declare a group of routes, whose filters apply to every route declared in the lambda,
     * e.g. to filter every route of an {@link HttpRoutes} bean. The builder of an
     * {@link HttpRoutes} bean has no route filter methods of its own, as it is shared by the beans:
     * the group is the scope of the filters, see {@link HttpRouteBuilder#serverFilter(String...)} for a server filter.
     *
     * <pre>{@code
     * routes.group(all -> {
     *     all.beforeReplacing((request, propagatedContext) -> {
     *         propagatedContext.add(new MdcPropagationContext(Map.of("path", request.getPath())));
     *         return null;
     *     });
     *     all.GET("/orders", ordersHandler);
     *     all.GET("/customers", customersHandler);
     * });
     * }</pre>
     *
     * <p>See {@link HttpRouteGroup} for which routes the filters apply to, and in which order.
     * A route declared in the lambda is ended with a terminal, see {@link HttpRouteSpec}, before
     * the lambda returns: otherwise the group fails with an {@link IllegalStateException} naming
     * the route.</p>
     *
     * @param routes Declares the routes and the filters of the group
     * @since 5.3.0
     */
    void group(Consumer<HttpRouteGroup> routes);

    /**
     * Declare a group of routes under a prefix: the URI template of every route of the group,
     * including the routes of its nested groups, is the prefix followed by
     * the URI template of the route, like the URI of a controller method under the URI of the
     * controller: {@code path("/api", api -> api.GET("/orders", handler))} routes
     * {@code GET /api/orders}. The prefixes of nested groups add up. The filters of the group apply
     * to every route declared in the lambda, see {@link HttpRouteGroup}.
     *
     * <p>The prefix is a path: it may have path variables, e.g. {@code /tenants/{tenant}}, but no
     * query or fragment.</p>
     *
     * @param prefix The prefix of the URI templates of the routes of the group
     * @param routes Declares the routes and the filters of the group
     * @since 5.3.0
     */
    void path(String prefix, Consumer<HttpRouteGroup> routes);

    /**
     * Route the requests under a prefix to the routes of a target located at runtime, like a
     * JAX-RS sub-resource locator. The prefix, e.g. {@code /orders/{id}}, is matched for every
     * standard HTTP method, followed by the rest of the path, which is empty or starts with a
     * slash. When the router selects the locator route, it runs the locator, and matches the rest
     * of the path, e.g. {@code /items/3} of {@code /orders/5/items/3}, with the
     * {@link LocatedRoutes} of the target: the request is handled by the route they declare, as if
     * they were the application routes, with the path variables of the prefix and of the route,
     * and with the target, see {@link LocatedRoutes#locatedTarget(PathVariables)}. A locator route of the
     * located routes locates again, from the rest of the path.
     *
     * <pre>{@code
     * routes.locate("/orders/{id}",
     *     (request, pathVariables) -> orders.find(pathVariables.getLong("id")),
     *     order -> order.isArchived() ? archivedOrderRoutes : orderRoutes);
     * }</pre>
     *
     * <p>The type of the target is the type the locator returns: the function receives it, e.g.
     * to choose the routes of a subtype. The handlers of the located routes receive the target of
     * the type of the routes, see {@link LocatedRoutes#targetType()}; a located target that is not
     * an instance of that type fails the request, answered by the error routes like a failed
     * controller method.</p>
     *
     * <p>When the located routes have no route for the rest of the path, the request is answered
     * like a request that no route matches: {@code 404}, or {@code 405}, {@code 415} or
     * {@code 406} if a located route matches the path with another method or media type. No other
     * route of the application is tried. The filters of the matched located route apply, and the
     * server filters apply to the full path.</p>
     *
     * <p>The locator runs while the request is matched, see {@link LocatorHandler}, at most once
     * per request: matching the request again, e.g. to find the allowed methods of a
     * {@code 405}, reuses the target, or the exception, of the first call. The routes of a
     * {@link LocatedRoutes} instance are declared once, when the first target it routes is
     * located, and shared by every locator that answers the instance.
     * A request with a custom HTTP method is located like any other: the located routes of that
     * method answer it, or those declared with {@code any(...)}, and the allowed methods of its
     * {@code 405} are those of the located routes.</p>
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target, or answers {@code null} for {@code 404}
     * @param routesOf  The routes of a located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    <T> void locate(String prefixUri, LocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix to one set of routes of the targets a locator locates, see
     * {@link #locate(String, LocatorHandler, Function)}.
     *
     * <pre>{@code
     * routes.locate("/orders/{id}", (request, pathVariables) -> orders.find(pathVariables.getLong("id")), orderRoutes);
     * }</pre>
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target, or answers {@code null} for {@code 404}
     * @param routes    The routes of every located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    default <T> void locate(String prefixUri, LocatorHandler<? extends T> locator, LocatedRoutes<T> routes) {
        Objects.requireNonNull(routes, "routes");
        locate(prefixUri, locator, target -> routes);
    }

    /**
     * Route the requests under a prefix to the routes of a target located asynchronously, e.g.
     * loaded from a database: the same as {@link #locate(String, LocatorHandler, Function)}, but
     * the locator returns a stage of the target, and the router matches the rest of the path with
     * the routes of the target when the stage completes, without blocking the thread that matches
     * the request. See {@link AsyncLocatorHandler}.
     *
     * <pre>{@code
     * routes.locateAsync("/orders/{id}",
     *     (request, pathVariables) -> orders.findAsync(pathVariables.getLong("id")), // completes with null: 404
     *     order -> orderRoutes);
     * }</pre>
     *
     * <p>The filters of the located route, the error routes and the server filters apply like
     * for {@link #locate(String, LocatorHandler, Function)}; the located routes may locate again,
     * synchronously or asynchronously. The target is located once per request.
     * {@link io.micronaut.web.router.Router#findClosest} of an application with an asynchronous
     * locator that has not located its target yet fails: the server matches such a request
     * again when the stage completes. The server cancels the stage when the client closes the
     * connection before it completes, and a CORS preflight request of the prefix is answered
     * once the target is located, with the methods of its routes.</p>
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target later, or completes with {@code null} for {@code 404}
     * @param routesOf  The routes of a located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    <T> void locateAsync(String prefixUri, AsyncLocatorHandler<? extends T> locator, Function<? super T, ? extends LocatedRoutes<?>> routesOf);

    /**
     * Route the requests under a prefix to one set of routes of the targets a locator locates
     * asynchronously, see {@link #locateAsync(String, AsyncLocatorHandler, Function)}.
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target later, or completes with {@code null} for {@code 404}
     * @param routes    The routes of every located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    default <T> void locateAsync(String prefixUri, AsyncLocatorHandler<? extends T> locator, LocatedRoutes<T> routes) {
        Objects.requireNonNull(routes, "routes");
        locateAsync(prefixUri, locator, target -> routes);
    }
}
