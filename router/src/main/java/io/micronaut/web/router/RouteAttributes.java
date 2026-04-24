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
import io.micronaut.http.HttpAttributes;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import java.util.Optional;

/**
 * Accessors for various route- and server-related attributes.
 *
 * @author Jonas Konrad
 * @since 4.8.0
 */
@SuppressWarnings("removal")
public final class RouteAttributes {
    private RouteAttributes() {
    }

    /**
     * Get the route match.
     *
     * @param request The request
     * @return The route match, if present
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<RouteMatch<?>> getRouteMatch(HttpRequest<?> request) {
        return (Optional) request.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class);
    }

    /**
     * Set the route match.
     *
     * @param request    The request
     * @param routeMatch The route match
     */
    public static void setRouteMatch(HttpRequest<?> request, RouteMatch<?> routeMatch) {
        request.setAttribute(HttpAttributes.ROUTE_MATCH, routeMatch);
    }

    /**
     * Get the route match.
     *
     * @param response The response
     * @return The route match, if present
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<RouteMatch<?>> getRouteMatch(HttpResponse<?> response) {
        return (Optional) response.getAttribute(HttpAttributes.ROUTE_MATCH, RouteMatch.class);
    }

    /**
     * Set the route match.
     *
     * @param response   The response
     * @param routeMatch The route match
     */
    public static void setRouteMatch(HttpResponse<?> response, RouteMatch<?> routeMatch) {
        response.setAttribute(HttpAttributes.ROUTE_MATCH, routeMatch);
    }

    /**
     * Get the route info.
     *
     * @param request The request
     * @return The route info, if present
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<RouteInfo<?>> getRouteInfo(HttpRequest<?> request) {
        return (Optional) request.getAttribute(HttpAttributes.ROUTE_INFO, RouteInfo.class);
    }

    /**
     * Set the route info.
     *
     * @param request   The request
     * @param routeInfo The route info
     */
    public static void setRouteInfo(HttpRequest<?> request, RouteInfo<?> routeInfo) {
        request.setAttribute(HttpAttributes.ROUTE_INFO, routeInfo);
    }

    /**
     * Get the route info.
     *
     * @param response The response
     * @return The route info, if present
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static Optional<RouteInfo<?>> getRouteInfo(HttpResponse<?> response) {
        // don't convert to RouteInfo to avoid type pollution
        return (Optional) response.getAttribute(HttpAttributes.ROUTE_INFO);
    }

    /**
     * Set the route info.
     *
     * @param response  The response
     * @param routeInfo The route info
     */
    public static void setRouteInfo(HttpResponse<?> response, RouteInfo<?> routeInfo) {
        response.setAttribute(HttpAttributes.ROUTE_INFO, routeInfo);
    }

    /**
     * Get the exception that triggered this response.
     *
     * @param response The response
     * @return The exception, if present
     */
    public static Optional<Throwable> getException(HttpResponse<?> response) {
        return response.getAttribute(HttpAttributes.EXCEPTION, Throwable.class);
    }

    /**
     * Set the exception that triggered this response.
     *
     * @param response  The response
     * @param throwable The exception
     */
    public static void setException(HttpResponse<?> response, Throwable throwable) {
        response.setAttribute(HttpAttributes.EXCEPTION, throwable);
    }

    /**
     * Get the body that was discarded because this is a response to a HEAD request.
     *
     * @param response The response
     * @return The discarded body, if present
     */
    public static Optional<Object> getHeadBody(HttpResponse<?> response) {
        return response.getAttribute(HttpAttributes.HEAD_BODY);
    }

    /**
     * Set the body that was discarded because this is a response to a HEAD request.
     *
     * @param response The response
     * @param body     The body
     */
    public static void setHeadBody(HttpResponse<?> response, Object body) {
        response.setAttribute(HttpAttributes.HEAD_BODY, body);
    }
}
