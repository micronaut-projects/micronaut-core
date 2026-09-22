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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.order.Ordered;
import io.micronaut.web.router.RouteSource;
import io.micronaut.web.router.RouteTable;
import io.micronaut.web.router.RouteTableFactory;

/**
 * Routes declared in code. Every bean of this type adds its routes to the application routes
 * when the router is created, next to the controllers:
 *
 * <pre>{@code
 * @Factory
 * class ItemRoutes {
 *     @Singleton
 *     HttpRoutes itemRoutes(ItemRepository items) {
 *         return routes -> {
 *             routes.GET("/items/{id}", (request, pathVariables) -> HttpResponse.ok(items.find(pathVariables.getLong("id"))));
 *             routes.POST("/items", Argument.of(Item.class), (request, pathVariables, item) -> HttpResponse.created(items.save(item)));
 *         };
 *     }
 * }
 * }</pre>
 *
 * <p>The same function builds routes that change at runtime:
 * {@link RouteTableFactory#build(java.util.function.Consumer)} turns it into a
 * {@link RouteTable} that a {@link RouteSource} publishes.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface HttpRoutes extends Ordered {

    /**
     * Declare the routes.
     *
     * @param routes The route builder
     */
    void routes(RouteBuilder routes);
}
