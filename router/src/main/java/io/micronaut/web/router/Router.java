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
package io.micronaut.web.router;

import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.filter.HttpFilter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
    @Nonnull <T, R> Stream<UriRouteMatch<T, R>> findAny(@Nonnull CharSequence uri);

    /**
     * Find any {@link RouteMatch} regardless of HTTP method.
     *
     * @param uri     The URI
     * @param context The optional {@link HttpRequest} context information to apply {@link io.micronaut.web.router.filter.RouteMatchFilter}.
     * @param <T>     The target type
     * @param <R>     The return type
     * @return A stream of route matches
     */
    @Nonnull <T, R> Stream<UriRouteMatch<T, R>> findAny(@Nonnull CharSequence uri, @Nullable HttpRequest<?> context);

    /**
     * Finds all of the possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI route match
     * @param <T> The target type
     * @param <R>        The type
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @Nonnull <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri);

    /**
     * Finds all of the possible routes for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI route match
     * @param context    The optional {@link HttpRequest} context information to apply {@link io.micronaut.web.router.filter.RouteMatchFilter}.
     * @param <T>        The target type
     * @param <R>        The type
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    @Nonnull <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri, @Nullable HttpRequest<?> context);

    /**
     * Finds the closest match for the given request.
     *
     * @param request The request
     * @param <T> The target type
     * @param <R>        The type
     * @return A {@link List} of possible {@link Route} instances.
     * @since 1.2.1
     */
    @Nonnull <T, R> List<UriRouteMatch<T, R>> findAllClosest(@Nonnull HttpRequest<?> request);

    /**
     * Find method, that should be used for non-standard http methods. For standards it should act
     * the same as {@link #find(HttpMethod, URI)}
     * @param request The request, that can have overridden {@link HttpRequest#getMethodName()}
     * @param uri The URI route match.
     * @param <T> The target type.
     * @param <R> The type of what
     * @return A {@link Stream} of possible {@link Route} instances.
     */
    default <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest request, CharSequence uri) {
        return find(HttpMethod.valueOf(request.getMethodName()), uri);
    }

    /**
     * Returns all UriRoutes.
     *
     * @return A {@link Stream} of all registered {@link UriRoute} instances.
     */
    @Nonnull Stream<UriRoute> uriRoutes();

    /**
     * Finds the first possible route for the given HTTP method and URI.
     *
     * @param httpMethod The HTTP method
     * @param uri        The URI
     * @param <T> The target type
     * @param <R>        The URI route match
     * @return The route match
     */
    <T, R> Optional<UriRouteMatch<T, R>> route(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param status The HTTP status
     * @param <R>    The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@Nonnull HttpStatus status);

    /**
     * Found a {@link RouteMatch} for the given {@link io.micronaut.http.HttpStatus} code.
     *
     * @param originatingClass The class the error originates from
     * @param status The HTTP status
     * @param <R>    The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@Nonnull Class originatingClass, @Nonnull HttpStatus status);

    /**
     * Match a route to an error.
     *
     * @param error The error
     * @param <R>   The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@Nonnull Throwable error);

    /**
     * Match a route to an error.
     *
     * @param originatingClass The class the error originates from
     * @param error            The error
     * @param <R>              The matched route
     * @return The {@link RouteMatch}
     */
    <R> Optional<RouteMatch<R>> route(@Nonnull Class originatingClass, @Nonnull Throwable error);

    /**
     * Build a filtered {@link org.reactivestreams.Publisher} for an action.
     *
     * @param request The request
     * @return A new filtered publisher
     */
    @Nonnull List<HttpFilter> findFilters(
            @Nonnull HttpRequest<?> request
    );

    /**
     * Find the first {@link RouteMatch} route for an {@link HttpMethod#GET} method and the given URI.
     *
     * @param uri The URI
     * @param <T> The target type
     * @param <R> The return type
     * @return An {@link Optional} of {@link RouteMatch}
     */
    default <T, R> Optional<UriRouteMatch<T, R>> GET(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> POST(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> PUT(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> PATCH(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> DELETE(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> OPTIONS(@Nonnull CharSequence uri) {
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
    default <T, R> Optional<UriRouteMatch<T, R>> HEAD(@Nonnull CharSequence uri) {
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
    default @Nonnull <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull URI uri) {
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
    default @Nonnull <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpRequest<?> request) {
        return find(request, request.getPath());
    }

}
