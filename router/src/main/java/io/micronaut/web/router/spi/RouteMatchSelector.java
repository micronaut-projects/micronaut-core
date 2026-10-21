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
package io.micronaut.web.router.spi;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.PathVariables;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.web.router.UriRouteInfo;
import io.micronaut.web.router.UriRouteMatch;
import io.micronaut.web.router.builder.DefaultPathVariables;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selects among the routes that match a request, instead of the Micronaut route selection policy,
 * and negotiates the media type of the response. A route gets a selector with
 * {@code RouteSpec#selectedBy(RouteMatchSelector)}, or from its groups; the routes of a template
 * language usually share one selector, e.g. one per JAX-RS application.
 *
 * <p>The router calls it when every route that matches the path of a request, and accepts its
 * method, port, conditions, content type ({@link io.micronaut.web.router.RouteInfo#doesConsume})
 * and accepted types ({@link io.micronaut.web.router.RouteInfo#doesProduce}), has the same
 * selector, the same instance, even when there is only one such route, instead of the Micronaut
 * resolution of ambiguous routes by the media types and the specificity of the templates. For the
 * routes of a selector the types are compatible when one matches the other, without the
 * parameters: {@code Accept: image/*} reaches the routes that produce {@code image/png} and
 * {@code image/*}; when none is compatible the router answers {@code 406} or {@code 415} as for
 * other routes. When the candidates have different selectors, or none, the Micronaut policy
 * selects, and no selector is called. For example a JAX-RS selector selects by the specificity of
 * JAX-RS (section 3.7.2), then by the content type of the request against {@code @Consumes} and
 * the accepted types, with their quality, against {@code @Produces}, with their {@code qs}.</p>
 *
 * <p>A locator route is never given to {@link #select}: its target is located after the
 * selection. When locator routes are among the candidates and they all have the same selector,
 * the router keeps the most specific of them by the {@link #order() order} of the selector, or by
 * the Micronaut policy. The selection is therefore not staged as in JAX-RS: a resource method
 * that the media types exclude does not keep a request from a locator route of the same path.</p>
 *
 * <p>When the router finds the routes of the path of every HTTP method
 * ({@link io.micronaut.web.router.Router#findAny(HttpRequest)}), e.g. for the {@code 405} status
 * and its {@code Allow} header, or a CORS preflight request, it calls the selector for the matches
 * of each method, given as above for a request of that method, and a route of the selector counts
 * only when it is selected. A route the selector does not select for the method of the request is
 * not found, and a method none of whose routes is selected is not allowed.</p>
 *
 * <p>The matches are normal route matches: filters, argument binding and the annotations of the
 * route apply to the selected match as to any other. The negotiated media type of a selection
 * reaches the handler as {@link UriRouteMatch#getSelectedMediaType()} and
 * {@link #selectedMediaType(io.micronaut.http.PathVariables)}, and is the content
 * type of the response when the handler does not set one.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface RouteMatchSelector {

    /**
     * Select among the matches of a request.
     *
     * @param request The request
     * @param matches The matches of the path, of the routes of this selector, in the order of the route
     *                table: at least one
     * @return The selected matches, each one of the given matches: one to route the request, none
     * for no route ({@code 404}, or the status the router answers for a path no route matches), and
     * more than one when the selector does not decide: the router then prefers, as for other
     * routes, a route of the method of the request over a route of any method, an explicit route
     * over an implicit {@code HEAD} route, and the lowest {@code order}, and the request is
     * ambiguous if they are equal
     */
    List<Selection> select(HttpRequest<?> request, List<UriRouteMatch<?, ?>> matches);

    /**
     * The order of specificity of the routes of this selector, where {@link #select} cannot run:
     * when locator routes are among the candidates, see the class documentation. The template a
     * locator route was declared with is {@link #locatorPrefix(UriRouteInfo)}.
     * {@code compare(a, b) < 0} means {@code a} is more specific than {@code b}; routes that
     * compare equal are equally specific, and a request both match is ambiguous.
     *
     * @return The order, or empty for the Micronaut policy
     */
    default Optional<Comparator<UriRouteInfo<?, ?>>> order() {
        return Optional.empty();
    }

    /**
     * The media type of the response that the route selector of the route
     * negotiated, for the path variables given to a handler, see
     * {@link UriRouteMatch#getSelectedMediaType()}.
     *
     * @param pathVariables The path variables a handler received
     * @return The media type, or {@code null} if none was negotiated
     * @since 5.3.0
     */
    static @Nullable MediaType selectedMediaType(PathVariables pathVariables) {
        return pathVariables instanceof DefaultPathVariables routeVariables ? routeVariables.selectedMediaType() : null;
    }

    /**
     * The template a locator route was declared with, e.g. for the {@link #order() order} of a
     * selector, which compares route infos: the prefix of a locator route whose prefix is not a
     * URI template, nested or mounted.
     *
     * @param route A route
     * @return The prefix, or {@code null} if the route is not such a locator route
     */
    static @Nullable RouteTemplate locatorPrefix(UriRouteInfo<?, ?> route) {
        return RouteLocator.locatorPrefix(route);
    }

    /**
     * A selected match, created with {@link #of(UriRouteMatch)} or
     * {@link #of(UriRouteMatch, MediaType)}: the interface is sealed, so that a selection can say
     * more later.
     */
    @Experimental
    sealed interface Selection permits DefaultSelection {

        /**
         * @param match A match, without a negotiated media type
         * @return The selection
         */
        static Selection of(UriRouteMatch<?, ?> match) {
            return of(match, null);
        }

        /**
         * @param match             A match
         * @param responseMediaType The negotiated media type of the response, or {@code null} to
         *                          let the server choose as for other routes
         * @return The selection
         */
        static Selection of(UriRouteMatch<?, ?> match, @Nullable MediaType responseMediaType) {
            return new DefaultSelection(match, responseMediaType);
        }

        /**
         * @return One of the matches given to the selector
         */
        UriRouteMatch<?, ?> match();

        /**
         * @return The negotiated media type of the response, or {@code null} to let the server
         * choose as for other routes
         */
        @Nullable MediaType responseMediaType();
    }
}
