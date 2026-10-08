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

/**
 * A filter of one route's responses that can replace the response and changes the propagated
 * context, declared with {@link RouteFilterSpec#afterReplacing(ContextReplacingRouteResponseFilter)}:
 * the {@link ReplacingRouteResponseFilter} that receives a {@link MutablePropagatedContext}, like a
 * {@code @ResponseFilter} method returning a response with a {@code MutablePropagatedContext}
 * parameter. It changes the context like a {@link ContextRouteResponseFilter}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ContextReplacingRouteResponseFilter {

    /**
     * Filter the response.
     *
     * @param request           The request
     * @param response          The response of the route, to change in place
     * @param propagatedContext The propagated context, to change for the response filters after this one
     * @return A response to continue with instead of the response, or {@code null} to continue with
     * the response
     * @throws Exception An error, handled by the error routes
     */
    @Nullable HttpResponse<?> filter(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) throws Exception;
}
