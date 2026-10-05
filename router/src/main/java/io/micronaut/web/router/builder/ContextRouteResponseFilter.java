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
import io.micronaut.http.MutableHttpResponse;

/**
 * A filter of one route's responses that changes the propagated context, declared with
 * {@link HttpRouteSpec#after(ContextRouteResponseFilter)}: the {@link RouteResponseFilter} that
 * receives a {@link MutablePropagatedContext}, like a {@code @ResponseFilter} method with a
 * {@code MutablePropagatedContext} parameter. It runs with the context the request filters
 * produced in scope, and its change is in scope for the response filters after it, e.g. to
 * remove an element a request filter added.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
@FunctionalInterface
public interface ContextRouteResponseFilter {

    /**
     * Filter the response.
     *
     * @param request           The request
     * @param response          The response of the route
     * @param propagatedContext The propagated context, to change for the response filters after this one
     * @throws Exception An error, handled by the error routes
     */
    void filter(HttpRequest<?> request, MutableHttpResponse<?> response, MutablePropagatedContext propagatedContext) throws Exception;
}
