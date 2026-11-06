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
package io.micronaut.dev.http;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.builder.RequestCondition;

/**
 * The check the server makes of the body conditions of its routes when it is created, made again of a route table that
 * a rebuild in place made: a body condition reads at most what the server may buffer. Only loaded with the HTTP server.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class DevRouteLimits {

    private DevRouteLimits() {
    }

    /**
     * @param context The context, whose server configuration gives the limit
     * @param router  The router of the new table
     * @throws ConfigurationException if a body condition of a route reads more than the server may buffer
     */
    static void check(BeanContext context, Router router) {
        HttpServerConfiguration configuration = context.findBean(HttpServerConfiguration.class).orElse(null);
        if (configuration == null) {
            return;
        }
        long maxRequestBufferSize = configuration.getMaxRequestBufferSize();
        // an implicit HEAD route has the conditions of its GET route, which names them
        router.uriRoutes().filter(route -> !route.isImplicitHead()).forEach(route -> check(route, maxRequestBufferSize));
    }

    private static void check(UriRouteInfo<?, ?> route, long maxRequestBufferSize) {
        for (RequestCondition.Body<?> condition : route.getBodyConditions()) {
            int maxBytes = condition.maxBytes();
            if (maxBytes != RequestCondition.Body.SERVER_LIMIT && maxBytes > maxRequestBufferSize) {
                throw new ConfigurationException("A body condition of the route " + route + " reads up to "
                    + maxBytes + " bytes, maxBytes(" + maxBytes + "), more than the " + maxRequestBufferSize
                    + " bytes of micronaut.server.max-request-buffer-size that the server may buffer: lower the maxBytes of the condition, "
                    + "or raise micronaut.server.max-request-buffer-size");
            }
        }
    }
}
