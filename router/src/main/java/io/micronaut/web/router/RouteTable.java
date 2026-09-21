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

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.stream.Stream;

/**
 * An immutable, indexed set of URI routes published by a {@link RouteSource}. Build it with the
 * {@link RouteTableFactory}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public final class RouteTable {
    private static final RouteTable EMPTY = new RouteTable(new DefaultRouter(List.of()));

    private final DefaultRouter router;
    private final boolean empty;
    @Nullable
    private volatile List<Integer> appliedDefaultPorts;

    RouteTable(DefaultRouter router) {
        this.router = router;
        this.empty = router.uriRoutes().findAny().isEmpty();
    }

    /**
     * @return A table without routes
     */
    public static RouteTable empty() {
        return EMPTY;
    }

    /**
     * @return Whether the table has no routes
     */
    public boolean isEmpty() {
        return empty;
    }

    /**
     * @return The routes of this table
     */
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return router.uriRoutes().distinct();
    }

    /**
     * The router that matches this table, with the default ports of the application applied, so
     * that a route without an explicit port only matches on the ports the application routes do.
     *
     * @param defaultPorts The default ports, or {@code null} if none were applied
     * @return The router
     */
    DefaultRouter router(@Nullable List<Integer> defaultPorts) {
        if (defaultPorts != null && defaultPorts != appliedDefaultPorts) {
            synchronized (this) {
                if (defaultPorts != appliedDefaultPorts) {
                    router.applyDefaultPorts(defaultPorts);
                    appliedDefaultPorts = defaultPorts;
                }
            }
        }
        return router;
    }
}
