/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.http.server;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.cors.CorsUtil;
import io.micronaut.web.router.RouteAttributes;
import io.micronaut.web.router.UriRouteMatch;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;
import static io.micronaut.http.server.cors.CorsFilter.CORS_FILTER_ORDER;

/**
 * This Filter intercepts HTTP OPTIONS requests which are not CORS Preflight requests.
 * It responds with an OK(200) response, and it populates the Allow HTTP Header with the supported HTTP methods for the request URI.
 * @author Sergio del Amo
 * @since 4.2.0
 */
@Requires(property = OptionsFilter.PREFIX, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ServerFilter(MATCH_ALL_PATTERN)
@Internal
public final class OptionsFilter implements Ordered {

    @SuppressWarnings("WeakerAccess")
    public static final String PREFIX = HttpServerConfiguration.PREFIX + ".dispatch-options-requests";

    @ResponseFilter
    @Nullable
    @Internal
    public HttpResponse<?> filterResponse(HttpRequest<?> request, MutableHttpResponse<?> response) {
        if (request.getMethod() != HttpMethod.OPTIONS) {
            return null; // proceed
        }
        if (CorsUtil.isPreflightRequest(request)) {
            return null; // proceed
        }
        if (hasOptionsRouteMatch(request)) {
            return null; // proceed
        }
        if (HttpStatus.METHOD_NOT_ALLOWED.equals(response.getStatus())) {
            List<String> allowedMethods = response.getHeaders().get(HttpHeaders.ALLOW, String[].class)
                .map(allow -> new ArrayList<>(Arrays.asList(allow))).orElse(new ArrayList<>());
            allowedMethods.add(HttpMethod.OPTIONS.toString());
            response.getHeaders().remove(HttpHeaders.ALLOW);
            response.getHeaders().allowGeneric(allowedMethods);
            response.status(HttpStatus.OK);
        }
        return response;
    }

    private boolean hasOptionsRouteMatch(HttpRequest<?> request) {
        return RouteAttributes.getRouteMatch(request).map(routeMatch -> {
            if (routeMatch instanceof UriRouteMatch<?, ?> uriRouteMatch) {
                return uriRouteMatch.getHttpMethod() == HttpMethod.OPTIONS;
            }
            return true;
        }).orElse(false);
    }

    @Override
    public int getOrder() {
        return CORS_FILTER_ORDER + 10;
    }
}
