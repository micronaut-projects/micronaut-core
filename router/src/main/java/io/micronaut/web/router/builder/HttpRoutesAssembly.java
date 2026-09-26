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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.order.Ordered;
import io.micronaut.web.router.AssembledRoutes;
import io.micronaut.web.router.DefaultRouteBuilder;
import io.micronaut.web.router.DefaultRouter;
import io.micronaut.web.router.FilterRoute;
import io.micronaut.web.router.RouteAssembly;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Assembles the routes of the {@link HttpRoutes} beans, in their order, for the application
 * router. Like controller routes, their URIs are under {@code micronaut.server.context-path}, and
 * like a {@code @Get} method, every {@code GET} route gets an implicit {@code HEAD} route unless a
 * {@code HEAD} route has the same URI.
 * <p>The assembly is a {@link io.micronaut.web.router.RouteBuilder} bean, so the router receives
 * the routes with the other route builders: a router that replaces {@link DefaultRouter} and only
 * calls {@link DefaultRouter#DefaultRouter(Collection)} has them too. It is ordered last, as the
 * routes were added after the routes of every route builder before.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = HttpRoutes.class)
@Order(Ordered.LOWEST_PRECEDENCE)
final class HttpRoutesAssembly extends DefaultRouteBuilder implements AssembledRoutes {

    private final RouteAssembly assembly;
    private final List<FilterRoute> filterRoutes;

    /**
     * @param executionHandleLocator The locator of the application beans
     * @param conversionService      The conversion service
     * @param routes                 The routes to add
     * @param contextPath            The context path of the server
     */
    @Inject
    HttpRoutesAssembly(ExecutionHandleLocator executionHandleLocator,
                       ConversionService conversionService,
                       List<HttpRoutes> routes,
                       @Nullable @Value("${micronaut.server.context-path}") String contextPath) {
        this(executionHandleLocator, conversionService, routes, new RouteAssembly(executionHandleLocator, conversionService,
            contextPath, route -> { }));
    }

    private HttpRoutesAssembly(ExecutionHandleLocator executionHandleLocator,
                               ConversionService conversionService,
                               List<HttpRoutes> routes,
                               RouteAssembly assembly) {
        super(executionHandleLocator, conversionService, assembly);
        this.assembly = assembly;
        // the ports given as strings are resolved like the port of a @Controller
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly,
            executionHandleLocator instanceof ApplicationContext context ? context.getEnvironment().getPlaceholderResolver() : null);
        List<HttpRoutes> ordered = new ArrayList<>(routes);
        OrderUtil.sort(ordered);
        try {
            for (HttpRoutes httpRoutes : ordered) {
                httpRoutes.routes(builder);
            }
        } finally {
            // the routes are read now: a route a bean declares later would be dropped
            builder.close();
        }
        assembly.addImplicitHeadRoutes();
        this.filterRoutes = assembly.filterRoutes();
    }

    @Override
    public List<FilterRoute> getFilterRoutes() {
        return filterRoutes;
    }

    @Override
    public RouteAssembly routes() {
        return assembly;
    }
}
