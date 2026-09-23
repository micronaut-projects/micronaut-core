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

import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.web.router.AssembledRoutes;
import io.micronaut.web.router.RouteAssembly;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the routes of the {@link HttpRoutes} beans, in their order, for the application
 * router. Like controller routes, their URIs are under {@code micronaut.server.context-path}, and
 * like a {@code @Get} method, every {@code GET} route gets an implicit {@code HEAD} route unless a
 * {@code HEAD} route has the same URI.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
@Requires(beans = HttpRoutes.class)
final class HttpRoutesAssembly implements AssembledRoutes {

    private final RouteAssembly assembly;

    /**
     * @param executionHandleLocator The locator of the application beans
     * @param conversionService      The conversion service
     * @param routes                 The routes to add
     * @param contextPath            The context path of the server
     */
    HttpRoutesAssembly(ExecutionHandleLocator executionHandleLocator,
                       ConversionService conversionService,
                       List<HttpRoutes> routes,
                       @Nullable @Value("${micronaut.server.context-path}") String contextPath) {
        this.assembly = new RouteAssembly(executionHandleLocator, conversionService,
            uri -> RouteAssembly.underContextPath(contextPath, uri), route -> { }, contextPath);
        DefaultHttpRouteBuilder builder = new DefaultHttpRouteBuilder(assembly);
        List<HttpRoutes> ordered = new ArrayList<>(routes);
        OrderUtil.sort(ordered);
        for (HttpRoutes httpRoutes : ordered) {
            httpRoutes.routes(builder);
        }
        assembly.addImplicitHeadRoutes();
    }

    @Override
    public RouteAssembly routes() {
        return assembly;
    }
}
