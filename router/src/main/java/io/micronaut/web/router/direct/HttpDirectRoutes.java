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
package io.micronaut.web.router.direct;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;

/**
 * Direct routes declared in code: every bean of this type declares its routes with a
 * {@link DirectRouteBuilder} when the application starts. The server answers a request a direct
 * route matches as soon as it has received it, before it creates the
 * {@link io.micronaut.http.HttpRequest}, runs the filters and looks up the ordinary routes: no
 * filter runs for a direct route, of any kind.
 *
 * <pre>{@code
 * @Singleton
 * class ProbeRoutes implements HttpDirectRoutes {
 *     @Override
 *     public void routes(DirectRouteBuilder routes) {
 *         routes.GET("/probe/live", HttpResponse.ok("UP"));
 *     }
 * }
 * }</pre>
 *
 * <p>The beans are a sibling of the {@link io.micronaut.web.router.builder.HttpRoutes} beans,
 * which declare the ordinary routes: a direct route shares nothing with them, neither the groups,
 * the filters, the port nor the executor of a group. The order of the beans only orders the
 * beans, like the order of the {@code HttpRoutes} beans.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface HttpDirectRoutes extends Ordered {

    /**
     * Declare the direct routes.
     *
     * @param routes The builder of the direct routes
     */
    void routes(DirectRouteBuilder routes);
}
