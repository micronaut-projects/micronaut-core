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

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Builds the {@link RouteTable}s of located targets, see
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder#locate}: the table of a
 * {@link LocatedRoutes} is built when a locator locates the first target it routes, and kept by
 * the identity of the instance, so that the locators that answer the same instance share it.
 * One factory serves the routes of an application, including the located tables, which may
 * locate again.
 *
 * <p>A table is kept no longer than its instance: the instance is referenced weakly, and its
 * table is dropped once the instance is collected. A table whose handlers reference the instance,
 * e.g. lambdas of the instance that use its fields, keeps it, and is kept, as long as the
 * factory.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteTableFactory {
    private final @Nullable Object beanLocator;
    private final ConversionService conversionService;
    /**
     * The tables by the identity of their instances, weakly referenced.
     */
    private final Map<Object, TableHolder> tables = new ConcurrentHashMap<>();
    /**
     * The keys of the collected instances.
     */
    private final ReferenceQueue<LocatedRoutes<?>> collected = new ReferenceQueue<>();

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
     * concurrent first call for the instance waits for the table the other call builds, a call
     * for another instance does not. A table that fails to build is not kept, the next call
     * builds it again. Building a table may build the tables of other instances.
     *
     * @param routes The routes
     * @return The table
     * @throws IllegalArgumentException if the routes declare global error or status routes
     *                                  or ports
     */
    RouteTable table(LocatedRoutes<?> routes) {
        Objects.requireNonNull(routes, "routes");
        expungeCollected();
        TableHolder holder = tables.get(new Lookup(routes));
        if (holder == null) {
            TableHolder created = new TableHolder();
            holder = tables.putIfAbsent(new WeakKey(routes, collected), created);
            if (holder == null) {
                holder = created;
            }
        }
        return holder.table(routes);
    }

    /**
     * Drop the tables of the collected instances.
     */
    private void expungeCollected() {
        for (Reference<?> key = collected.poll(); key != null; key = collected.poll()) {
            tables.remove(key);
        }
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
        DefaultLocatedHttpRouteBuilder<T> builder = new DefaultLocatedHttpRouteBuilder<>(assembly, targetType, routes.getClass());
        try {
            routes.routes(builder);
        } catch (RuntimeException | Error e) {
            builder.discard();
            throw e;
        }
        // fails for a route that was not ended with a terminal
        builder.close();
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
     * The table of an instance, built outside of the map of the tables, once.
     */
    private final class TableHolder {
        private final ReentrantLock lock = new ReentrantLock();
        private final AtomicReference<@Nullable DefaultRouteTable> table = new AtomicReference<>();

        /**
         * @param routes The routes of the table
         * @return The table, built by the first call
         */
        DefaultRouteTable table(LocatedRoutes<?> routes) {
            DefaultRouteTable built = table.get();
            if (built != null) {
                return built;
            }
            if (lock.isHeldByCurrentThread()) {
                throw new IllegalStateException("The routes declare a locator that needs their own table while they are declared: " + routes);
            }
            lock.lock();
            try {
                built = table.get();
                if (built == null) {
                    built = build(routes);
                    table.set(built);
                }
                return built;
            } finally {
                lock.unlock();
            }
        }
    }

    /**
     * The key of the table of an instance of {@link LocatedRoutes} in the map: its identity,
     * whatever its {@code equals}, referenced weakly. Equal to the {@link Lookup} of the instance
     * while it is not collected, and to itself.
     */
    private static final class WeakKey extends WeakReference<LocatedRoutes<?>> {
        private final int hash;

        WeakKey(LocatedRoutes<?> routes, ReferenceQueue<LocatedRoutes<?>> queue) {
            super(routes, queue);
            this.hash = System.identityHashCode(routes);
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            Object routes = get();
            if (routes == null) {
                return false;
            }
            if (o instanceof Lookup lookup) {
                return lookup.routes == routes;
            }
            return o instanceof WeakKey other && other.get() == routes;
        }

        @Override
        public int hashCode() {
            return hash;
        }
    }

    /**
     * Looks up the table of an instance by its identity.
     *
     * @param routes The routes
     */
    private record Lookup(LocatedRoutes<?> routes) {
        @Override
        public boolean equals(Object o) {
            if (o instanceof Lookup other) {
                return other.routes == routes;
            }
            return o instanceof WeakKey key && key.get() == routes;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(routes);
        }
    }
}
