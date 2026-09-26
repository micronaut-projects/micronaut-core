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
import io.micronaut.core.propagation.MutablePropagatedContext;
import io.micronaut.http.HttpMessage;
import io.micronaut.http.MutableHttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * An asynchronous filter of one route's requests that changes the propagated context and can
 * answer or replace the request, declared with
 * {@link HttpRouteSpec#beforeReplacingAsync(AsyncContextReplacingRouteRequestFilter)}: the
 * {@link AsyncReplacingRouteRequestFilter} that receives a {@link MutablePropagatedContext}. The change is
 * taken when the returned stage completes, so the filter can add an element once it looked
 * something up, e.g. the security context of a token it verified, or reject the request:
 *
 * <pre>{@code
 * routes.GET("/orders", handler).beforeReplacingAsync((request, propagatedContext) -> tokens.verify(request)
 *     .thenApply(user -> {
 *         if (user.isEmpty()) {
 *             return HttpResponse.unauthorized();
 *         }
 *         propagatedContext.add(new UserContext(user.get()));
 *         return null;
 *     }));
 * }</pre>
 *
 * <p>The filter chain continues when the stage completes, possibly on another thread, with the
 * changed context in scope for the next filters, the route handler and the response filters.
 * It changes or replaces the request like a {@link ReplacingRouteRequestFilter}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see ContextReplacingRouteRequestFilter
 */
@Experimental
@FunctionalInterface
public interface AsyncContextReplacingRouteRequestFilter {

    /**
     * Filter the request.
     *
     * @param request           The request, to change in place until the returned stage completes, see {@link ReplacingRouteRequestFilter}
     * @param propagatedContext The propagated context, to change until the returned stage completes
     * @return Completes with a response to answer the request with instead of the route, with a
     * request to continue with instead of the request, or with {@code null} to proceed with the
     * request; completing exceptionally is handled by the error routes
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable HttpMessage<?>> filter(MutableHttpRequest<?> request, MutablePropagatedContext propagatedContext) throws Exception;
}
