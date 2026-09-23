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
import io.micronaut.http.HttpStatus;
import io.micronaut.web.router.RouteTable;
import io.micronaut.http.form.FormData;

import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds routes to handler functions: in an {@link HttpRoutes} bean, which adds them to the
 * application routes, or in a route table built at runtime. A handler route runs like a
 * controller route: argument binding, filters, error routes, executor selection, 405 / 415 / 406,
 * CORS and implicit {@code HEAD} routes all apply.
 *
 * <pre>{@code
 * routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id"))))
 *     .executeOn(TaskExecutors.BLOCKING);
 * routes.error(NoSuchFileException.class, (request, error) -> HttpResponse.notFound());
 * }</pre>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@SuppressWarnings("MethodName")
public interface HttpRouteBuilder {

    /**
     * Route a {@code GET} request to a handler function, with an implicit {@code HEAD} route
     * only if the route builder adds them.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec GET(String uri, RequestHandler handler) {
        return handle(HttpMethod.GET, uri, handler);
    }

    /**
     * Route a {@code POST} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec POST(String uri, RequestHandler handler) {
        return handle(HttpMethod.POST, uri, handler);
    }

    /**
     * Route a {@code PUT} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec PUT(String uri, RequestHandler handler) {
        return handle(HttpMethod.PUT, uri, handler);
    }

    /**
     * Route a {@code PATCH} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec PATCH(String uri, RequestHandler handler) {
        return handle(HttpMethod.PATCH, uri, handler);
    }

    /**
     * Route a {@code DELETE} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec DELETE(String uri, RequestHandler handler) {
        return handle(HttpMethod.DELETE, uri, handler);
    }

    /**
     * Route a {@code POST} request to a handler function that receives the decoded body.
     *
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     */
    default <B> HttpRouteSpec POST(String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return handle(HttpMethod.POST, uri, bodyType, handler);
    }

    /**
     * Route a {@code PUT} request to a handler function that receives the decoded body.
     *
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     */
    default <B> HttpRouteSpec PUT(String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return handle(HttpMethod.PUT, uri, bodyType, handler);
    }

    /**
     * Route requests to a handler function. The route runs like a controller method that takes
     * the request and returns a response: filters, error routes, body writers and executor
     * selection apply as they do for a blocking controller method. Like a controller route it
     * consumes JSON unless {@link HttpRouteSpec#consumes} says otherwise, and in {@link HttpRoutes} and
     * route tables its URI is under {@code micronaut.server.context-path}.
     *
     * @param method  The HTTP method, not {@link HttpMethod#CUSTOM}: a route of a custom method
     *                is declared by its name, see {@link #handle(String, String, RequestHandler)}
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @throws IllegalArgumentException if the method is {@link HttpMethod#CUSTOM}
     */
    HttpRouteSpec handle(HttpMethod method, String uri, RequestHandler handler);

    /**
     * Route requests to a handler function that receives the body decoded to the given type, like
     * a controller method with a {@code @Body} argument.
     * The body is required, unless the type is {@link Argument#isNullable() nullable}: then a
     * request without a body is handled with {@code null}.
     *
     * @param method   The HTTP method
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     */
    <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler);

    /**
     * Route a {@code POST} request with a submitted form to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec POST(String uri, FormRequestHandler handler) {
        return handleForm(HttpMethod.POST, uri, handler);
    }

    /**
     * Route a {@code PUT} request with a submitted form to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default HttpRouteSpec PUT(String uri, FormRequestHandler handler) {
        return handleForm(HttpMethod.PUT, uri, handler);
    }

    /**
     * Route requests with a submitted form, {@code application/x-www-form-urlencoded} or
     * {@code multipart/form-data}, to a handler function that receives the whole form as
     * {@link FormData}. The route consumes both form media types.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    HttpRouteSpec handleForm(HttpMethod method, String uri, FormRequestHandler handler);

    /**
     * Route a {@code GET} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default HttpRouteSpec asyncGET(String uri, AsyncRequestHandler handler) {
        return handleAsync(HttpMethod.GET, uri, handler);
    }

    /**
     * Route a {@code POST} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default HttpRouteSpec asyncPOST(String uri, AsyncRequestHandler handler) {
        return handleAsync(HttpMethod.POST, uri, handler);
    }

    /**
     * Route a {@code PUT} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default HttpRouteSpec asyncPUT(String uri, AsyncRequestHandler handler) {
        return handleAsync(HttpMethod.PUT, uri, handler);
    }

    /**
     * Route a {@code PATCH} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default HttpRouteSpec asyncPATCH(String uri, AsyncRequestHandler handler) {
        return handleAsync(HttpMethod.PATCH, uri, handler);
    }

    /**
     * Route a {@code DELETE} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default HttpRouteSpec asyncDELETE(String uri, AsyncRequestHandler handler) {
        return handleAsync(HttpMethod.DELETE, uri, handler);
    }

    /**
     * Route requests of several HTTP methods to one handler function: a route per method, which
     * the returned route configures together.
     *
     * @param methods The HTTP methods
     * @param uri     The URI template
     * @param handler The handler
     * @return The routes, to configure together
     * @see #handle(HttpMethod, String, RequestHandler)
     */
    HttpRouteSpec handle(Set<HttpMethod> methods, String uri, RequestHandler handler);

    /**
     * Route requests of several HTTP methods to one handler function that completes the response
     * later.
     *
     * @param methods The HTTP methods
     * @param uri     The URI template
     * @param handler The handler
     * @return The routes, to configure together
     * @see #handle(Set, String, RequestHandler)
     */
    HttpRouteSpec handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler);

    /**
     * Handle the exceptions of a type, and of its subtypes, with a handler function. Where it is
     * declared decides which routes it answers for:
     * <ul>
     *     <li>declared on the builder of an {@link HttpRoutes} bean, it is global, like an
     *     {@code @Error(global = true)} method: it answers requests to controller routes and
     *     handler routes that fail with such an exception;</li>
     *     <li>declared in a {@link HttpRouteGroup group}, it is local to the routes of the group
     *     and of its nested groups, including the routes they locate, like an {@code @Error}
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
     * that {@link HttpRouteSpec#implementing implements} a bean method first has the local
     * error routes of the bean class. An error of a request no route matched, e.g. a
     * {@code 404} or a {@code 405}, is answered by the global error routes only. A route table
     * built at runtime can declare error routes in its groups only.</p>
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
     * Bind a handler function to a declared route, e.g. a constant generated at compile time. The
     * route is registered with the keys of the declaration and built the first time the router
     * uses it; otherwise it is the same as {@link #handle(HttpMethod, String, RequestHandler)}.
     * A {@code GET} route gets an implicit {@code HEAD} route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     */
    HttpRouteSpec handle(RouteDeclaration route, RequestHandler handler);

    /**
     * Bind a handler function that receives the decoded body to a declared route.
     *
     * @param route    The declared route
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler);

    /**
     * Bind a handler function that completes the response later to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    HttpRouteSpec handleAsync(RouteDeclaration route, AsyncRequestHandler handler);

    /**
     * Bind a form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    HttpRouteSpec handleForm(RouteDeclaration route, FormRequestHandler handler);

    /**
     * Route requests to a handler function that completes the response later. The executor is
     * selected like for a controller method returning a {@code CompletionStage}. The handler
     * receives no decoded body: it reads the body with the methods of its
     * {@link io.micronaut.http.AsyncServerHttpRequest}, see {@link AsyncRequestHandler}.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler);

    /**
     * Route requests of a method by its name, including a custom HTTP method such as
     * {@code PROPFIND}, like a controller method annotated {@code @CustomHttpMethod}.
     *
     * @param httpMethodName The name of the HTTP method, a token
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
     * @throws IllegalArgumentException if the name is empty or not a token, e.g. blank
     * @see #handle(HttpMethod, String, RequestHandler)
     */
    HttpRouteSpec handle(String httpMethodName, String uri, RequestHandler handler);

    /**
     * Route requests of a method by its name, including a custom HTTP method, to a handler function
     * that receives the decoded body.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param bodyType       The body type
     * @param handler        The handler
     * @param <B>            The body type
     * @return The route
     * @see #handle(HttpMethod, String, Argument, BodyRequestHandler)
     */
    <B> HttpRouteSpec handle(String httpMethodName, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler);

    /**
     * Route requests of a method by its name, including a custom HTTP method, to a handler function
     * that completes the response later.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    HttpRouteSpec handleAsync(String httpMethodName, String uri, AsyncRequestHandler handler);

    /**
     * Route requests of a method by its name, including a custom HTTP method, with a submitted
     * form to a handler function that receives the whole form. The route consumes both form
     * media types.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
     * @see #handleForm(HttpMethod, String, FormRequestHandler)
     */
    HttpRouteSpec handleForm(String httpMethodName, String uri, FormRequestHandler handler);

    /**
     * Route the requests under a prefix to the routes of a target located at runtime, like a
     * JAX-RS sub-resource locator. The prefix, e.g. {@code /orders/{id}}, is matched for every
     * standard HTTP method, followed by the rest of the path, which is empty or starts with a
     * slash. When the router selects the locator route, it runs the locator, and matches the rest
     * of the path, e.g. {@code /items/3} of {@code /orders/5/items/3}, with the routes of the
     * table of the target: the request is handled by the route of that table, as if the table
     * were the application routes, with the path variables of the prefix and of the route, and
     * with the target, see {@link PathVariables#locatedTarget()}. A locator route of the table
     * locates again, from the rest of the path.
     *
     * <pre>{@code
     * RouteTable itemRoutes = tables.buildLocatedHttpRoutes(Order.class, items ->
     *     items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
     *         HttpResponse.ok(order.item(pathVariables.getInt("item")))));
     * routes.locate("/orders/{id}", (request, pathVariables) -> orders.find(pathVariables.getLong("id")), order -> itemRoutes);
     * }</pre>
     *
     * <p>The type of the target is the type the locator returns: the route table function
     * receives it, e.g. to choose the table of a subtype. A table built for a type, see
     * {@link io.micronaut.web.router.RouteTableFactory#buildLocatedHttpRoutes(Class, Consumer)},
     * gives its handlers the target of that type; a located target that is not an instance of
     * the type of its table fails the request, answered by the error routes like a failed
     * controller method.</p>
     *
     * <p>When the table has no route for the rest of the path, the request is answered like a
     * request that no route of the table matches: {@code 404}, or {@code 405}, {@code 415} or
     * {@code 406} if a route of the table matches the path with another method or media type.
     * No other route of the application is tried. The filters of the matched route of the table
     * apply, and the server filters apply to the full path.</p>
     *
     * <p>The locator runs while the request is matched, see {@link LocatorHandler}. Its tables
     * are built with {@link io.micronaut.web.router.RouteTableFactory#buildLocatedHttpRoutes},
     * whose URIs are relative to the prefix, and should be built once per type of target, not per
     * request: the target reaches the handlers as an argument, see {@link LocatedHttpRouteBuilder},
     * or through {@link PathVariables#locatedTarget()}.
     * Requests with a custom HTTP method are not located.</p>
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target, or answers {@code null} for {@code 404}
     * @param tables    The route table of a located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    <T> void locate(String prefixUri, LocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables);

    /**
     * Route the requests under a prefix to the routes of a target located asynchronously, e.g.
     * loaded from a database: the same as {@link #locate(String, LocatorHandler, Function)}, but
     * the locator returns a stage of the target, and the router matches the rest of the path with
     * the routes of the table of the target when the stage completes, without blocking the thread
     * that matches the request. See {@link AsyncLocatorHandler}.
     *
     * <pre>{@code
     * RouteTable itemRoutes = tables.buildLocatedHttpRoutes(Order.class, items ->
     *     items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
     *         HttpResponse.ok(order.item(pathVariables.getInt("item")))));
     * routes.locateAsync("/orders/{id}",
     *     (request, pathVariables) -> orders.findAsync(pathVariables.getLong("id")), // completes with null: 404
     *     order -> itemRoutes);
     * }</pre>
     *
     * <p>The filters of the located route, the error routes and the server filters apply like
     * for {@link #locate(String, LocatorHandler, Function)}; a table of a located target may
     * locate again, synchronously or asynchronously. The target is located once per request.
     * {@link io.micronaut.web.router.Router#findClosest} of an application with an asynchronous
     * locator that has not located its target yet fails: the server matches such a request
     * again when the stage completes.</p>
     *
     * @param prefixUri The URI template of the prefix
     * @param locator   Locates the target later, or completes with {@code null} for {@code 404}
     * @param tables    The route table of a located target
     * @param <T>       The type of the target
     * @since 5.3.0
     */
    <T> void locateAsync(String prefixUri, AsyncLocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables);

    /**
     * Declare a server filter, the functional form of a {@code @ServerFilter} bean: it filters
     * every request whose path matches one of the patterns, e.g. {@code /**} or {@code /api/**},
     * whatever answers it, a controller, a handler route or a static resource, including the
     * requests no route matches, ordered together with the filter beans. It is global wherever it
     * is declared: the prefix and the filters of a {@link HttpRouteGroup} do not apply to it.
     *
     * <pre>{@code
     * routes.filter("/**").order(100).before((request, propagatedContext) -> {
     *     propagatedContext.add(new MdcPropagationContext(Map.of("path", request.getPath())));
     *     return null;
     * });
     * }</pre>
     *
     * <p>Each call declares a new server filter. The server filters of an {@link HttpRoutes} bean
     * are read when the router is built. A route table built at runtime cannot declare them.</p>
     *
     * @param patterns The patterns of the paths to filter, in the {@link ServerFilterSpec#patternStyle style} of the filter, {@code ANT} by default
     * @return The server filter, to declare its filters on
     * @see ServerFilterSpec
     * @since 5.3.0
     */
    ServerFilterSpec filter(String... patterns);

    /**
     * Declare a group of routes, whose filters apply to every route declared in the lambda,
     * e.g. to filter every route of an {@link HttpRoutes} bean. The builder of an
     * {@link HttpRoutes} bean has no route filter methods of its own, as it is shared by the beans:
     * the group is the scope of the filters, see {@link #filter(String...)} for a server filter.
     *
     * <pre>{@code
     * routes.group(all -> {
     *     all.before((request, propagatedContext) -> {
     *         propagatedContext.add(new MdcPropagationContext(Map.of("path", request.getPath())));
     *         return null;
     *     });
     *     all.GET("/orders", ordersHandler);
     *     all.GET("/customers", customersHandler);
     * });
     * }</pre>
     *
     * <p>See {@link HttpRouteGroup} for which routes the filters apply to, and in which order.</p>
     *
     * @param routes Declares the routes and the filters of the group
     * @since 5.3.0
     */
    void group(Consumer<HttpRouteGroup> routes);

    /**
     * Declare a group of routes under a prefix: the URI template of every route of the group,
     * including its locator routes and the routes of its nested groups, is the prefix followed by
     * the URI template of the route, like the URI of a controller method under the URI of the
     * controller: {@code path("/api", api -> api.GET("/orders", handler))} routes
     * {@code GET /api/orders}. The prefixes of nested groups add up. The filters of the group apply
     * to every route declared in the lambda, see {@link HttpRouteGroup}.
     *
     * <p>The prefix is a path: it may have path variables, e.g. {@code /tenants/{tenant}}, but no
     * query or fragment. A {@link RouteDeclaration}, whose index keys are computed for its own URI
     * template, cannot be bound in a group with a prefix.</p>
     *
     * @param prefix The prefix of the URI templates of the routes of the group
     * @param routes Declares the routes and the filters of the group
     * @since 5.3.0
     */
    void path(String prefix, Consumer<HttpRouteGroup> routes);

    /**
     * A body type that is {@code null} when the request has no body, for the handlers that
     * receive the decoded body: {@code routes.POST(uri, HttpRouteBuilder.nullableBody(Argument.of(Item.class)), handler)},
     * and for the body an asynchronous handler reads:
     * {@code request.body(HttpRouteBuilder.nullableBody(Argument.of(Item.class)))}.
     *
     * @param bodyType The body type
     * @param <T>      The type
     * @return The nullable body type, with the annotations of the given one
     */
    static <T> Argument<T> nullableBody(Argument<T> bodyType) {
        return HandlerMethod.nullable(bodyType);
    }

    /**
     * A body type that is {@code null} when the request has no body.
     *
     * @param type The type
     * @param <T>  The type
     * @return The nullable body type
     * @see #nullableBody(Argument)
     */
    static <T> Argument<T> nullableBody(Class<T> type) {
        return nullableBody(Argument.of(type));
    }

}
