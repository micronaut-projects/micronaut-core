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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Decorates the application {@link Router} so that the {@link RouteSource}s are consulted for
 * requests it has no route for.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class RouteSourceRouter implements Router {

    private final Router router;
    private final Supplier<List<RouteSource>> sources;

    /**
     * @param router  The application router
     * @param sources The route sources, in order
     */
    RouteSourceRouter(Router router, Supplier<List<RouteSource>> sources) {
        this.router = router;
        this.sources = sources;
    }

    private List<RouteSource> sources() {
        return sources.get();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> context) {
        return router.findAny(uri, context);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> matches = router.findAny(request);
        List<UriRouteMatch<T, R>> all = null;
        for (RouteSource source : sources()) {
            List<UriRouteMatch<T, R>> sourceMatches = source.findAny(request);
            if (!sourceMatches.isEmpty()) {
                if (all == null) {
                    all = new ArrayList<>(matches);
                }
                all.addAll(sourceMatches);
            }
        }
        return all == null ? matches : all;
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return router.getExposedPorts();
    }

    @Override
    public void applyDefaultPorts(List<Integer> ports) {
        router.applyDefaultPorts(ports);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        return router.find(httpMethod, uri, context);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> matches = router.findAllClosest(request);
        if (!matches.isEmpty()) {
            return matches;
        }
        for (RouteSource source : sources()) {
            List<UriRouteMatch<T, R>> sourceMatches = source.findAllClosest(request);
            if (!sourceMatches.isEmpty()) {
                return sourceMatches;
            }
        }
        return matches;
    }

    @Override
    public <T, R> @Nullable UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        UriRouteMatch<T, R> match = router.findClosest(request);
        if (match != null) {
            return match;
        }
        for (RouteSource source : sources()) {
            match = source.findClosest(request);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        return router.find(request, uri);
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
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
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        return router.find(request);
    }
}
