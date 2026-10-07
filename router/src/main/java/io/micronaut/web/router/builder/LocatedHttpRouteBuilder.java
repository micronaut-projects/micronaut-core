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
import io.micronaut.http.PathVariables;

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
 * controller method. The groups of the routes, see {@link #group}, are groups of the routes of the
 * same targets: their routes and locators receive the target too.</p>
 *
 * <p>The error and status routes of located routes are declared in a group, local to its routes:
 * located routes declare no global error or status route, no server filter and no port, which
 * belong to the application routes. Declaring a global error or status route, or a port, fails
 * when it is declared.</p>
 *
 * @param <T> The type of the located target
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface LocatedHttpRouteBuilder<T> extends LocatedHttpRouteScope<T> permits DefaultLocatedHttpRouteBuilder {
}
