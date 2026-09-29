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
 * A pending route whose handler receives the body of the request, after a body stage of
 * {@link HttpRouteSpec}: {@link HttpRouteSpec#body(Argument)}, {@link HttpRouteSpec#body()} or
 * {@link HttpRouteSpec#form()}. It takes the settings of a route, see {@link RouteSpec}, and ends
 * with one terminal, which adds the route.
 *
 * <pre>{@code
 * routes.POST("/items").body(Item.class).handle((request, pathVariables, item) -> HttpResponse.created(items.save(item)));
 * routes.POST("/items/async").body(Item.class).handleAsync((request, pathVariables, item) ->
 *     items.saveAsync(item).thenApply(HttpResponse::created));
 * routes.POST("/login").form().handle((request, pathVariables, form) -> login(form.getString("user")));
 * }</pre>
 *
 * @param <B> The type of the body the handler receives
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public sealed interface HttpBodyRouteSpec<B> extends RouteSpec<HttpBodyRouteSpec<B>> permits DefaultHttpBodyRouteSpec {

    /**
     * Declare the type of the body of the responses of the route, see
     * {@link HttpRouteSpec#responseType(Argument)}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     */
    HttpBodyRouteSpec<B> responseType(Argument<?> responseType);

    /**
     * Declare the type of the body of the responses as a class: {@code responseType(Argument.of(responseType))}.
     *
     * @param responseType The type of the body of the response
     * @return The route
     */
    default HttpBodyRouteSpec<B> responseType(Class<?> responseType) {
        return responseType(Argument.of(Objects.requireNonNull(responseType, "responseType")));
    }

    /**
     * End the route with a handler function that receives the body and returns the response. The
     * executor is selected like for a blocking controller method.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handle(BodyRequestHandler<B> handler);

    /**
     * End the route with a handler function that receives the body and completes the response
     * later. The executor is selected like for a controller method returning a
     * {@code CompletionStage}, see {@link AsyncBodyRequestHandler}.
     *
     * @param handler The handler
     * @throws IllegalStateException if the route was already ended
     */
    void handleAsync(AsyncBodyRequestHandler<B> handler);
}
