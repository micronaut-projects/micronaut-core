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
import io.micronaut.http.*;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.http.server.cors.CorsUtil;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteMatch;
import static io.micronaut.http.annotation.Filter.MATCH_ALL_PATTERN;

/**
 * This Filter intercepts HTTP OPTIONS requests which are not CORS Preflight requests.
 * It responds with a NO_CONTENT(204) response, and it populates the Allow HTTP Header with the supported HTTP methods for the request URI.
 * @author Sergio del Amo
 * @since 4.2.0
 */
@Requires(property = "micronaut.server.dispatch-options-requests", value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@ServerFilter(MATCH_ALL_PATTERN)
public class OptionsFilter implements Ordered {
    private final Router router;

    /**
     *
     * @param router Router
     */
    public OptionsFilter(Router router) {
        this.router = router;
    }

    @RequestFilter
    @Nullable
    @Internal
    public final HttpResponse<?> filterRequest(HttpRequest<?> request) {
        if (request.getMethod() != HttpMethod.OPTIONS) {
            return null; // proceed
        }
        if (CorsUtil.isPreflightRequest(request)) {
            return null; // proceed
        }
        MutableHttpResponse<?> mutableHttpResponse = HttpResponse.status(HttpStatus.NO_CONTENT);
        router.findAny(request.getUri().toString(), request)
            .map(UriRouteMatch::getHttpMethod)
            .map(HttpMethod::toString)
            .forEach(allow -> mutableHttpResponse.header(HttpHeaders.ALLOW, allow));
        mutableHttpResponse.header(HttpHeaders.ALLOW, HttpMethod.OPTIONS.toString());
        return mutableHttpResponse;
    }
}
