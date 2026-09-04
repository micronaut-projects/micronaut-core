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
package io.micronaut.http.server.cors;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.resource.StaticResourceResolver;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.micronaut.http.HttpHeaders.ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK;

/**
 * Default {@link PreflightRequestValidator} implementation.
 *
 * <p>The requested method must be permitted by the resolved CORS configuration and match an
 * application route. When no route matches, a resolved static resource is treated as supporting
 * {@link HttpMethod#GET}. Requested headers and private-network access are then checked against
 * the same configuration.</p>
 *
 * @since 5.2.0
 */
@Singleton
@Internal
class DefaultPreflightRequestValidator implements PreflightRequestValidator {
    private final @Nullable StaticResourceResolver staticResourceResolver;
    private final Router router;

    /**
     * Creates a validator backed by the application router and, when available, the static
     * resource resolver.
     *
     * @param staticResourceResolver the resolver used to recognize GET requests for static
     * resources, or {@code null} when static resource resolution is unavailable
     * @param router the router used to determine which HTTP methods are available for the
     * requested URI
     */
    DefaultPreflightRequestValidator(@Nullable StaticResourceResolver staticResourceResolver,
                                     Router router) {
        this.staticResourceResolver = staticResourceResolver;
        this.router = router;
    }

    @Override
    public boolean validatePreflightRequest(HttpRequest<?> request,
                                            CorsOriginConfiguration config) {
        Optional<HttpMethod> methodToMatchOptional = CrossOriginUtil.validateMethodToMatch(request, config);
        if (methodToMatchOptional.isEmpty()) {
            return false;
        }
        HttpMethod methodToMatch = methodToMatchOptional.get();

        if (!CorsUtil.isPreflightRequest(request)) {
            return false;
        }
        List<HttpMethod> availableHttpMethods = availableHttpMethods(request);
        if (availableHttpMethods.stream().noneMatch(method -> method.equals(methodToMatch))) {
            return false;
        }

        if (!CrossOriginUtil.hasAllowedHeaders(request, config)) {
            return false;
        }

        if (request.getHeaders().contains(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK)) {
            boolean accessControlRequestPrivateNetwork = request.getHeaders().get(ACCESS_CONTROL_REQUEST_PRIVATE_NETWORK, Boolean.class, Boolean.FALSE);
            if (accessControlRequestPrivateNetwork && !config.isAllowPrivateNetwork()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Resolves the HTTP methods available for the requested URI. Static resources contribute
     * {@link HttpMethod#GET} only when no application route matches the URI.
     *
     * @param request the preflight request
     * @return the HTTP methods available for the requested URI
     */
    private List<HttpMethod> availableHttpMethods(HttpRequest<?> request) {
        List<HttpMethod> methods = new ArrayList<>(router != null
            ? router.findAny(request).stream().map(UriRouteMatch::getHttpMethod).toList()
            : Collections.emptyList()
        );
        if (CollectionUtils.isEmpty(methods) &&
            staticResourceResolver != null &&
            staticResourceResolver.resolve(request.getUri().getPath()).isPresent()) {
            methods.add(HttpMethod.GET);
        }
        return methods;
    }
}
