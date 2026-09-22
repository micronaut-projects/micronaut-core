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
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The {@link RouteTable} built by the {@link RouteTableFactory}: an indexed router of the table's
 * routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultRouteTable implements RouteTable {
    static final DefaultRouteTable EMPTY = new DefaultRouteTable(new DefaultRouter(List.of()));

    private final DefaultRouter router;
    private final boolean empty;
    @Nullable
    private volatile List<Integer> appliedDefaultPorts;

    /**
     * @param router The router of the table's routes
     */
    DefaultRouteTable(DefaultRouter router) {
        this.router = router;
        this.empty = router.uriRoutes().findAny().isEmpty();
    }

    /**
     * @return Whether the table has no routes
     */
    boolean isEmpty() {
        return empty;
    }

    /**
     * The router of the table, with the default ports of the application applied once.
     *
     * @param defaultPorts The default ports of the application, or {@code null} if not known yet
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
