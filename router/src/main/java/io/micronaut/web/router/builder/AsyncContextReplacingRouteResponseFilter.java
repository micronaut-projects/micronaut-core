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
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpResponse;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletionStage;

/**
 * An asynchronous filter of one route's responses that can replace the response and changes the
 * propagated context, declared with
 * {@link RouteFilterSpec#afterReplacingAsync(AsyncContextReplacingRouteResponseFilter)}: the
 * {@link AsyncReplacingRouteResponseFilter} that receives a {@link MutablePropagatedContext}. The
 * change is taken when the returned stage completes.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 * @see ContextReplacingRouteResponseFilter
 */
@Experimental
@FunctionalInterface
public interface AsyncContextReplacingRouteResponseFilter {

    /**
     * Filter the response.
     *
     * @param request           The request
     * @param response          The response of the route, which the filter can change until the stage completes
     * @param propagatedContext The propagated context, to change until the returned stage completes
     * @return Completes with a response to continue with instead of the response, or with
     * {@code null} to continue with the response; completing exceptionally is handled by the error routes
     * @throws Exception An error, handled by the error routes like a controller error
     */
    CompletionStage<? extends @Nullable HttpResponse<?>> filter(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) throws Exception;
}
