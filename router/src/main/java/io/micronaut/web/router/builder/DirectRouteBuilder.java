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
import io.micronaut.http.HttpResponse;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Declares the direct routes of an {@link HttpDirectRoutes} bean: the routes the server answers
 * as soon as it has received a request, before it creates the {@link io.micronaut.http.HttpRequest},
 * without any filter.
 *
 * <pre>{@code
 * routes.GET("/probe/live", HttpResponse.ok("UP"));
 * routes.GET("/greetings/{name}").respond(direct -> HttpResponse.ok("Hello " + direct.pathVariables().getString("name")));
 * routes.GET("/health").order(-1).respond(HttpResponse.ok("UP"));
 * routes.path("/internal", internal -> internal.GET("/ping", HttpResponse.ok("pong")));
 * }</pre>
 *
 * <p>A direct route is declared in stages, like the routes of an
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder}: a creator, e.g. {@link #GET(String)},
 * gives the pending route, a {@link DirectRouteSpec}, which takes the conditions, the constraints,
 * the order and the executor of the route, and one terminal, which adds the route: a response given
 * as a value, which the route copies for each request, a function that composes the response of
 * each request from a {@link DirectContext}, or a function that completes it later. A function
 * that returns {@code null}, or a stage completed with {@code null}, declines the request, which
 * continues to the ordinary routes with its body untouched. A route with no settings that answers
 * with a value has a shortcut per method, e.g. {@link #GET(String, HttpResponse)}: unlike the
 * shortcuts of {@link HttpRouteScope}, e.g. {@link HttpRouteScope#GET(String, RequestHandler)},
 * which take a handler function, they take the response.</p>
 *
 * <p>A pending route that is not ended with a terminal fails the startup, naming the route and
 * the {@link HttpDirectRoutes} bean that declares it, once
 * {@link HttpDirectRoutes#routes(DirectRouteBuilder)} returned, or once the lambda of the
 * {@link #path(String, Consumer) prefix} that declares it returned.</p>
 *
 * <p>The builder has what a direct route can have, and nothing else: a direct route has no
 * filters, media types, annotations, attributes, error routes or port, and the builder has no
 * group, only a path {@link #path(String, Consumer) prefix}. Micronaut implements this
 * interface: the application uses it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@SuppressWarnings("MethodName")
public sealed interface DirectRouteBuilder permits DefaultDirectRouteBuilder {

    /**
     * Declare a direct route of {@code GET} requests, and the {@code HEAD} requests that no {@code HEAD} direct route matches, with the
     * headers of the response only: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec GET(String uri) {
        return route(HttpMethod.GET, uri);
    }

    /**
     * Declare a direct route of {@code POST} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec POST(String uri) {
        return route(HttpMethod.POST, uri);
    }

    /**
     * Declare a direct route of {@code PUT} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec PUT(String uri) {
        return route(HttpMethod.PUT, uri);
    }

    /**
     * Declare a direct route of {@code PATCH} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec PATCH(String uri) {
        return route(HttpMethod.PATCH, uri);
    }

    /**
     * Declare a direct route of {@code DELETE} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec DELETE(String uri) {
        return route(HttpMethod.DELETE, uri);
    }

    /**
     * Declare a direct route of {@code HEAD} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}. It wins over the {@code GET} direct route
     * of a path when they match a request equally well, and the server writes the headers of its
     * response only, with the {@code Content-Length} of its body, or the one it set when it has
     * none. A {@code HEAD} direct route that declines a request hands it to the ordinary routes,
     * not to the {@code GET} direct route.
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec HEAD(String uri) {
        return route(HttpMethod.HEAD, uri);
    }

    /**
     * Declare a direct route of {@code OPTIONS} requests: the pending route, to configure and to
     * end with a terminal, see {@link DirectRouteSpec}.
     *
     * <p>No filter runs for a direct route, the CORS filter neither: an {@code OPTIONS} direct
     * route also answers the CORS preflight requests of its paths, without the CORS headers, so a
     * browser rejects the cross-origin requests it covers. A route that must leave them to the
     * CORS filter excludes them with a condition:</p>
     *
     * <pre>{@code
     * routes.OPTIONS("/api/{+path}")
     *     .where(RouteCondition.not(RouteCondition.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD)))
     *     .respond(HttpResponse.ok().header(HttpHeaders.ALLOW, "GET, POST, OPTIONS"));
     * }</pre>
     *
     * @param uri The URI template
     * @return The pending route
     */
    default DirectRouteSpec OPTIONS(String uri) {
        return route(HttpMethod.OPTIONS, uri);
    }

    /**
     * Declare a direct route of the requests of a method: the pending route, see
     * {@link DirectRouteSpec}.
     *
     * @param method The method, a standard one
     * @param uri    The URI template
     * @return The pending route
     * @throws IllegalArgumentException if the method is {@link HttpMethod#CUSTOM}
     */
    DirectRouteSpec route(HttpMethod method, String uri);

    /**
     * Declare the direct routes of the requests of several methods: one route per method, which
     * the pending route configures together.
     *
     * @param methods The methods, standard ones
     * @param uri     The URI template
     * @return The pending routes
     * @throws IllegalArgumentException if there is no method, or one is {@link HttpMethod#CUSTOM}
     */
    DirectRouteSpec route(Set<HttpMethod> methods, String uri);

    /**
     * Answer {@code GET} requests with a response given as a value: a direct route with no
     * settings, the same as {@code GET(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void GET(String uri, HttpResponse<?> response) {
        GET(uri).respond(response);
    }

    /**
     * Answer {@code POST} requests with a response given as a value: a direct route with no
     * settings, the same as {@code POST(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void POST(String uri, HttpResponse<?> response) {
        POST(uri).respond(response);
    }

    /**
     * Answer {@code PUT} requests with a response given as a value: a direct route with no
     * settings, the same as {@code PUT(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void PUT(String uri, HttpResponse<?> response) {
        PUT(uri).respond(response);
    }

    /**
     * Answer {@code PATCH} requests with a response given as a value: a direct route with no
     * settings, the same as {@code PATCH(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void PATCH(String uri, HttpResponse<?> response) {
        PATCH(uri).respond(response);
    }

    /**
     * Answer {@code DELETE} requests with a response given as a value: a direct route with no
     * settings, the same as {@code DELETE(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void DELETE(String uri, HttpResponse<?> response) {
        DELETE(uri).respond(response);
    }

    /**
     * Answer {@code HEAD} requests with a response given as a value: a direct route with no
     * settings, the same as {@code HEAD(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void HEAD(String uri, HttpResponse<?> response) {
        HEAD(uri).respond(response);
    }

    /**
     * Answer {@code OPTIONS} requests with a response given as a value: a direct route with no
     * settings, the same as {@code OPTIONS(uri).respond(response)}.
     *
     * @param uri      The URI template
     * @param response The response
     */
    default void OPTIONS(String uri, HttpResponse<?> response) {
        OPTIONS(uri).respond(response);
    }

    /**
     * Declare routes under a path prefix, joined to their URI templates like the URI of a
     * controller and of its method. The prefix is all the routes share: a direct route has no
     * group settings. The prefixes of nested calls add up. A route declared in the lambda is
     * ended with a terminal before the lambda returns: otherwise the prefix fails with an
     * {@link IllegalStateException} naming the route.
     *
     * @param prefix The prefix, a path without a query
     * @param routes Declares the routes under the prefix
     */
    void path(String prefix, Consumer<DirectRouteBuilder> routes);
}
