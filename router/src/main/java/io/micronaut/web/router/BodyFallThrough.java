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
import io.micronaut.http.HttpRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a request goes when the body conditions of its best routes fail, see
 * {@link io.micronaut.web.router.builder.RequestCondition.Body}:
 * it ranks the routes a request matched without its body into the {@link PendingRouteMatch#getRanks()
 * ranks} the server tries in order. The server takes the first rank with a route the body
 * matches, and two routes of that rank the body both matches make the request ambiguous.
 *
 * <p>This is the only place that decides the fall-through, so that another strategy, e.g. one that
 * stays within the most specific URI template, can replace the default,
 * {@link #ACROSS_SPECIFICITY}, without changing the router or the server.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
sealed interface BodyFallThrough permits BodyFallThrough.AcrossSpecificity {

    /**
     * The routes of every specificity: a request whose body the routes of a URI template do not
     * match falls through to the routes of the next, less specific, URI template, like to the
     * routes of a higher order.
     */
    BodyFallThrough ACROSS_SPECIFICITY = new AcrossSpecificity();

    /**
     * The strategy of the router.
     */
    BodyFallThrough DEFAULT = ACROSS_SPECIFICITY;

    /**
     * Rank the matches of a request.
     *
     * @param request The request
     * @param matches The matches of the request without its body, sorted by the specificity of
     *                their URI templates, at least one
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The ranks, best first, each the routes that are equally good for the request
     * without its body, at least one
     */
    <T, R> List<List<UriRouteMatch<T, R>>> rank(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches);

    /**
     * See {@link #ACROSS_SPECIFICITY}. A rank holds the closest routes of the remaining ones, as
     * the router selects the closest route without body conditions: by the specificity of the URI
     * template and by the media types, then the preference of a specific method over any method
     * and of an explicit {@code HEAD} route over an implicit one, then the lowest order. The ranks
     * stop at the first rank with a route without body conditions, which always matches.
     */
    final class AcrossSpecificity implements BodyFallThrough {

        private AcrossSpecificity() {
        }

        @Override
        public <T, R> List<List<UriRouteMatch<T, R>>> rank(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
            if (matches.size() == 1) {
                // one route is its own rank: nothing to rank, and the caller's list is not kept
                return List.of(List.of(matches.get(0)));
            }
            List<UriRouteMatch<T, R>> remaining = new ArrayList<>(matches);
            List<List<UriRouteMatch<T, R>>> ranks = new ArrayList<>(2);
            while (!remaining.isEmpty()) {
                List<UriRouteMatch<T, R>> rank = remaining.size() > 1 ? DefaultRouter.resolveAmbiguity(request, remaining) : remaining;
                if (rank.size() > 1) {
                    rank = AnyMethodRoutes.preferSpecificMethod(rank);
                }
                if (rank.size() > 1) {
                    rank = ImplicitHeadRoutes.preferExplicit(rank);
                }
                if (rank.size() > 1) {
                    rank = RouteOrders.preferLowest(rank);
                }
                rank = List.copyOf(rank);
                ranks.add(rank);
                if (!allHaveBodyConditions(rank)) {
                    // a route without body conditions always matches: the ranks after it are never tried
                    break;
                }
                List<UriRouteMatch<T, R>> ranked = rank;
                remaining.removeIf(match -> containsSame(ranked, match));
            }
            return ranks;
        }

        private static boolean allHaveBodyConditions(List<? extends UriRouteMatch<?, ?>> matches) {
            for (UriRouteMatch<?, ?> match : matches) {
                if (match.getRouteInfo().getBodyConditions().isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        private static boolean containsSame(List<?> list, Object element) {
            for (Object o : list) {
                if (o == element) {
                    return true;
                }
            }
            return false;
        }
    }
}
