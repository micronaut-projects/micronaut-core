/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.management.endpoint;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.OncePerRequestHttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.filter.ServerFilterPhase;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.web.router.MethodBasedRouteMatch;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.RouteMatchUtils;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.Optional;

/**
 * Returns 401 for {@link io.micronaut.management.endpoint.annotation.Endpoint} requests which have sensitive true.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Filter("/**")
public class EndpointsFilter extends OncePerRequestHttpServerFilter {

    private final Map<ExecutableMethod, Boolean> endpointMethods;

    /**
     * Constructor.
     * @param endpointSensitivityProcessor The processor that resolves endpoint sensitivity
     */
    public EndpointsFilter(EndpointSensitivityProcessor endpointSensitivityProcessor) {
        this.endpointMethods = endpointSensitivityProcessor.getEndpointMethods();
    }

    /**
     * Returns 401 if the route is a match for an endpoint with sensitive true.
     *
     * @param request The {@link HttpRequest} instance
     * @param chain   The {@link ServerFilterChain} instance
     * @return A {@link Publisher} for the Http response
     */
    @Override
    protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
        Optional<RouteMatch> routeMatch = RouteMatchUtils.findRouteMatch(request);
        if (routeMatch.isPresent() && routeMatch.get() instanceof MethodBasedRouteMatch) {
            ExecutableMethod method = ((MethodBasedRouteMatch) routeMatch.get()).getExecutableMethod();
            if (endpointMethods.getOrDefault(method, false)) {
                return Publishers.just(HttpResponse.status(HttpStatus.UNAUTHORIZED));
            }
        }
        return chain.proceed(request);
    }

    @Override
    public int getOrder() {
        return ServerFilterPhase.SECURITY.order();
    }
}
