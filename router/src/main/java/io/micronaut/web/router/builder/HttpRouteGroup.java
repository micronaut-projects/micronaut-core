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

/**
 * A group of routes, declared with {@link HttpRouteBuilder#group} or, under a prefix, with
 * {@link HttpRouteBuilder#path}: the routes declared on the group, and in the groups nested in it,
 * are the routes of the group, and the filters of the group apply to each of them.
 *
 * <pre>{@code
 * routes.path("/api", api -> {
 *     api.GET("/orders", (request, pathVariables) -> HttpResponse.ok(orders.all()));
 *     api.path("/admin", admin -> {
 *         admin.GET("/users", (request, pathVariables) -> HttpResponse.ok(users.all()));
 *         admin.before(request -> isAdmin(request) ? null : HttpResponse.forbidden());
 *     });
 *     api.before((request, propagatedContext) -> {
 *         propagatedContext.add(new MdcPropagationContext(Map.of("tenant", tenantOf(request))));
 *         return null;
 *     });
 * });
 * }</pre>
 *
 * <p><b>Coverage.</b> A filter of the group applies to every route declared in the lambda of the
 * group, wherever the filter is declared in it: before the routes, after them, or in between.
 * The filters are resolved when the routes are built. The group is closed when its lambda
 * returns: declaring a route or a filter on it afterwards fails.</p>
 *
 * <p><b>Order.</b> The application's filters, the {@code @ServerFilter} beans, run first. Then the
 * filters of the outer group, then those of the inner groups, then those of the route, then the
 * handler. Response filters run the other way: the filters of the route, then those of the inner
 * groups, then those of the outer group, then the application's response filters. Within a group
 * or a route, request filters and response filters each run in the order they are declared, like
 * the filters of a route.</p>
 *
 * <p><b>Matched routes only.</b> A filter of the group runs only when a route of the group
 * matched the request. A request under the prefix of the group that no route answers, a
 * {@code 404}, or one that a route of the group would answer with another method or media type, a
 * {@code 405}, {@code 415} or {@code 406}, does not run the filters of the group; a
 * {@code @ServerFilter("/api/**")} bean filters every request under a prefix.</p>
 *
 * <p><b>Errors.</b> An exception of a route of the group is answered by the error routes, and the
 * response filters of the group, like those of the route, filter the response of the error route. The error and status routes declared on a group,
 * {@link #error}, {@link #errorAsync}, {@link #status} and {@link #statusAsync}, are global, like
 * the ones declared on the builder of the {@link HttpRoutes} bean: they answer the requests of
 * every route, and the group neither prefixes nor filters them.</p>
 *
 * <p><b>Locators.</b> A {@link #locate locator route} declared in a group is under the prefix of
 * the group, and the filters of the group apply to every route of the located tables, before the
 * filters of the located route.</p>
 *
 * <p><b>Declared routes.</b> A {@link RouteDeclaration} is bound with its own URI template: its
 * index keys are computed for that template, e.g. at compile time. It can be bound in a group
 * without a prefix, whose filters apply to it, but not in a group with a prefix.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface HttpRouteGroup extends HttpRouteBuilder, RouteFilterSpec<HttpRouteGroup> {
}
