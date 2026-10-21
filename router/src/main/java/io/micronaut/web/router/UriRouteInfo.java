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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriRouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriMatcher;

import java.net.URI;
import java.util.List;
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
     * @throws UnsupportedOperationException for a route whose template is not a URI template, see
     * {@link #getRouteTemplate()}: since 5.3.0, a route declared with a template of another
     * language, e.g. with {@code route(HttpMethod, RouteTemplate)}, in a group with
     * {@code templates(RouteTemplateParser)}, or the locator route of such a prefix
     */
    UriMatchTemplate getUriMatchTemplate();

    /**
     * The template of the route, of any template language.
     * Unlike {@link #getUriMatchTemplate()} it is available for every route; use it for labels,
     * logging and metrics.
     *
     * <p>For a route with a URI template, it is a {@link UriRouteTemplate} of {@link #getUriMatchTemplate()}. The
     * {@link UriMatchTemplate} of a route of another template is not available:
     * {@link #getUriMatchTemplate()} throws an {@link UnsupportedOperationException} for it.</p>
     *
     * @return The template
     * @since 5.3.0
     */
    default RouteTemplate getRouteTemplate() {
        return UriRouteTemplate.of(getUriMatchTemplate());
    }

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI
     * @return An {@link Optional} of {@link RouteMatch}
     */
    @Override
    default Optional<UriRouteMatch<T, R>> match(URI uri) {
        return match(uri.toString());
    }

    /**
     * Match this route within the given URI and produce a {@link RouteMatch} if a match is found.
     *
     * @param uri The URI
     * @return A null or a {@link RouteMatch}
     */
    @Nullable
    default UriRouteMatch<T, R> tryMatch(URI uri) {
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
    UriRouteMatch<T, R> tryMatch(String uri);

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

    /**
     * Whether this route is an <em>implicit</em> {@code HEAD} route; that is, a {@code HEAD} route that
     * was derived automatically from a {@link io.micronaut.http.annotation.Get} mapping rather than
     * declared by the user with {@link io.micronaut.http.annotation.Head}.
     *
     * <p>Implicit routes are fully-fledged routes and participate in matching and content negotiation
     * exactly like any other route. The distinction is only used as a last-resort tie-break: if a request
     * cannot be resolved to a single route and the remaining candidates contain both implicit and
     * explicitly declared routes, the explicitly declared ones win instead of the request being rejected
     * as ambiguous.</p>
     *
     * @return {@code true} if this route was derived from a {@code @Get} mapping rather than declared
     * @since 5.2.0
     */
    default boolean isImplicitHead() {
        return false;
    }

    /**
     * The order of the route among the routes that are equally good for a request, e.g. two
     * routes of the same URI template whose conditions a request both meets: the one with the
     * lowest order answers it. The order only breaks such a tie: it is consulted after the
     * produced and consumed media types, the specificity of the URI templates, the preference of a
     * route of the request's method over a route of any method and of an explicit {@code HEAD}
     * route over an implicit one, and two routes with the same order still make the
     * request ambiguous. A controller route has the order {@code 0}.
     *
     * @return The order of the route, {@code 0} by default
     * @see io.micronaut.web.router.builder.HttpRouteSpec#order(int)
     * @since 5.3.0
     */
    @Experimental
    default int getOrder() {
        return 0;
    }

    /**
     * The declarative conditions of the route, of its groups and its own, see
     * {@link io.micronaut.web.router.builder.RouteSpec#where(io.micronaut.web.router.builder.RequestCondition)}:
     * normalized, the parts a request must all meet in the order the router evaluates them, e.g.
     * for the routes endpoint. A condition of a lambda is a
     * {@link io.micronaut.web.router.builder.RequestCondition.Custom} part.
     *
     * @return The conditions, empty if the route has none
     * @since 5.3.0
     */
    @Internal
    @Experimental
    default List<io.micronaut.web.router.builder.RequestCondition> getConditions() {
        return List.of();
    }

    /**
     * The conditions on the body among the {@link #getConditions() conditions} of the route, see
     * {@link io.micronaut.web.router.builder.RequestCondition.Body}. The router matches a request
     * without its body: the routes whose body conditions decide the match are left pending, see
     * {@link Router#findClosest(io.micronaut.http.HttpRequest, java.util.function.Consumer)}, and
     * the server chooses among them once it read the body.
     *
     * @return The body conditions, empty if the route has none
     * @since 5.3.0
     */
    @Internal
    @Experimental
    default List<io.micronaut.web.router.builder.RequestCondition.Body<?>> getBodyConditions() {
        return List.of();
    }
}
