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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;

import java.util.function.Predicate;

/**
 * A route to a handler function, to configure after it was added with the {@link HttpRouteBuilder}.
 * A route on several HTTP methods configures all of them.
 *
 * <p>The filters of the route, see {@link RouteFilterSpec}, run after the application's filters and
 * the filters of the groups the route is declared in, closest to the route, and are resolved when
 * the route is built.</p>
 *
 * <p>The configuration of the route is read when the router is built, once the routes were
 * declared: configure the route where it is declared, in {@link HttpRoutes#routes(HttpRouteBuilder)}
 * or in the callback that builds a route table. A change made to a route kept after that is
 * ignored.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface HttpRouteSpec extends RouteFilterSpec<HttpRouteSpec> {

    /**
     * Accept requests with these media types only, like {@code @Consumes} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec consumes(MediaType... mediaTypes);

    /**
     * Accept requests with any media type.
     *
     * @return The route
     */
    HttpRouteSpec consumesAll();

    /**
     * Produce these media types, like {@code @Produces} on a controller method.
     *
     * @param mediaTypes The media types
     * @return The route
     */
    HttpRouteSpec produces(MediaType... mediaTypes);

    /**
     * Give the route annotations: the features that read the annotations of the matched route,
     * such as security rules, versioning, filter binding and the message body writers, see them
     * as if they were on a controller method, and so does the return type of the route. Typically
     * they are the annotations of the bean method the handler implements, from its
     * {@link io.micronaut.inject.ExecutableMethod}.
     *
     * @param annotationMetadata The annotations of the route
     * @return The route
     */
    HttpRouteSpec annotationMetadata(AnnotationMetadata annotationMetadata);

    /**
     * The route implements a bean method, e.g. a method of a resource that a framework
     * integration routes with handler functions: the route has the annotations of the method, see
     * {@link #annotationMetadata(AnnotationMetadata)}, and its target method, declaring type and
     * method name are the ones of the bean method. The arguments of the route stay those of the
     * handler.
     *
     * @param method The bean method
     * @return The route
     */
    HttpRouteSpec implementing(ExecutableMethod<?, ?> method);

    /**
     * Run the route on the named executor, like {@code @ExecuteOn} on a controller method. It
     * applies whatever the thread selection of the server.
     *
     * @param executorName The name of the executor, e.g. {@code TaskExecutors.BLOCKING}
     * @return The route
     */
    HttpRouteSpec executeOn(String executorName);

    /**
     * Run the route on the event loop, like {@code @NonBlocking} on a controller method, when the
     * server selects threads automatically. The route must not block.
     *
     * @return The route
     */
    HttpRouteSpec nonBlocking();

    /**
     * Route the requests on this port only, like {@code @Controller(port = ...)}: the server opens
     * the port when it starts, and the route does not match a request on another port, which is
     * answered as if the route did not exist. A route without a port matches the requests on the
     * default ports of the server only, once a route of the application has a port. The port of
     * the route overrides the port of its {@link HttpRouteGroup#port(int) group}.
     *
     * <pre>{@code
     * routes.GET("/metrics", (request, pathVariables) -> HttpResponse.ok(metrics.scrape()))
     *     .port(9090);
     * }</pre>
     *
     * <p>A route table built at runtime cannot open a port: its routes cannot have one.</p>
     *
     * @param port The port, between {@code 1} and {@code 65535}: unlike
     *             {@code @Controller(port = ...)}, a negative port or {@code 0}, a random port the
     *             route could not match, is rejected
     * @return The route
     * @throws IllegalArgumentException if the port is not between {@code 1} and {@code 65535}
     * @since 5.3.0
     */
    HttpRouteSpec port(int port);

    /**
     * Match the requests that meet a condition only, like {@code @RouteCondition} on a controller
     * method: a request the condition rejects is answered as if the route did not exist, by
     * another route of the same URI and method, e.g. one with another condition, or by
     * {@code 404}, never by a {@code 405}, {@code 415} or {@code 406} of this route. The
     * conditions of the groups of the route, outer group first, then those of the route, must
     * all be met. The conditions are evaluated while the request is matched, for every request
     * whose path the route matches: they must be fast and must not block, nor read the body.
     *
     * <pre>{@code
     * routes.GET("/reports/{id}", (request, pathVariables) -> HttpResponse.ok(reports.csv(pathVariables.getLong("id"))))
     *     .where(RequestPredicates.header("X-Export", "csv"));
     * routes.GET("/reports/{id}", (request, pathVariables) -> HttpResponse.ok(reports.find(pathVariables.getLong("id"))));
     * }</pre>
     *
     * <p>A request both routes of the example match, with the header, is ambiguous: the most
     * specific route answers it, and the two routes are equally specific, so it is answered with
     * {@code 400}. {@link RequestPredicates} builds conditions on the headers, query parameters,
     * media types and method of the request.</p>
     *
     * @param condition The condition
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec where(Predicate<HttpRequest<?>> condition);

    /**
     * Break a tie with other routes that are equally good for a request: the route with the
     * lowest order answers it. The router selects the most specific route by its URI template,
     * then by the media types, then prefers an explicit {@code HEAD} route over an implicit one;
     * only the routes still left after that are compared by their order. So the order never
     * makes a less specific route win over a more specific one: it chooses among routes of the
     * same URI template whose {@link #where(Predicate) conditions} a request both meets, e.g. a
     * specialized route and a fallback. Two routes left with the same order still make the
     * request ambiguous, answered with {@code 400}.
     *
     * <pre>{@code
     * routes.GET("/reports/{id}", csvHandler)
     *     .where(RequestPredicates.queryParam("format", "csv"))
     *     .order(-1);
     * routes.GET("/reports/{id}", reportHandler); // the order 0: answers the other requests
     * }</pre>
     *
     * <p>The default order is {@code 0}, the order of a controller route, or the order of the
     * {@link HttpRouteGroup#order(int) group} of the route, which the order of the route
     * overrides. The order of an {@link HttpRoutes} bean orders the beans, not their routes.</p>
     *
     * @param order The order, lower wins
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec order(int order);

    /**
     * Give the route an attribute: metadata of the route for the code that handles its requests,
     * which reads it from the matched route with {@link io.micronaut.web.router.RouteInfo#getAttribute(String)},
     * e.g. a route filter, a server filter or the handler. The attributes of the
     * {@link HttpRouteGroup#attribute(String, Object) groups} of the route apply too, and an
     * attribute of the route overrides the attribute of a group with the same name.
     *
     * <pre>{@code
     * routes.path("/admin", admin -> {
     *     admin.attribute("role", "admin");
     *     admin.GET("/users", usersHandler);
     *     admin.GET("/audit", auditHandler).attribute("role", "auditor");
     * });
     * routes.filter("/admin/**").before(request -> {
     *     String role = RouteAttributes.getRouteInfo(request)
     *         .flatMap(route -> route.getAttribute("role", String.class))
     *         .orElseThrow();
     *     return hasRole(request, role) ? null : HttpResponse.forbidden();
     * });
     * }</pre>
     *
     * <p>They are the attributes of the route, not of the request, see
     * {@link io.micronaut.web.router.RouteAttributes} for the route of a request.</p>
     *
     * @param name  The name of the attribute
     * @param value The value of the attribute
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec attribute(String name, Object value);
}
