/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.web.router.filter;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.web.router.RouteMatch;
import io.micronaut.web.router.Router;
import io.micronaut.web.router.UriRoute;
import io.micronaut.web.router.UriRouteMatch;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Allows decorating an existing {@link Router} with filtering capabilities.
 *
 * <p>Filters themselves should be supplied via the {@link RouteMatchFilter} interface.</p>
 *
 * <p>A filtered router can be enabled by implementing a {@link io.micronaut.context.event.BeanCreatedEventListener} for
 * the existing {@link Router} and decorating appropriately. See for example {@link io.micronaut.web.router.version.VersionAwareRouterListener}</p>
 *
 * @see RouteMatchFilter
 * @author Bogdan Oros
 * @author graemerocher
 * @since 1.1.0
 */
public class FilteredRouter implements Router {

    private final Router router;
    private final RouteMatchFilter routeFilter;

    /**
     * Creates a decorated router for an existing router and {@link RouteMatchFilter}.
     *
     * @param router      A {@link Router} to delegate to
     * @param routeFilter A {@link RouteMatchFilter} to filter non matching routes
     */
    public FilteredRouter(Router router,
                          RouteMatchFilter routeFilter) {
        this.router = router;
        this.routeFilter = routeFilter;
    }

    @NonNull
    @Deprecated
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@NonNull CharSequence uri) {
        return router.findAny(uri, null);
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@NonNull CharSequence uri, @Nullable HttpRequest<?> context) {
        final Stream<UriRouteMatch<T, R>> matchStream = router.findAny(uri, context);
        if (context != null) {
            return matchStream.filter(routeFilter.filter(context));
        }
        return matchStream;
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return router.getExposedPorts();
    }

    @Override
    public void applyDefaultPorts(List<Integer> ports) {
        router.applyDefaultPorts(ports);
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri) {
        return router.find(httpMethod, uri, null);
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri, @Nullable HttpRequest<?> context) {
        final Stream<UriRouteMatch<T, R>> matchStream = router.find(httpMethod, uri, context);
        if (context != null) {
            return matchStream.filter(routeFilter.filter(context));
        }
        return matchStream;
    }

    @NonNull
    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(@NonNull HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> closestMatches = router.findAllClosest(request);
        return closestMatches.stream().filter(routeFilter.filter(request))
                    .collect(Collectors.toList());
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest request, @NonNull CharSequence uri) {
        return router.find(request, uri);
    }

    @NonNull
    @Override
    public Stream<UriRoute> uriRoutes() {
        return router.uriRoutes();
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri) {
        return router.route(httpMethod, uri);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull HttpStatus status) {
        return router.route(status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Class originatingClass, @NonNull HttpStatus status) {
        return router.route(originatingClass, status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Throwable error) {
        return router.route(error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Class originatingClass, @NonNull Throwable error) {
        return router.route(originatingClass, error);
    }

    @NonNull
    @Override
    public List<HttpFilter> findFilters(@NonNull HttpRequest<?> request) {
        return router.findFilters(request);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> GET(@NonNull CharSequence uri) {
        return router.GET(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> POST(@NonNull CharSequence uri) {
        return router.POST(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PUT(@NonNull CharSequence uri) {
        return router.PUT(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PATCH(@NonNull CharSequence uri) {
        return router.PATCH(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> DELETE(@NonNull CharSequence uri) {
        return router.DELETE(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(@NonNull CharSequence uri) {
        return router.OPTIONS(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> HEAD(@NonNull CharSequence uri) {
        return router.HEAD(uri);
    }

    @NonNull
    @Deprecated
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull URI uri) {
        return router.find(httpMethod, uri, null);
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request) {
        Stream<UriRouteMatch<T, R>> matches = router.find(request);
        return matches.filter(routeFilter.filter(request));
    }
}
