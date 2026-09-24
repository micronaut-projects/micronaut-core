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
import io.micronaut.core.type.Argument;

import java.util.Objects;

/**
 * A route to a handler function, to configure after it was added with the {@link HttpRouteBuilder}.
 * A route on several HTTP methods configures all of them. The settings it shares with a
 * {@link HttpRouteGroup group}, e.g. its media types, executor, annotations and order, are the
 * ones of {@link RouteSpec}; a setting of the route overrides the one of its groups.
 *
 * <p>The filters of the route, see {@link RouteFilterSpec}, run after the application's filters and
 * the filters of the groups the route is declared in, closest to the route, and are resolved when
 * the route is built.</p>
 *
 * <p>The configuration of the route is read when the router is built, once the routes were
 * declared: configure the route where it is declared, in {@link HttpRoutes#routes(HttpRouteBuilder)}
 * or in the callback that builds a route table. A change made to a route kept after that is
 * ignored.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface HttpRouteSpec extends RouteSpec<HttpRouteSpec> permits DefaultHttpRouteSpec {

    /**
     * Declare the type of the body of the responses of the route, like the return type
     * {@code HttpResponse<R>} of a controller method: the message body writer is selected for the
     * declared type, with its type arguments and annotations, instead of the runtime class of the
     * body, e.g. a writer or a JSON view for {@code List<Item>} instead of one for
     * {@code ArrayList}. The handler still returns an {@code HttpResponse}, or a stage of one;
     * a body that is not an instance of the declared type is written as its runtime class, like
     * the body of a controller route.
     *
     * <pre>{@code
     * routes.GET("/items", (request, pathVariables) -> HttpResponse.ok(items.findAll()))
     *     .responseType(Argument.listOf(Item.class));
     * }</pre>
     *
     * <p>A response without a body, and a handler that returns no response, are answered like
     * those of a controller route. The route has the declared type for every handler kind,
     * e.g. {@code CompletionStage<HttpResponse<R>>} for a handler that completes the response
     * later, and for the features that read the return type of the matched route, see
     * {@link io.micronaut.web.router.RouteInfo#getResponseBodyType()}.</p>
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    HttpRouteSpec responseType(Argument<?> responseType);

    /**
     * Declare the type of the body of the responses as a class: {@code responseType(Argument.of(responseType))},
     * see {@link #responseType(Argument)}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     * @since 5.3.0
     */
    default HttpRouteSpec responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }
}
