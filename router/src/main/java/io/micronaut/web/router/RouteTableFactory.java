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
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Produces;
import io.micronaut.web.router.builder.DefaultHttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.List;
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
        return table(builder.assembly, List.of(builder), List.of());
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
            uri -> RouteAssembly.underContextPath(contextPath, uri), route -> { });
        declare(routes, assembly);
        assembly.addImplicitHeadRoutes();
        return table(assembly, List.of(), List.of(() -> assembly));
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
        RouteAssembly assembly = new RouteAssembly(executionHandleLocator, conversionService, uri -> uri, route -> { });
        declare(routes, assembly);
        assembly.addImplicitHeadRoutes();
        return table(assembly, List.of(), List.of(() -> assembly));
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

    private static RouteTable table(RouteAssembly assembly, List<RouteBuilder> builders, List<AssembledRoutes> assembled) {
        if (!assembly.statusRoutes().isEmpty() || !assembly.errorRoutes().isEmpty() || !assembly.filterRoutes().isEmpty()) {
            throw new IllegalArgumentException("A route table can only declare URI routes, not filter, status or error routes");
        }
        if (!assembly.exposedPorts().isEmpty()) {
            throw new IllegalArgumentException("A route table cannot expose ports: " + assembly.exposedPorts());
        }
        return new DefaultRouteTable(new DefaultRouter(builders, assembled));
    }
}
