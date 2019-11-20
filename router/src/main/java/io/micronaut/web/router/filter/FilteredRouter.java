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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.util.List;
import java.util.Optional;
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

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri) {
        return router.findAny(uri);
    }

    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@Nonnull CharSequence uri, @Nullable HttpRequest<?> context) {
        final Stream<UriRouteMatch<T, R>> matchStream = router.findAny(uri);
        if (context != null) {
            return matchStream.filter(routeFilter.filter(context));
        }
        return matchStream;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri) {
        return router.find(httpMethod, uri);
    }

    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri, @Nullable HttpRequest<?> context) {
        final Stream<UriRouteMatch<T, R>> matchStream = router.find(httpMethod, uri);
        if (context != null) {
            return matchStream.filter(routeFilter.filter(context));
        }
        return matchStream;
    }

    @Nonnull
    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(@Nonnull HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> closestMatches = router.findAllClosest(request);
        return closestMatches.stream().filter(routeFilter.filter(request))
                    .collect(Collectors.toList());
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest request, CharSequence uri) {
        return router.find(request, uri);
    }

    @Override
    public Stream<UriRoute> uriRoutes() {
        return router.uriRoutes();
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        return router.route(httpMethod, uri);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
        return router.route(status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class originatingClass, HttpStatus status) {
        return router.route(originatingClass, status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Throwable error) {
        return router.route(error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class originatingClass, Throwable error) {
        return router.route(originatingClass, error);
    }

    @Override
    public List<HttpFilter> findFilters(HttpRequest<?> request) {
        return router.findFilters(request);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> GET(CharSequence uri) {
        return router.GET(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> POST(CharSequence uri) {
        return router.POST(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PUT(CharSequence uri) {
        return router.PUT(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> PATCH(CharSequence uri) {
        return router.PATCH(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> DELETE(CharSequence uri) {
        return router.DELETE(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(CharSequence uri) {
        return router.OPTIONS(uri);
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> HEAD(CharSequence uri) {
        return router.HEAD(uri);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, URI uri) {
        return router.find(httpMethod, uri);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        Stream<UriRouteMatch<T, R>> matches = router.find(request);
        return matches.filter(routeFilter.filter(request));
    }
}
