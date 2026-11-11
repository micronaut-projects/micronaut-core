/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

/**
 * The rejection of a request by a route selector while it was routed, see
 * {@link io.micronaut.web.router.spi.RouteMatchSelector.Selection#rejectionStatus()}: the server
 * answers the request with the {@code 406} or {@code 415} of the rejected route, as the router
 * decided it, instead of finding the routes of the path again.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteRejections {

    private static final String ATTRIBUTE = "micronaut.router.selector.rejection";

    private RouteRejections() {
    }

    /**
     * @param request The request, after it was routed
     * @return The match a route selector rejected the request with, the match of the request for
     * a route of a located target, or {@code null}
     */
    public static @Nullable DefaultUriRouteMatch<?, ?> get(HttpRequest<?> request) {
        return request.getAttribute(ATTRIBUTE, DefaultUriRouteMatch.class).orElse(null);
    }

    /**
     * Forget the rejection of an earlier routing of the request.
     *
     * @param request The request
     */
    public static void clear(HttpRequest<?> request) {
        request.removeAttribute(ATTRIBUTE, DefaultUriRouteMatch.class);
    }

    static void set(HttpRequest<?> request, DefaultUriRouteMatch<?, ?> rejection) {
        request.setAttribute(ATTRIBUTE, rejection);
    }
}
