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
import io.micronaut.web.router.RouteTable;

import java.util.Objects;
import java.util.function.Function;

/**
 * The builder of the route table of located targets of a type, see
 * {@link io.micronaut.web.router.RouteTableFactory#buildLocatedHttpRoutes(Argument, java.util.function.Consumer)}:
 * besides the routes of an {@link HttpRouteBuilder}, whose handlers read the target with
 * {@link PathVariables#locatedTarget(Class)}, it routes to handlers that receive the target as an
 * argument, of the type of the table.
 *
 * <pre>{@code
 * RouteTable itemRoutes = tables.buildLocatedHttpRoutes(Order.class, items -> {
 *     items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
 *         HttpResponse.ok(order.item(pathVariables.getInt("item"))));
 *     items.handle(HttpMethod.POST, "/items", Argument.of(Item.class), (request, pathVariables, order, item) ->
 *         HttpResponse.created(order.add(item)));
 * });
 * routes.locate("/orders/{id}", (request, pathVariables) -> orders.find(pathVariables.getLong("id")), order -> itemRoutes);
 * }</pre>
 *
 * <p>The router checks the target a locator located against the type of the table it chose for
 * it: a target that is not an instance of the type fails the request, answered by the error
 * routes like a failed controller method. The typed handlers have no shortcuts such as
 * {@code GET}: {@code POST(uri, (request, pathVariables, form) -> ...)} would be ambiguous with
 * the form handlers. The handlers of the groups of the table, see {@link #group}, read the
 * target with {@link PathVariables#locatedTarget(Class)}.</p>
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpRouteBuilder<T> extends HttpRouteBuilder permits DefaultLocatedHttpRouteBuilder {

    /**
     * @return The type of the located targets of the table
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
     * Route requests to a handler function that receives the located target and completes the
     * response later.
     *
     * @param method  The HTTP method
     * @param uri     The URI template, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleAsync(HttpMethod, String, AsyncRequestHandler)
     */
    HttpRouteSpec handleAsync(HttpMethod method, String uri, LocatedAsyncRequestHandler<T> handler);

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
     * Bind a handler function that receives the located target and completes the response later
     * to a declared route.
     *
     * @param route   The declared route, relative to the prefix of the locator
     * @param handler The handler
     * @return The route
     * @see #handleAsync(RouteDeclaration, AsyncRequestHandler)
     */
    HttpRouteSpec handleAsync(RouteDeclaration route, LocatedAsyncRequestHandler<T> handler);

    /**
     * Route the requests under a prefix to the routes of a target that a locator locates from the
     * located target of this table, e.g. a child of a node of a tree.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of this table
     * @param locator   Locates the target from the target of this table, or answers {@code null} for {@code 404}
     * @param tables    The route table of a located target
     * @param <U>       The type of the target the locator locates
     * @see #locate(String, LocatorHandler, Function)
     */
    <U> void locate(String prefixUri, LocatedLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables);

    /**
     * Route the requests under a prefix to the routes of a target that a locator locates later
     * from the located target of this table.
     *
     * @param prefixUri The URI template of the prefix, relative to the prefix of the locator of this table
     * @param locator   Locates the target later from the target of this table, or completes with {@code null} for {@code 404}
     * @param tables    The route table of a located target
     * @param <U>       The type of the target the locator locates
     * @see #locateAsync(String, AsyncLocatorHandler, Function)
     */
    <U> void locateAsync(String prefixUri, LocatedAsyncLocatorHandler<T, ? extends U> locator, Function<? super U, RouteTable> tables);
}
