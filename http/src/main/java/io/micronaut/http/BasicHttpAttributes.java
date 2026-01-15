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
package io.micronaut.http;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.uri.UriMatchInfo;

import java.util.Optional;

/**
 * Accessors for basic attributes outside micronaut-http-router.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@SuppressWarnings("removal")
public final class BasicHttpAttributes {
    private static final String ROUTE_WAITS_FOR = BasicHttpAttributes.class.getName() + ".ROUTE_WAITS_FOR";

    private BasicHttpAttributes() {
    }

    /**
     * Get the route match as a {@link UriMatchInfo}.
     *
     * @param request The request
     * @return The route match, if present
     */
    public static Optional<UriMatchInfo> getRouteMatchInfo(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.ROUTE_MATCH, UriMatchInfo.class);
    }

    /**
     * Get the URI template as a String, for tracing.
     *
     * @param request The request
     * @return The template, if present
     */
    public static Optional<String> getUriTemplate(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.URI_TEMPLATE, String.class);
    }

    /**
     * Set the URI template as a String, for tracing.
     *
     * @param request     The request
     * @param uriTemplate The template, if present
     */
    public static void setUriTemplate(HttpRequest<?> request, String uriTemplate) {
        request.setAttribute(HttpAttributes.URI_TEMPLATE, uriTemplate);
    }

    /**
     * Get the client service ID.
     *
     * @param request The request
     * @return The client service ID
     */
    public static Optional<String> getServiceId(HttpRequest<?> request) {
        return request.getAttribute(HttpAttributes.SERVICE_ID, String.class);
    }

    /**
     * A condition that must be awaited before executing controllers for the given request. This is
     * used to delay execution for argument binding.
     *
     * @param request The request
     * @return The condition to wait for
     */
    @Experimental
    public static ExecutionFlow<?> getRouteWaitsFor(HttpRequest<?> request) {
        @SuppressWarnings("rawtypes")
        Optional<ExecutionFlow> attr = request.getAttribute(ROUTE_WAITS_FOR, ExecutionFlow.class);
        return attr.orElseGet(ExecutionFlow::empty);
    }

    /**
     * Add a condition that must be awaited before executing controllers for the given request.
     * This is used to delay execution for argument binding.
     *
     * @param request The request
     * @param flowToAdd The condition to wait for
     */
    @Experimental
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void addRouteWaitsFor(HttpRequest<?> request, ExecutionFlow<?> flowToAdd) {
        Optional<ExecutionFlow> attr = request.getAttribute(ROUTE_WAITS_FOR, ExecutionFlow.class);
        if (attr.isPresent()) {
            request.setAttribute(ROUTE_WAITS_FOR, attr.get().then(() -> flowToAdd));
        } else {
            request.setAttribute(ROUTE_WAITS_FOR, flowToAdd);
        }
    }
}
