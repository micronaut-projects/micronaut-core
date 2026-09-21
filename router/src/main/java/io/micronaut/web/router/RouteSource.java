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
import io.micronaut.core.order.Ordered;

/**
 * A source of routes that change at runtime, e.g. routes read from configuration that is
 * refreshed. Implement it as a bean that publishes an immutable {@link RouteTable}, built with the
 * {@link RouteTableFactory}, and replaces it when the routes change.
 * <p>Micronaut matches the tables with the same rules as the routes of the application: ports,
 * conditions, consumed and produced media types, versions, specificity and implicit HEAD routes. A
 * match is a real route match, so server filters, security, CORS and error handling apply to it,
 * and a route that matches the path but not the method is answered with
 * {@code 405 Method Not Allowed}.
 * <p>Precedence: a route of the application that fully matches the request (after every rule,
 * including route filters such as versioning) takes precedence. Otherwise the route sources are
 * consulted in {@link Ordered order}, and the first table with a matching route wins. A controller
 * that does not accept the request, e.g. because it does not consume its content type, does not
 * own its URI: the request can still match a route of a source.
 * <p>Each request uses one snapshot of every source: {@link #snapshot()} is called at most once per
 * request, so matching, CORS and error handling of a request see the same tables even if a source
 * publishes a new table meanwhile.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface RouteSource extends Ordered {

    /**
     * The current routes. Called at most once per request, so it should return a table that was
     * built before, not build one.
     *
     * @return The current route table, or {@link RouteTable#empty()}
     */
    RouteTable snapshot();
}
