/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.shortcircuit.PreparedMatchResult;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * <p>Core Router interface that allows discovery of a route given an HTTP method and URI.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("MethodName")
public interface Router {

    /**
     * Find any {@link RouteMatch} regardless of HTTP method.
     *
     * @param uri     The URI
     * @param context The optional {@link HttpRequest} context information to apply {@link io.micronaut.web.router.filter.RouteMatchFilter}.
     * @param <T>     The target type
     * @param <R>     The return type
     * @return A stream of route matches
     */
    @NonNull
    <T, R> Stream<UriRouteMatch<T, R>> findAny(@NonNull CharSequence uri, @Nullable HttpRequest<?> context);

    /**
     * Find any {@link RouteMatch} regardless of HTTP method.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The return type
     * @return A stream of route matches
     * @since 4.0.0
     */
    @NonNull
    <T, R> List<UriRouteMatch<T, R>> findAny(@NonNull HttpRequest<?> request);

    /**
     * @return The exposed ports.
     */
    Set<Integer> getExposedPorts();

    /**
     * Sets the ports the application will listen to by default.
     *
     * @param ports The default ports
     */
    void applyDefaultPorts(List<Integer> ports);

    /**
     * Finds all possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI route match
     * @param context    The optional {@link HttpRequest} context information to apply {@link io.micronaut.web.router.filter.RouteMatchFilter}.
     * @param <T>        The target type
     * @param <R>        The type
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @NonNull
    <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri, @Nullable HttpRequest<?> context);

    /**
     * Finds all possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI
     * @param context    The optional {@link HttpRequest} context
     * @param <T>        The target type
     * @param <R>        The URI route match
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @NonNull
    default <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull URI uri, @Nullable HttpRequest<?> context) {
        return find(httpMethod, uri.toString(), context);
    }

    /**
     * Finds all possible routes for the given HTTP request.
     *
     * @param request The HTTP request
     * @param <T>     The target type
     * @param <R>     The URI route match
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @NonNull
    default <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request) {
        return find(request, request.getPath());
    }

    /**
     * Find method, that should be used for non-standard http methods. For standards, it should act
     * the same as {@link #find(HttpMethod, CharSequence, HttpRequest)}
     * @param request The request, that can have overridden {@link HttpRequest#getMethodName()}
     * @param uri     The URI route match.
     * @param <T>     The target type.
     * @param <R>     The type of what
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @NonNull
    default  <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request, @NonNull CharSequence uri) {
        return find(HttpMethod.valueOf(request.getMethodName()), uri, request);
    }

    /**
     * Finds the closest match for the given request.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The type
     * @return A {@link List} of possible {@link Route} instances.
     * @since 1.2.1
     */
    @NonNull
    <T, R> List<UriRouteMatch<T, R>> findAllClosest(@NonNull HttpRequest<?> request);

    /**
     * Finds the closest match for the given request or null if none is found.
     *
     * @param request The request
     * @param <T>     The target type
     * @param <R>     The type
     * @return A match or null, throws {@link DuplicateRouteException} on multiple routes.
     * @since 4.0.0
     */
    @Nullable
    default <T, R> UriRouteMatch<T, R> findClosest(@NonNull HttpRequest<?> request) throws DuplicateRouteException {
        List<UriRouteMatch<T, R>> uriRoutes = findAllClosest(request);
        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(request.getPath(), (List) uriRoutes);
        } else if (uriRoutes.size() == 1) {
            return uriRoutes.get(0);
        }
        return null;
    }

    @Internal
    @Nullable
    default PreparedMatchResult findPreparedMatchResult(@NonNull HttpRequest<?> request) {
        return null;
    }

    /**
     * Returns all UriRoutes.
     *
     * @return A {@link Stream} of all registered {@link UriRoute} instances.
     */
    @NonNull
    Stream<UriRouteInfo<?, ?>> uriRoutes();

    /**
     * Finds the first possible route for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI
     * @param <T>        The target type
     * @param <R>        The URI route match
     * @return The route match
     */
    <T, R> Optional<UriRouteMatch<T, R>> route(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param status The HTTP status
     * @param <R>    The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@NonNull HttpStatus status);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param originatingClass The class the error originates from
     * @param status           The HTTP status
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@NonNull Class<?> originatingClass, @NonNull HttpStatus status);

    /**
     * Match a route to an error.
     *
     * @param error The error
     * @param <R>   The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@NonNull Throwable error);

    /**
     * Match a route to an error.
     *
     * @param originatingClass The class the error originates from
     * @param error            The error
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@NonNull Class<?> originatingClass, @NonNull Throwable error);

    /**
     * Match a route to an error.
     *
     * @param originatingClass The class the error originates from
     * @param error            The error
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> findErrorRoute(
            @NonNull Class<?> originatingClass,
            @NonNull Throwable error,
            HttpRequest<?> request);

    /**
     * Match a route to an error.
     *
     * @param error            The error
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> findErrorRoute(
            @NonNull Throwable error,
            HttpRequest<?> request);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param originatingClass The class the error originates from
     * @param status           The HTTP status
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> findStatusRoute(
            @NonNull Class<?> originatingClass,
            @NonNull HttpStatus status,
            HttpRequest<?> request);

    /**
     * Found a {@link RouteMatch} for the given status code.
     *
     * @param originatingClass The class the error originates from
     * @param statusCode       The HTTP status
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    default <R> Optional<RouteMatch<R>> findStatusRoute(
            @NonNull Class<?> originatingClass,
            int statusCode,
            HttpRequest<?> request) {
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(statusCode);
        } catch (IllegalArgumentException iae) {
            // custom status code
            return Optional.empty();
        }
        return findStatusRoute(originatingClass, status, request);
    }

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param status           The HTTP status
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> findStatusRoute(
            @NonNull HttpStatus status,
            HttpRequest<?> request);

    /**
     * Found a {@link RouteMatch} for the given status code.
     *
     * @param statusCode       The HTTP status code
     * @param request          The request
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    default <R> Optional<RouteMatch<R>> findStatusRoute(
            int statusCode,
            HttpRequest<?> request) {
        HttpStatus status;
        try {
            status = HttpStatus.valueOf(statusCode);
        } catch (IllegalArgumentException iae) {
            // custom status code
            return Optional.empty();
        }
        return findStatusRoute(status, request);
    }

    /**
     * Build a filtered {@link org.reactivestreams.Publisher} for an action.
     *
     * @param request The request
     * @return A new filtered publisher
     */
    @NonNull List<GenericHttpFilter> findFilters(
            @NonNull HttpRequest<?> request
    );

    /**
     * Get the fixed (request-independent) filter list. If this method returns anything but
     * optional, <i>any</i> call to {@link #findFilters} must return the same filters as returned
     * by this method.
     *
     * @return The fixed filter list, or {@link Optional#empty()} if the filter list is dynamic
     */
    @Internal
    default Optional<List<GenericHttpFilter>> getFixedFilters() {
        return Optional.empty();
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#GET} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> GET(@NonNull CharSequence uri) {
        return route(HttpMethod.GET, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#POST} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> POST(@NonNull CharSequence uri) {
        return route(HttpMethod.POST, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#PUT} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The URI route match
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> PUT(@NonNull CharSequence uri) {
        return route(HttpMethod.PUT, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#PATCH} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> PATCH(@NonNull CharSequence uri) {
        return route(HttpMethod.PATCH, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#DELETE} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> DELETE(@NonNull CharSequence uri) {
        return route(HttpMethod.DELETE, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#OPTIONS} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(@NonNull CharSequence uri) {
        return route(HttpMethod.OPTIONS, uri);
    }

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#HEAD} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> HEAD(@NonNull CharSequence uri) {
        return route(HttpMethod.HEAD, uri);
    }

}
