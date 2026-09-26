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
package io.micronaut.web.router;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.HttpRequest;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The error and status routes declared in the groups of handler routes, see
 * {@link io.micronaut.web.router.builder.HttpRouteGroup}: local to the routes of the group, like
 * the non-global {@code @Error} methods of a controller are local to its routes. The error route
 * of a request is looked up in the innermost group of its route first, then in the groups around
 * it, and for a route resolved by a {@link DynamicRouteTarget}, then in the error scopes of the
 * resolution. Within a group, the error route of the closest exception type answers.
 * The server looks the local error routes of the declaring type up before, and the global error
 * routes after them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class GroupErrorRoutes {

    private GroupErrorRoutes() {
    }

    /**
     * The error route of the groups of the route of a request.
     *
     * @param request   The request, with the route it matched
     * @param routeInfo The route that failed, or {@code null}
     * @param error     The error
     * @param <R>       The result type
     * @return The match of the error route, or {@code null} if no group of the route handles the error
     */
    public static <R> @Nullable RouteMatch<R> findErrorRoute(HttpRequest<?> request, @Nullable RouteInfo<?> routeInfo, Throwable error) {
        if (!(routeInfo instanceof DefaultUrlRouteInfo<?, ?> route)) {
            return null;
        }
        RouteMatch<R> match = findErrorRoute(route.errorScope, request, error);
        if (match != null) {
            return match;
        }
        for (RouteAssembly.RouteGroup scope : resolvedScopes(request, route)) {
            match = findErrorRoute(scope, request, error);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * The status route of the groups of the route of a request.
     *
     * @param request   The request, with the route it matched
     * @param routeInfo The route that answered the status, or {@code null}
     * @param status    The status code
     * @param <R>       The result type
     * @return The match of the status route, or {@code null} if no group of the route handles the status
     */
    public static <R> @Nullable RouteMatch<R> findStatusRoute(HttpRequest<?> request, @Nullable RouteInfo<?> routeInfo, int status) {
        if (!(routeInfo instanceof DefaultUrlRouteInfo<?, ?> route)) {
            return null;
        }
        RouteMatch<R> match = findStatusRoute(route.errorScope, request, status);
        if (match != null) {
            return match;
        }
        for (RouteAssembly.RouteGroup scope : resolvedScopes(request, route)) {
            match = findStatusRoute(scope, request, status);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static <R> @Nullable RouteMatch<R> findErrorRoute(RouteAssembly.@Nullable RouteGroup scope, HttpRequest<?> request, Throwable error) {
        for (RouteAssembly.RouteGroup group = scope; group != null; group = group.enclosing()) {
            ErrorRouteInfo<Object, Object>[] routes = group.errorRouteInfos();
            if (routes.length != 0) {
                Optional<RouteMatch<R>> match = DefaultRouter.findErrorRoute(routes, null, error, request);
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        return null;
    }

    private static <R> @Nullable RouteMatch<R> findStatusRoute(RouteAssembly.@Nullable RouteGroup scope, HttpRequest<?> request, int status) {
        for (RouteAssembly.RouteGroup group = scope; group != null; group = group.enclosing()) {
            StatusRouteInfo<Object, Object>[] routes = group.statusRouteInfos();
            if (routes.length != 0) {
                Optional<RouteMatch<R>> match = DefaultRouter.findStatusRoute(routes, null, status, request);
                if (match.isPresent()) {
                    return match.get();
                }
            }
        }
        return null;
    }

    /**
     * The error scopes of the dynamic target that resolved the matched route, see {@link DynamicRouteTarget}.
     */
    private static List<RouteAssembly.RouteGroup> resolvedScopes(HttpRequest<?> request, DefaultUrlRouteInfo<?, ?> route) {
        RouteMatch<?> match = RouteAttributes.getRouteMatch(request).orElse(null);
        if (match instanceof DefaultUriRouteMatch<?, ?> uriMatch
            && uriMatch.getRouteInfo() == route
            && uriMatch.matchInfo() instanceof DynamicRouteTarget.ResolvedMatchInfo resolved) {
            return resolved.errorScopes();
        }
        return List.of();
    }
}
