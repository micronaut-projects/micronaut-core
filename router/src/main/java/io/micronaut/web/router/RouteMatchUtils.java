/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.web.router;

import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * @author Sergio del Amo
 * @since 1.0
 */
public class RouteMatchUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RouteMatchUtils.class);

    /**
     * @param request The Http request
     * @return The optional route match
     */
    public static Optional<RouteMatch> findRouteMatch(HttpRequest<?> request) {
        Optional<RouteMatch> routeMatchAttribute = request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class);
        if (routeMatchAttribute.isPresent()) {
            return routeMatchAttribute;
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Route match attribute for request ({}) not found", request.getPath());
            }
            return Optional.empty();
        }
    }
}
