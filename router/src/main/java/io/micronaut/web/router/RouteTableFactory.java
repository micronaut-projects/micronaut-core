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
package io.micronaut.web.router;

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.DefaultLocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.LocatedHttpRouteBuilder;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds the {@link RouteTable}s of a {@link RouteSource} with the same route builder as the
 * routes of the application. Routes target executable bean methods, e.g.
 * {@code routes.GET("/orders/{+path}", OrdersHandler.class, "handle", HttpRequest.class)} or the
 * {@code BeanDefinition}/{@code ExecutableMethod} form, so arguments are bound and the method's
 * annotations apply as they do for a controller: {@code @Consumes} and {@code @Produces} set the
 * media types of the route (calls on the route in the build callback override them), and
 * {@code @Version} and the executor apply when the route is matched and executed. Every
 * {@code GET} route gets an implicit {@code HEAD} route, unless the table has a {@code HEAD}
 * route for the same URI or the method is annotated {@code @Get(headRoute = false)}. Like
 * controller routes, the URIs are under {@code micronaut.server.context-path}. A table of routes to
 * handler functions is built with {@link #buildHttpRoutes(HttpRoutes)}, e.g.
 * {@code routes.GET("/orders/{id}", (request, pathVariables) -> ...)}.
 * <p>A table is immutable: changing a route returned by the builder after {@link #build} returned
 * does not change the table.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@Singleton
public final class RouteTableFactory {
    private final ExecutionHandleLocator executionHandleLocator;
    private final RouteBuilder.UriNamingStrategy uriNamingStrategy;
    private final ConversionService conversionService;
    private final @Nullable String contextPath;

    /**
     * @param executionHandleLocator The locator of the executable methods
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     * @param contextPath            The context path of the server
     */
    RouteTableFactory(ExecutionHandleLocator executionHandleLocator,
                      RouteBuilder.UriNamingStrategy uriNamingStrategy,
                      ConversionService conversionService,
                      @Nullable @Value("${micronaut.server.context-path}") String contextPath) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = uriNamingStrategy;
        this.conversionService = conversionService;
        this.contextPath = contextPath;
    }

    /**
     * Build a route table.
     *
     * @param routes Declares the URI routes of the table on the given builder. Filter, status and
     *               error routes and exposed ports are not supported: they belong to the
     *               application router
     * @return The table
     * @throws IllegalArgumentException if the routes declare anything but URI routes
     */
    public RouteTable build(Consumer<? super RouteBuilder> routes) {
        Objects.requireNonNull(routes, "routes");
        DefaultRouteBuilder builder = new DefaultRouteBuilder(executionHandleLocator, uriNamingStrategy, conversionService) {
            @Override
            protected String routeUri(String uri) {
                return RouteAssembly.underContextPath(contextPath, uri);
            }

            @Override
            void routeCreated(RouteAssembly.DefaultUriRoute route) {
                // like a controller method: the media types of the handler method apply, and calls
                // on the route in the build callback can still change them
                MediaType[] consumes = MediaType.of(route.targetMethod.stringValues(Consumes.class));
                if (consumes.length > 0) {
                    route.consumes(consumes);
                }
                MediaType[] produces = MediaType.of(route.targetMethod.stringValues(Produces.class));
                if (produces.length > 0) {
                    route.produces(produces);
                }
            }
        };
        routes.accept(builder);
        builder.addImplicitHeadRoutes();
        if (!builder.getFilterRoutes().isEmpty()) {
            throw new IllegalArgumentException("A route table can only declare URI routes, not filter, status or error routes");
        }
        return table(builder.assembly, null);
    }

    /**
     * Build a route table of routes to handler functions, declared like the routes of an
     * {@link HttpRoutes} bean.
     *
     * @param routes Declares the URI routes of the table. Error and status routes declared in a
     *               group are local to its routes and supported; global ones, declared on the
     *               builder itself, server filters and ports are not: they belong to the
     *               application router
     * @return The table
     * @throws IllegalArgumentException if the routes declare global error or status routes,
     *                                  server filters or ports
     */
    public RouteTable buildHttpRoutes(HttpRoutes routes) {
        Objects.requireNonNull(routes, "routes");
        RouteAssembly assembly = new RouteAssembly(executionHandleLocator, conversionService,
            contextPath, route -> { });
        declare(routes, assembly);
        assembly.addImplicitHeadRoutes();
        return table(assembly, null);
    }

    /**
     * Build the route table of a target located by a locator route, see
     * {@link io.micronaut.web.router.builder.HttpRouteBuilder#locate}: the URIs of its routes
     * are relative to the prefix of the locator, not under the context path. The table does not
     * depend on the target, which reaches the handlers through
     * {@link io.micronaut.web.router.builder.PathVariables#locatedTarget()}: build it once per
     * type of target.
     *
     * @param routes Declares the URI routes of the table, including other locator routes. Error
     *               and status routes declared in a group are local to its routes and supported;
     *               global ones, declared on the builder itself, server filters and ports are
     *               not: they belong to the application router
     * @return The table
     * @throws IllegalArgumentException if the routes declare global error or status routes,
     *                                  server filters or ports
     * @since 5.3.0
     */
    public RouteTable buildLocatedHttpRoutes(HttpRoutes routes) {
        Objects.requireNonNull(routes, "routes");
        // relative to the prefix of the locator: no context path, so templates of every engine are supported
        RouteAssembly assembly = new RouteAssembly(executionHandleLocator, conversionService, (String) null, route -> { });
        declare(routes, assembly);
        assembly.addImplicitHeadRoutes();
        return table(assembly, null);
    }

    /**
     * Declare the routes on a builder that is closed when they returned: a route declared on it
     * later, which the table would not have, fails.
     *
     * @param routes   Declares the routes
     * @param assembly The assembly the routes are added to
     */
    private static void declare(HttpRoutes routes, RouteAssembly assembly) {
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        try {
            routes.routes(builder);
        } finally {
            builder.close();
        }
    }

    /**
     * Build the route table of the located targets of a type, whose handlers receive the target,
     * see {@link LocatedHttpRouteBuilder}.
     *
     * @param targetType The type of the located targets
     * @param routes     Declares the URI routes of the table, see {@link #buildLocatedHttpRoutes(HttpRoutes)}
     * @param <T>        The type of the located targets
     * @return The table
     * @throws IllegalArgumentException if the routes declare anything but URI routes
     * @since 5.3.0
     */
    public <T> RouteTable buildLocatedHttpRoutes(Class<T> targetType, Consumer<? super LocatedHttpRouteBuilder<T>> routes) {
        return buildLocatedHttpRoutes(Argument.of(Objects.requireNonNull(targetType, "targetType")), routes);
    }

    /**
     * Build the route table of the located targets of a type, whose handlers receive the target,
     * see {@link LocatedHttpRouteBuilder}: the same as {@link #buildLocatedHttpRoutes(HttpRoutes)},
     * but a located target that is not an instance of the type fails the request, answered by the
     * error routes like a failed controller method.
     *
     * <pre>{@code
     * RouteTable itemRoutes = tables.buildLocatedHttpRoutes(Argument.of(Order.class), items ->
     *     items.handle(HttpMethod.GET, "/items/{item}", (request, pathVariables, order) ->
     *         HttpResponse.ok(order.item(pathVariables.getInt("item")))));
     * }</pre>
     *
     * @param targetType The type of the located targets
     * @param routes     Declares the URI routes of the table, see {@link #buildLocatedHttpRoutes(HttpRoutes)}
     * @param <T>        The type of the located targets
     * @return The table
     * @throws IllegalArgumentException if the routes declare anything but URI routes
     * @since 5.3.0
     */
    public <T> RouteTable buildLocatedHttpRoutes(Argument<T> targetType, Consumer<? super LocatedHttpRouteBuilder<T>> routes) {
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(routes, "routes");
        // relative to the prefix of the locator: no context path, so templates of every engine are supported
        RouteAssembly assembly = new RouteAssembly(executionHandleLocator, conversionService, (String) null, route -> { });
        DefaultLocatedHttpRouteBuilder<T> builder = new DefaultLocatedHttpRouteBuilder<>(assembly, targetType);
        try {
            routes.accept(builder);
        } finally {
            builder.close();
        }
        assembly.addImplicitHeadRoutes();
        return table(assembly, targetType);
    }

    private static RouteTable table(RouteAssembly assembly, @Nullable Argument<?> locatedTargetType) {
        if (!assembly.statusRoutes().isEmpty() || !assembly.errorRoutes().isEmpty() || !assembly.filterRoutes().isEmpty()) {
            throw new IllegalArgumentException("A route table can only declare URI routes, not filter, status or error routes");
        }
        if (!assembly.exposedPorts().isEmpty()) {
            throw new IllegalArgumentException("A route table cannot expose ports: " + assembly.exposedPorts());
        }
        return new DefaultRouteTable(UriRouteSet.of(assembly.uriRoutes(), assembly.lazyRouteInfos()), locatedTargetType);
    }
}
