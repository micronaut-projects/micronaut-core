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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.convert.ConversionService;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Builds the {@link RouteTable}s of a {@link RouteSource} with the same route builder as the
 * routes of the application. Routes target executable bean methods, e.g.
 * {@code routes.GET("/orders/{+path}", OrdersHandler.class, "handle", HttpRequest.class)} or the
 * {@code BeanDefinition}/{@code ExecutableMethod} form, so arguments are bound and the method's
 * annotations (produces, consumes, version, executor) apply as they do for a controller.
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

    /**
     * @param executionHandleLocator The locator of the executable methods
     * @param uriNamingStrategy      The URI naming strategy
     * @param conversionService      The conversion service
     */
    RouteTableFactory(ExecutionHandleLocator executionHandleLocator,
                      RouteBuilder.UriNamingStrategy uriNamingStrategy,
                      ConversionService conversionService) {
        this.executionHandleLocator = executionHandleLocator;
        this.uriNamingStrategy = uriNamingStrategy;
        this.conversionService = conversionService;
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
        };
        routes.accept(builder);
        if (!builder.getFilterRoutes().isEmpty() || !builder.getStatusRoutes().isEmpty() || !builder.getErrorRoutes().isEmpty()) {
            throw new IllegalArgumentException("A route table can only declare URI routes, not filter, status or error routes");
        }
        if (!builder.getExposedPorts().isEmpty()) {
            throw new IllegalArgumentException("A route table cannot expose ports: " + builder.getExposedPorts());
        }
        return new RouteTable(new DefaultRouter(List.of(builder)));
    }
}
