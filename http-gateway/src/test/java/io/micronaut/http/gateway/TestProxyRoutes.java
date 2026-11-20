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
package io.micronaut.http.gateway;

import io.micronaut.web.router.builder.AsyncRequestHandler;
import io.micronaut.web.router.builder.HttpRouteScope;

/**
 * Plain routes of any method for a prefix and every path under it, as a test relays requests
 * with: {@code any(prefix)} and {@code any(prefix + "/{+path}")}.
 */
final class TestProxyRoutes {

    private TestProxyRoutes() {
    }

    static void proxy(HttpRouteScope routes, String prefix, AsyncRequestHandler handler) {
        String trimmed = prefix;
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        routes.any(trimmed.isEmpty() ? "/" : trimmed).consumesAll().handleAsync(handler);
        routes.any(trimmed + "/{+path}").consumesAll().handleAsync(handler);
    }
}
