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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatcher;

import java.net.URI;
import java.util.Optional;

/**
 * Represents a {@link Route} that matches a {@link URI}.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
public interface UriRouteInfo<T, R> extends MethodBasedRouteInfo<T, R>, RequestMatcher, UriMatcher, Comparable<UriRouteInfo<T, R>> {

    /**
     * @return The HTTP method for this route
     */
    HttpMethod getHttpMethod();

    /**
     * @return The {@link UriMatchTemplate} used to match URIs
     */
    UriMatchTemplate getUriMatchTemplate();

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI The URI
     * @return An {@link Optional} of {@link RouteMatch}
     */
    @Override
    default Optional<UriRouteMatch<T, R>> match(URI uri) {
        return match(uri.toString());
    }

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI The URI
     * @return A null or a {@link RouteMatch}
     */
    @Nullable
    default UriRouteMatch<T, R> tryMatch(@NonNull URI uri) {
        return tryMatch(uri.toString());
    }

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI
     * @return An {@link Optional} of {@link RouteMatch}
     */
    @Override
    Optional<UriRouteMatch<T, R>> match(String uri);

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI
     * @return A null or a {@link RouteMatch}
     */
    @Nullable
    UriRouteMatch<T, R> tryMatch(@NonNull String uri);

    /**
     * @return The port the route listens to, or null if the default port
     */
    @Nullable
    Integer getPort();

    /**
     *
     * @return The http method. Is equal to {@link #getHttpMethod()} value for standard http methods.
     */
    default String getHttpMethodName() {
        return getHttpMethod().name();
    }
}
