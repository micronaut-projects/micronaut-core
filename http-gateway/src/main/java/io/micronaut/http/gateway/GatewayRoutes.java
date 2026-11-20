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
package io.micronaut.http.gateway;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;

/**
 * A bean that declares gateway routes in code, e.g.
 *
 * <pre>{@code
 * @Singleton
 * class ApiGateway implements GatewayRoutes {
 *     @Override
 *     public void routes(GatewayRouteBuilder gateway) {
 *         gateway.route("orders")
 *             .path("/api/orders/**")
 *             .where(RequestCondition.header("X-Tenant"))
 *             .filters(GatewayFilters.stripPrefix(2), GatewayFilters.addRequestHeader("X-Gateway", "mn"))
 *             .retry(GatewayRetry.of(2))
 *             .uri("lb://orders");
 *     }
 * }
 * }</pre>
 *
 * <p>The routes of every bean, and those of the configuration {@code micronaut.gateway.routes},
 * become functional routes, see {@link GatewayRoute}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface GatewayRoutes extends Ordered {

    /**
     * Declare routes.
     *
     * @param gateway The builder of the gateway routes
     */
    void routes(GatewayRouteBuilder gateway);
}
