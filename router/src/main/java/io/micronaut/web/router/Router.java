/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.web.router;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;

import java.net.URI;
import java.util.List;
import java.util.Optional;
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
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return A stream of route matches
     */
    <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri);

    /**
     * Finds all of the possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI route match
     * @param <T> The target type
     * @param <R>        The type
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri);

    /**
     * Returns all UriRoutes.
     *
     * @return A {@link Stream} of all registered {@link UriRoute} instances.
     */
    Stream<UriRoute> uriRoutes();

    /**
     * Finds the first possible route for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI
     * @param <T> The target type
     * @param <R>        The URI route match
     * @return The route match
     */
    <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param status The HTTP status
     * @param <R>    The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(HttpStatus status);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param originatingClass The class the error originates from
     * @param status The HTTP status
     * @param <R>    The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(Class originatingClass, HttpStatus status);

    /**
     * Match a route to an error.
     *
     * @param error The error
     * @param <R>   The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(Throwable error);

    /**
     * Match a route to an error.
     *
     * @param originatingClass The class the error originates from
     * @param error            The error
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(Class originatingClass, Throwable error);

    /**
     * Build a filtered {@link org.reactivestreams.Publisher} for an action.
     *
     * @param request The request
     * @return A new filtered publisher
     */
    List<HttpFilter> findFilters(
        HttpRequest<?> request
    );

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#GET} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> GET(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> POST(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> PUT(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> PATCH(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> DELETE(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> HEAD(CharSequence uri) {
        return route(HttpMethod.HEAD, uri);
    }

    /**
     * Finds all of the possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI
     * @param <T> The target type
     * @param <R>        The URI route match
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    default <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, URI uri) {
        return find(httpMethod, uri.toString());
    }

    /**
     * Finds all of the possible routes for the given HTTP request.
     *
     * @param request The HTTP request
     * @param <T>     The target type
     * @param <R>     The URI route match
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    default <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        return find(request.getMethod(), request.getPath());
    }

}
