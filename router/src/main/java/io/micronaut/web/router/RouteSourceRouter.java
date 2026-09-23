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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.filter.RouteMatchFilter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Decorates the application {@link Router} with the tables of the {@link RouteSource}s.
 * <p>Requests are matched tier by tier: first the application routes, then each source in order.
 * The {@link RouteMatchFilter}s (e.g. versioning) are applied to each tier before deciding whether
 * it has a match, so a route rejected by a filter falls through to the next tier, and the routes of
 * a source are filtered like the application routes. Applying a filter again is harmless, so this
 * holds whether a {@link io.micronaut.web.router.filter.FilteredRouter} wraps this router or the
 * application router.
 * <p>The tables are captured once per request and kept in a request attribute, so every lookup for
 * the same request sees the same tables.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteSourceRouter implements Router {

    private static final String SNAPSHOT_ATTRIBUTE = "micronaut.router.route-source.snapshot";

    private final Router router;
    private final Supplier<List<RouteSource>> sources;
    private final Supplier<List<RouteMatchFilter>> filters;
    @Nullable
    private volatile List<Integer> defaultPorts;

    /**
     * @param router  The application router
     * @param sources The route sources, in order
     * @param filters The route match filters
     */
    RouteSourceRouter(Router router, Supplier<List<RouteSource>> sources, Supplier<List<RouteMatchFilter>> filters) {
        this.router = router;
        this.sources = sources;
        this.filters = filters;
    }

    /**
     * The routers of the source tables for a request: captured once and reused for the rest of
     * the request.
     */
    private List<DefaultRouter> tables(@Nullable HttpRequest<?> request) {
        if (request != null && request.getAttribute(SNAPSHOT_ATTRIBUTE).orElse(null) instanceof Snapshot snapshot) {
            return snapshot.tables;
        }
        List<RouteSource> routeSources = sources.get();
        List<DefaultRouter> tables = new ArrayList<>(routeSources.size());
        List<Integer> ports = defaultPorts;
        for (RouteSource source : routeSources) {
            RouteTable table = source.snapshot();
            if (table == null) {
                throw new IllegalStateException("Route source " + source + " returned no route table");
            }
            // sealed: the RouteTableFactory builds every table
            DefaultRouteTable defaultTable = (DefaultRouteTable) table;
            if (!defaultTable.isEmpty()) {
                tables.add(defaultTable.router(ports));
            }
        }
        if (request != null) {
            request.setAttribute(SNAPSHOT_ATTRIBUTE, new Snapshot(tables));
        }
        return tables;
    }

    private <T, R> @Nullable Predicate<UriRouteMatch<T, R>> predicate(@Nullable HttpRequest<?> request) {
        if (request == null) {
            return null;
        }
        List<RouteMatchFilter> routeMatchFilters = filters.get();
        if (routeMatchFilters.isEmpty()) {
            return null;
        }
        Predicate<UriRouteMatch<T, R>> predicate = routeMatchFilters.get(0).filter(request);
        for (int i = 1; i < routeMatchFilters.size(); i++) {
            predicate = predicate.and(routeMatchFilters.get(i).filter(request));
        }
        return predicate;
    }

    private static <T, R> List<UriRouteMatch<T, R>> filter(List<UriRouteMatch<T, R>> matches, @Nullable Predicate<UriRouteMatch<T, R>> predicate) {
        if (predicate == null || matches.isEmpty()) {
            return matches;
        }
        return matches.stream().filter(predicate).toList();
    }

    private static <T, R> Stream<UriRouteMatch<T, R>> filter(Stream<UriRouteMatch<T, R>> matches, @Nullable Predicate<UriRouteMatch<T, R>> predicate) {
        return predicate == null ? matches : matches.filter(predicate);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(request);
        List<UriRouteMatch<T, R>> matches = filter(router.findAllClosest(request), predicate);
        if (!matches.isEmpty()) {
            return matches;
        }
        for (DefaultRouter table : tables(request)) {
            List<UriRouteMatch<T, R>> tableMatches = filter(table.findAllClosest(request), predicate);
            if (!tableMatches.isEmpty()) {
                return tableMatches;
            }
        }
        return matches;
    }

    @Override
    public <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        if (filters.get().isEmpty()) {
            UriRouteMatch<T, R> match = router.findClosest(request);
            if (match != null) {
                return match;
            }
            for (DefaultRouter table : tables(request)) {
                match = table.findClosest(request);
                if (match != null) {
                    return match;
                }
            }
            return null;
        }
        List<UriRouteMatch<T, R>> matches = findAllClosest(request);
        if (matches.size() > 1) {
            matches = ImplicitHeadRoutes.preferExplicit(matches);
        }
        if (matches.size() > 1) {
            matches = RouteOrders.preferLowest(matches);
        }
        if (matches.size() > 1) {
            throw new DuplicateRouteException(request.getPath(), (List) matches);
        }
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(request);
        List<UriRouteMatch<T, R>> matches = filter(router.findAny(request), predicate);
        List<UriRouteMatch<T, R>> all = null;
        for (DefaultRouter table : tables(request)) {
            List<UriRouteMatch<T, R>> tableMatches = filter(table.findAny(request), predicate);
            if (!tableMatches.isEmpty()) {
                if (all == null) {
                    all = new ArrayList<>(matches);
                }
                all.addAll(tableMatches);
            }
        }
        return all == null ? matches : all;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> context) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(context);
        Stream<UriRouteMatch<T, R>> matches = filter(router.findAny(uri, context), predicate);
        for (DefaultRouter table : tables(context)) {
            matches = Stream.concat(matches, filter(table.findAny(uri, context), predicate));
        }
        return matches;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(context);
        Stream<UriRouteMatch<T, R>> matches = filter(router.find(httpMethod, uri, context), predicate);
        for (DefaultRouter table : tables(context)) {
            matches = Stream.concat(matches, filter(table.find(httpMethod, uri, context), predicate));
        }
        return matches;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(request);
        Stream<UriRouteMatch<T, R>> matches = filter(router.find(request), predicate);
        for (DefaultRouter table : tables(request)) {
            matches = Stream.concat(matches, filter(table.find(request), predicate));
        }
        return matches;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        Predicate<UriRouteMatch<T, R>> predicate = predicate(request);
        Stream<UriRouteMatch<T, R>> matches = filter(router.find(request, uri), predicate);
        for (DefaultRouter table : tables(request)) {
            matches = Stream.concat(matches, filter(table.find(request, uri), predicate));
        }
        return matches;
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        Optional<UriRouteMatch<T, R>> match = router.route(httpMethod, uri);
        if (match.isPresent()) {
            return match;
        }
        for (DefaultRouter table : tables(null)) {
            match = table.route(httpMethod, uri);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        Stream<UriRouteInfo<?, ?>> routes = router.uriRoutes();
        for (DefaultRouter table : tables(null)) {
            routes = Stream.concat(routes, table.uriRoutes().distinct());
        }
        return routes;
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return router.getExposedPorts();
    }

    @Override
    public void applyDefaultPorts(List<Integer> ports) {
        router.applyDefaultPorts(ports);
        defaultPorts = List.copyOf(ports);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
        return router.route(status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, HttpStatus status) {
        return router.route(originatingClass, status);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Throwable error) {
        return router.route(error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, Throwable error) {
        return router.route(originatingClass, error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(Class<?> originatingClass, Throwable error, HttpRequest<?> request) {
        return router.findErrorRoute(originatingClass, error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(Throwable error, HttpRequest<?> request) {
        return router.findErrorRoute(error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, HttpStatus status, HttpRequest<?> request) {
        return router.findStatusRoute(originatingClass, status, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, int statusCode, HttpRequest<?> request) {
        return router.findStatusRoute(originatingClass, statusCode, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(HttpStatus status, HttpRequest<?> request) {
        return router.findStatusRoute(status, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(int statusCode, HttpRequest<?> request) {
        return router.findStatusRoute(statusCode, request);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request) {
        return router.findFilters(request);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch) {
        return router.findFilters(request, routeMatch);
    }

    @Override
    public List<GenericHttpFilter> findPreMatchingFilters(HttpRequest<?> request) {
        return router.findPreMatchingFilters(request);
    }

    /**
     * The tables of one request.
     *
     * @param tables The routers of the tables
     */
    private record Snapshot(List<DefaultRouter> tables) {
    }
}
