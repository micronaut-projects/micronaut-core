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
/**
 * Functional routes: routes to handler functions declared in code, next to the controllers.
 *
 * <p>A bean of type {@link io.micronaut.web.router.builder.HttpRoutes} declares routes with an
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder} when the router is created. A handler
 * route runs like a controller route: argument binding, the message body readers and writers,
 * content negotiation, the application's filters, CORS, the error routes, the executors and the
 * propagated context all apply.</p>
 *
 * <pre>{@code
 * @Singleton
 * class OrderRoutes implements HttpRoutes {
 *     private final OrderRepository orders;
 *
 *     OrderRoutes(OrderRepository orders) {
 *         this.orders = orders;
 *     }
 *
 *     @Override
 *     public void routes(HttpRouteBuilder routes) {
 *         routes.path("/orders", group -> {
 *             group.beforeReplacing(request -> request.getHeaders().contains("X-Tenant") ? null : HttpResponse.badRequest());
 *             group.GET("/{id}", (request, pathVariables) -> HttpResponse.ok(orders.find(pathVariables.getLong("id"))));
 *             group.POST("/", Order.class, (request, pathVariables, order) -> HttpResponse.created(orders.save(order)));
 *             group.asyncPOST("/import", (request, pathVariables, body) -> body.elements(Order.class)
 *                 .forEach(orders::saveAsync)
 *                 .thenApply(done -> HttpResponse.accepted()));
 *             group.error(NoSuchOrderException.class, (request, error) -> HttpResponse.notFound());
 *         });
 *         routes.filter("/**").after((request, response) -> response.header("X-Served-By", "orders"));
 *     }
 * }
 * }</pre>
 *
 * <p>The main types:</p>
 * <ul>
 *     <li>{@link io.micronaut.web.router.builder.HttpRouteBuilder}: routes per HTTP method, by
 *     method name, with a decoded body, a form or an asynchronous handler; error and status
 *     routes; locator routes; server filters; groups;</li>
 *     <li>{@link io.micronaut.web.router.builder.HttpRouteSpec}: the configuration of a route
 *     (media types, executor, annotations, conditions, port, order, attributes) and its filters,
 *     from {@link io.micronaut.web.router.builder.RouteFilterSpec};</li>
 *     <li>{@link io.micronaut.web.router.builder.HttpRouteGroup}: a group of routes with a path
 *     prefix, filters, conditions, error routes and settings common to its routes;</li>
 *     <li>{@link io.micronaut.web.router.builder.ServerFilterSpec}: the functional form of a
 *     {@code @ServerFilter} bean, optionally pre-matching;</li>
 *     <li>the handler functions ({@link io.micronaut.web.router.builder.RequestHandler},
 *     {@link io.micronaut.web.router.builder.BodyRequestHandler},
 *     {@link io.micronaut.web.router.builder.FormRequestHandler},
 *     {@link io.micronaut.web.router.builder.AsyncRequestHandler},
 *     {@link io.micronaut.web.router.builder.AsyncBodyRequestHandler}, which reads the
 *     {@link io.micronaut.http.body.AsyncRequestBody}, the error, status and locator handlers)
 *     and the route filter functions;</li>
 *     <li>{@link io.micronaut.web.router.builder.PathVariables}, the typed path variables of the
 *     matched route, and {@link io.micronaut.web.router.builder.RequestPredicates}, conditions
 *     for {@code where(...)};</li>
 *     <li>{@link io.micronaut.web.router.builder.RouteDeclaration}, a route declared apart from
 *     its handler, e.g. generated at compile time.</li>
 * </ul>
 *
 * <p>The builders, the route, group, error, status and server filter specs and the path
 * variables are sealed: Micronaut implements them, the application uses them. The application
 * implements {@link io.micronaut.web.router.builder.HttpRoutes}, the handler and filter
 * functions, usually as lambdas, and may implement
 * {@link io.micronaut.web.router.builder.RouteDeclaration}.</p>
 *
 * <p>Routes that change while the application runs are built with
 * {@link io.micronaut.web.router.RouteTableFactory#buildHttpRoutes(io.micronaut.web.router.builder.HttpRoutes)}
 * and published by a {@link io.micronaut.web.router.RouteSource}. See the "Functional Routes"
 * section of the user guide.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@NullMarked
package io.micronaut.web.router.builder;

import org.jspecify.annotations.NullMarked;
