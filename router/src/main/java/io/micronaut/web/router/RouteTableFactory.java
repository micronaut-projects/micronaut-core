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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.web.router.builder.DefaultLocatedHttpRouteBuilder;
import io.micronaut.web.router.builder.LocatedRoutes;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the {@link RouteTable}s of located targets, see
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder#locate}: the table of a
 * {@link LocatedRoutes} is built when a locator locates the first target it routes, and kept by
 * the identity of the instance, so that the locators that answer the same instance share it.
 * One factory serves the routes of an application, including the located tables, which may
 * locate again.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteTableFactory {
    private final @Nullable Object beanLocator;
    private final ConversionService conversionService;
    private final Map<Identity, DefaultRouteTable> tables = new ConcurrentHashMap<>();

    /**
     * @param beanLocator       The locator of the application beans, see {@link RouteAssembly}
     * @param conversionService The conversion service
     */
    RouteTableFactory(@Nullable Object beanLocator, ConversionService conversionService) {
        this.beanLocator = beanLocator;
        this.conversionService = conversionService;
    }

    /**
     * The table of located routes, built on the first call for the instance and then reused: a
     * concurrent first call waits for the table the other call builds. A table that fails to
     * build is not kept, the next call builds it again.
     *
     * @param routes The routes
     * @return The table
     * @throws IllegalArgumentException if the routes declare global error or status routes,
     *                                  server filters or ports
     */
    RouteTable table(LocatedRoutes<?> routes) {
        Objects.requireNonNull(routes, "routes");
        return tables.computeIfAbsent(new Identity(routes), identity -> build(identity.routes()));
    }

    /**
     * Build the table of located routes: the URIs of its routes are relative to the prefix of the
     * locator, not under the context path.
     *
     * @param routes The routes
     * @param <T>    The type of the located targets
     * @return The table
     */
    private <T> DefaultRouteTable build(LocatedRoutes<T> routes) {
        Argument<T> targetType = Objects.requireNonNull(routes.targetType(), "targetType");
        RouteAssembly assembly = new RouteAssembly(beanLocator, conversionService, uri -> uri, route -> { });
        // the located tables of the table are kept here too
        assembly.locatedTables = this;
        DefaultLocatedHttpRouteBuilder<T> builder = new DefaultLocatedHttpRouteBuilder<>(assembly, targetType);
        try {
            routes.routes(builder);
        } finally {
            builder.close();
        }
        assembly.addImplicitHeadRoutes();
        if (!assembly.statusRoutes().isEmpty() || !assembly.errorRoutes().isEmpty() || !assembly.filterRoutes().isEmpty()) {
            throw new IllegalArgumentException("Located routes can only declare URI routes, not filter, status or error routes: " + routes);
        }
        if (!assembly.exposedPorts().isEmpty()) {
            throw new IllegalArgumentException("Located routes cannot expose ports: " + assembly.exposedPorts());
        }
        return new DefaultRouteTable(UriRouteSet.of(assembly.uriRoutes()), targetType);
    }

    /**
     * The key of the table of an instance of {@link LocatedRoutes}: its identity, whatever its
     * {@code equals}.
     *
     * @param routes The routes
     */
    private record Identity(LocatedRoutes<?> routes) {
        @Override
        public boolean equals(Object o) {
            return o instanceof Identity other && other.routes == routes;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(routes);
        }
    }
}
