/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.web.router.AsyncFormRequestHandler;
import io.micronaut.web.router.AsyncRequestHandler;
import io.micronaut.web.router.BodyRequestHandler;
import io.micronaut.web.router.ErrorRouteHandler;
import io.micronaut.web.router.FormRequestHandler;
import io.micronaut.web.router.HttpRoutes;
import io.micronaut.web.router.RequestHandler;
import io.micronaut.web.router.RouteDeclaration;
import io.micronaut.web.router.StatusRouteHandler;
import io.micronaut.web.router.StreamingFormRequestHandler;

import java.util.Set;

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
public interface RouteBuilder {

    /**
     * Route a {@code GET} request to a handler function, with an implicit {@code HEAD} route
     * only if the route builder adds them.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute GET(String uri, RequestHandler handler) {
        return handle(HttpMethod.GET, uri, handler);
    }

    /**
     * Route a {@code POST} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute POST(String uri, RequestHandler handler) {
        return handle(HttpMethod.POST, uri, handler);
    }

    /**
     * Route a {@code PUT} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute PUT(String uri, RequestHandler handler) {
        return handle(HttpMethod.PUT, uri, handler);
    }

    /**
     * Route a {@code PATCH} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute PATCH(String uri, RequestHandler handler) {
        return handle(HttpMethod.PATCH, uri, handler);
    }

    /**
     * Route a {@code DELETE} request to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute DELETE(String uri, RequestHandler handler) {
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
    default <B> UriRoute POST(String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
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
    default <B> UriRoute PUT(String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return handle(HttpMethod.PUT, uri, bodyType, handler);
    }

    /**
     * Route requests to a handler function. The route runs like a controller method that takes
     * the request and returns a response: filters, error routes, body writers and executor
     * selection apply as they do for a blocking controller method. Like a controller route it
     * consumes JSON unless {@link UriRoute#consumes} says otherwise, and in {@link HttpRoutes} and
     * route tables its URI is under {@code micronaut.server.context-path}.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    UriRoute handle(HttpMethod method, String uri, RequestHandler handler);

    /**
     * Route requests to a handler function that receives the body decoded to the given type, like
     * a controller method with a {@code @Body} argument.
     *
     * @param method   The HTTP method
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     */
    <B> UriRoute handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler);

    /**
     * Route a {@code POST} request with a submitted form to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute POST(String uri, FormRequestHandler handler) {
        return handleForm(HttpMethod.POST, uri, handler);
    }

    /**
     * Route a {@code PUT} request with a submitted form to a handler function.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    default UriRoute PUT(String uri, FormRequestHandler handler) {
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
    UriRoute handleForm(HttpMethod method, String uri, FormRequestHandler handler);

    /**
     * Route requests with a submitted form to a handler function that completes the response
     * later. The whole form is read before the handler runs, as for
     * {@link #handleForm(HttpMethod, String, FormRequestHandler)}.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    UriRoute handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler);

    /**
     * Route requests with a submitted form to a handler function that reads the form as it
     * arrives, part by part, with {@link FormParts#forEach}. The route consumes both form media
     * types.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    UriRoute handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler);

    /**
     * Route a {@code GET} request to a handler function that completes the response later.
     *
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    default UriRoute asyncGET(String uri, AsyncRequestHandler handler) {
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
    default UriRoute asyncPOST(String uri, AsyncRequestHandler handler) {
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
    default UriRoute asyncPUT(String uri, AsyncRequestHandler handler) {
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
    default UriRoute asyncPATCH(String uri, AsyncRequestHandler handler) {
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
    default UriRoute asyncDELETE(String uri, AsyncRequestHandler handler) {
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
    UriRoute handle(Set<HttpMethod> methods, String uri, RequestHandler handler);

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
    UriRoute handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler);

    /**
     * Handle the exceptions of a type, and of its subtypes, with a handler function, like an
     * {@code @Error(global = true)} method: it answers requests to controller routes and handler
     * routes that fail with such an exception.
     *
     * @param type    The type of the exception
     * @param handler The handler
     * @param <E>     The type of the exception
     * @return The error route
     */
    <E extends Throwable> ErrorRoute error(Class<E> type, ErrorRouteHandler<E> handler);

    /**
     * Handle the responses of a status with a handler function, like an
     * {@code @Error(status = ..., global = true)} method, e.g. to answer {@code 404}.
     *
     * @param status  The status
     * @param handler The handler
     * @return The status route
     */
    StatusRoute status(HttpStatus status, StatusRouteHandler handler);

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
    UriRoute handle(RouteDeclaration route, RequestHandler handler);

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
    <B> UriRoute handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler);

    /**
     * Bind a handler function that completes the response later to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    UriRoute handleAsync(RouteDeclaration route, AsyncRequestHandler handler);

    /**
     * Bind a form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    UriRoute handleForm(RouteDeclaration route, FormRequestHandler handler);

    /**
     * Bind an asynchronous form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    UriRoute handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler);

    /**
     * Bind a streaming form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    UriRoute handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler);

    /**
     * Route requests to a handler function that completes the response later. The executor is
     * selected like for a controller method returning a {@code CompletionStage}.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    UriRoute handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler);

}
