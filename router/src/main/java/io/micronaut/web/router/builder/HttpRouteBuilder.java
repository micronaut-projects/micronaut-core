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
import io.micronaut.web.router.Route;
import io.micronaut.http.form.FormData;
import io.micronaut.http.form.FormParts;

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
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
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
     * Route requests with a submitted form to a handler function that completes the response
     * later. The whole form is read before the handler runs, as for
     * {@link #handleForm(HttpMethod, String, FormRequestHandler)}.
     *
     * @param method  The HTTP method
     * @param uri     The URI template
     * @param handler The handler
     * @return The route
     */
    HttpRouteSpec handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler);

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
    HttpRouteSpec handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler);

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
     * Handle the exceptions of a type, and of its subtypes, with a handler function, like an
     * {@code @Error(global = true)} method: it answers requests to controller routes and handler
     * routes that fail with such an exception.
     *
     * @param type    The type of the exception
     * @param handler The handler
     * @param <E>     The type of the exception
     * @return The error route
     */
    <E extends Throwable> ErrorRouteSpec error(Class<E> type, ErrorRouteHandler<E> handler);

    /**
     * Handle the responses of a status with a handler function, like an
     * {@code @Error(status = ..., global = true)} method, e.g. to answer {@code 404}.
     *
     * @param status  The status
     * @param handler The handler
     * @return The status route
     */
    StatusRouteSpec status(HttpStatus status, StatusRouteHandler handler);

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
     * Bind an asynchronous form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    HttpRouteSpec handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler);

    /**
     * Bind a streaming form handler function to a declared route.
     *
     * @param route   The declared route
     * @param handler The handler
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    HttpRouteSpec handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler);

    /**
     * Route requests to a handler function that completes the response later. The executor is
     * selected like for a controller method returning a {@code CompletionStage}.
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
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
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
     * Route requests of a method by its name, including a custom HTTP method, to a handler function
     * that receives the decoded body and completes the response later.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param bodyType       The body type
     * @param handler        The handler
     * @param <B>            The body type
     * @return The route
     * @see #handleAsync(HttpMethod, String, Argument, AsyncBodyRequestHandler)
     */
    <B> HttpRouteSpec handleAsync(String httpMethodName, String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler);

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
     * Route requests of a method by its name, including a custom HTTP method, with a submitted
     * form to a handler function that completes the response later.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
     * @see #handleFormAsync(HttpMethod, String, AsyncFormRequestHandler)
     */
    HttpRouteSpec handleFormAsync(String httpMethodName, String uri, AsyncFormRequestHandler handler);

    /**
     * Route requests of a method by its name, including a custom HTTP method, with a submitted
     * form to a handler function that reads the form as it arrives.
     *
     * @param httpMethodName The name of the HTTP method
     * @param uri            The URI template
     * @param handler        The handler
     * @return The route
     * @see #handleFormStream(HttpMethod, String, StreamingFormRequestHandler)
     */
    HttpRouteSpec handleFormStream(String httpMethodName, String uri, StreamingFormRequestHandler handler);

    /**
     * A body type that is {@code null} when the request has no body, for the handlers that
     * receive the decoded body: {@code routes.POST(uri, HttpRouteBuilder.nullableBody(Argument.of(Item.class)), handler)}.
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

    /**
     * Route requests to a handler function that receives the body decoded to the given type and
     * completes the response later: a {@link #handle(HttpMethod, String, Argument, BodyRequestHandler)}
     * route whose handler returns a {@code CompletionStage}.
     * A {@link Argument#isNullable() nullable} body type is {@code null} without a body.
     *
     * @param method   The HTTP method
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     */
    <B> HttpRouteSpec handleAsync(HttpMethod method, String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler);

    /**
     * Bind a handler function that receives the decoded body and completes the response later to
     * a declared route.
     *
     * @param route    The declared route
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route, to configure further
     * @see #handle(RouteDeclaration, RequestHandler)
     */
    <B> HttpRouteSpec handleAsync(RouteDeclaration route, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler);

    /**
     * Route a {@code POST} request to a handler function that receives the body decoded to the
     * given type and completes the response later.
     *
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @see #handleAsync(HttpMethod, String, Argument, AsyncBodyRequestHandler)
     */
    default <B> HttpRouteSpec asyncPOST(String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return handleAsync(HttpMethod.POST, uri, bodyType, handler);
    }

    /**
     * Route a {@code PUT} request to a handler function that receives the body decoded to the
     * given type and completes the response later.
     *
     * @param uri      The URI template
     * @param bodyType The body type
     * @param handler  The handler
     * @param <B>      The body type
     * @return The route
     * @see #handleAsync(HttpMethod, String, Argument, AsyncBodyRequestHandler)
     */
    default <B> HttpRouteSpec asyncPUT(String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return handleAsync(HttpMethod.PUT, uri, bodyType, handler);
    }

}
