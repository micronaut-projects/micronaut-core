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

/**
 * The {@link RouteTable} built by the {@link RouteTableFactory}: an immutable set of URI routes,
 * sorted for each HTTP method, that the {@link DefaultRouter} matches after the application routes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultRouteTable implements RouteTable {
    static final DefaultRouteTable EMPTY = new DefaultRouteTable(UriRouteSet.NONE);

    private final UriRouteSet routes;

    /**
     * @param routes The routes of the table
     */
    DefaultRouteTable(UriRouteSet routes) {
        this.routes = routes;
    }

    /**
     * @return The routes of the table
     */
    UriRouteSet routes() {
        return routes;
    }
}
