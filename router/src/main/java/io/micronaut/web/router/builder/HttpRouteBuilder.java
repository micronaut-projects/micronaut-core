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
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Builds routes to handler functions: in an {@link HttpRoutes} bean, which adds them to the
 * application routes. A handler route runs like a
 * controller route: argument binding, filters, error routes, executor selection, 405 / 415 / 406,
 * CORS and implicit {@code HEAD} routes all apply.
 *
 * <p>A route is declared in stages: a creator, e.g. {@link #GET(String)}, gives the pending
 * route, an {@link HttpRouteSpec}, which takes the settings of the route, then optionally a body
 * stage, and one terminal, the handler or the response of the route, which adds the route. A
 * route with no settings has a shortcut per method, e.g. {@link #GET(String, RequestHandler)}.</p>
 *
 * <pre>{@code
 * routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id"))));
 * routes.GET("/items/{id}/name")
 *     .produces(MediaType.TEXT_PLAIN_TYPE)
 *     .executeOn(TaskExecutors.BLOCKING)
 *     .handle((request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id")).name()));
 * routes.POST("/items").body(Item.class).handle((request, pathVariables, item) -> HttpResponse.created(items.save(item)));
 * routes.error(NoSuchFileException.class, (request, error) -> HttpResponse.notFound());
 * }</pre>
 *
 * <p>A pending route that is not ended with a terminal fails the startup: an
 * {@link IllegalStateException} names the route and the {@link HttpRoutes} bean that declares it,
 * once {@link HttpRoutes#routes(HttpRouteBuilder)} returned, or once the lambda of the group that
 * declares it returned.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface HttpRouteBuilder extends HttpRouteScope permits DefaultHttpRouteBuilder {

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
     * Declare a server filter, the functional form of a {@code @ServerFilter} bean: it filters
     * every request whose path matches one of the patterns, e.g. {@code /**} or {@code /api/**},
     * whatever answers it, a controller, a handler route or a static resource, including the
     * requests no route matches, ordered together with the filter beans. It is global: only the
     * builder of an {@link HttpRoutes} bean declares server filters, a {@link HttpRouteGroup} declares
     * route filters, which apply to its routes only.
     *
     * <pre>{@code
     * routes.serverFilter("/**").order(100).beforeReplacing((request, propagatedContext) -> {
     *     propagatedContext.add(new MdcPropagationContext(Map.of("path", request.getPath())));
     *     return null;
     * });
     * }</pre>
     *
     * <p>Each call declares a new server filter. The server filters of an {@link HttpRoutes} bean
     * are read when the router is built.</p>
     *
     * @param patterns The patterns of the paths to filter, in the {@link ServerFilterSpec#patternStyle style} of the filter, {@code ANT} by default
     * @return The server filter, to declare its filters on
     * @see ServerFilterSpec
     * @since 5.3.0
     */
    ServerFilterSpec serverFilter(String... patterns);

    /**
     * A body type that is {@code null} when the request has no body, for the handlers that
     * receive the decoded body: {@code routes.POST(uri).body(HttpRouteBuilder.nullableBody(Argument.of(Item.class))).handle(handler)},
     * and for the body an asynchronous handler reads:
     * {@code body.body(HttpRouteBuilder.nullableBody(Argument.of(Item.class)))}.
     *
     * @param bodyType The body type
     * @param <T>      The type
     * @return The nullable body type, with the annotations of the given one
     */
    static <T> Argument<@Nullable T> nullableBody(Argument<T> bodyType) {
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
    static <T> Argument<@Nullable T> nullableBody(Class<T> type) {
        return nullableBody(Argument.of(type));
    }
}
