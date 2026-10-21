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
import io.micronaut.web.router.builder.RequestCondition;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The match of a request that depends on its body: the routes the request matched without its
 * body, some with {@link UriRouteInfo#getBodyConditions() body conditions}, ranked as the router
 * ranks them, see {@link Router#resolve(HttpRequest)}. The server
 * reads the body, evaluates the body conditions rank by rank, and {@link #choose chooses} the
 * route among the routes of the first rank that the body matches.
 *
 * <p>The ranks, and so where a request goes when the body conditions of its best routes fail,
 * are decided by a {@link BodyFallThrough}, see {@link BodyFallThrough#ACROSS_SPECIFICITY} for the
 * default.</p>
 *
 * <p>The chosen route may be a route locator, see {@link RouteLocator}: its target is located
 * then, and when the routes of the target depend on the body too, the choice is another pending
 * match, see {@link #choose(HttpRequest, List)}.</p>
 *
 * @param <T> The target type
 * @param <R> The result type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class PendingRouteMatch<T, R> {

    private final List<List<UriRouteMatch<T, R>>> ranks;
    private final Chooser<T, R> chooser;

    /**
     * @param path     The path of the request
     * @param ranks    The ranks of the matches, the first with a route with body conditions
     * @param resolver Resolves the match of a route with a dynamic target, or {@code null}
     */
    PendingRouteMatch(String path, List<List<UriRouteMatch<T, R>>> ranks, @Nullable Resolver<T, R> resolver) {
        // the ranks are built by a BodyFallThrough, the only place that decides where a request
        // whose body conditions fail goes
        this(ranks, (request, matched) -> {
            UriRouteMatch<T, R> match = UriRouteSet.closest(path, matched);
            if (match == null || resolver == null) {
                return RouteResolution.of(match);
            }
            return resolver.resolve(request, match);
        });
    }

    private PendingRouteMatch(List<List<UriRouteMatch<T, R>>> ranks, Chooser<T, R> chooser) {
        this.ranks = ranks;
        this.chooser = chooser;
    }

    /**
     * The pending match of the routes of a located target, see {@link RouteLocator}: the same
     * ranks, whose chosen match is chosen with the request of the target and wrapped as a match
     * of the request.
     *
     * @param targetRequest The request the routes of the target were matched with
     * @param wrap          Wraps the chosen match of the target
     * @param failed        Records a failure to choose among the routes of the target as the
     *                      outcome of its location, so that the error routes of the location answer it
     * @return The pending match of the request
     */
    PendingRouteMatch<T, R> located(HttpRequest<?> targetRequest, UnaryOperator<UriRouteMatch<T, R>> wrap,
                                    UnaryOperator<RuntimeException> failed) {
        return new PendingRouteMatch<>(ranks, (request, matched) -> {
            RouteResolution<T, R> resolution;
            try {
                resolution = choose(targetRequest, matched);
            } catch (RuntimeException e) {
                throw failed.apply(e);
            }
            return switch (resolution) {
                case RouteResolution.Matched<T, R> m -> new RouteResolution.Matched<>(wrap.apply(m.match()));
                case RouteResolution.Pending<T, R> p -> new RouteResolution.Pending<>(p.pending().located(targetRequest, wrap, failed));
                case RouteResolution.NotFound<T, R> n -> n;
            };
        });
    }

    /**
     * @return The ranks of the matches, best first: the routes of a rank are equally good for the
     * request without its body
     */
    public List<List<UriRouteMatch<T, R>>> getRanks() {
        return ranks;
    }

    /**
     * The number of bytes of the body the conditions of the routes read: the largest limit of
     * the body conditions of the routes, see {@link RequestCondition.Body#maxBytes(int)}.
     *
     * @param serverLimit The limit of the server, {@code micronaut.server.max-request-buffer-size}
     * @return The limit
     */
    public int getMaxBytes(int serverLimit) {
        int max = 0;
        for (List<UriRouteMatch<T, R>> rank : ranks) {
            for (UriRouteMatch<T, R> match : rank) {
                for (RequestCondition.Body<?> condition : match.getRouteInfo().getBodyConditions()) {
                    max = Math.max(max, condition.effectiveMaxBytes(serverLimit));
                }
            }
        }
        return max;
    }

    /**
     * Choose the route among the matches of a rank that the body matches. A chosen route
     * locator locates its target, see {@link RouteLocator}: when the routes of the target depend
     * on the body too, the resolution is {@link RouteResolution.Pending pending} again. An
     * asynchronous locator that has not located its target yet fails like it does for the
     * router, see {@link RouteLocator#pendingLocation(Throwable)}.
     *
     * @param request The request
     * @param matched The matches of a rank whose routes the body matches
     * @return The resolution: the chosen match, none, or the matches of a located target that
     * depend on the body
     * @throws DuplicateRouteException if several routes match
     */
    public RouteResolution<T, R> choose(HttpRequest<?> request, List<UriRouteMatch<T, R>> matched) throws DuplicateRouteException {
        return chooser.choose(request, matched);
    }

    /**
     * @param matches Matches
     * @return Whether a route of the matches has body conditions
     */
    static boolean anyHasBodyConditions(List<? extends UriRouteMatch<?, ?>> matches) {
        for (UriRouteMatch<?, ?> match : matches) {
            if (!match.getRouteInfo().getBodyConditions().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Chooses the match among the matches of a rank the body matches.
     *
     * @param <T> The target type
     * @param <R> The result type
     */
    @FunctionalInterface
    private interface Chooser<T, R> {
        RouteResolution<T, R> choose(HttpRequest<?> request, List<UriRouteMatch<T, R>> matched);
    }

    /**
     * Resolves the match of a route with a dynamic target, see {@link DynamicRouteTarget}.
     *
     * @param <T> The target type
     * @param <R> The result type
     */
    @FunctionalInterface
    interface Resolver<T, R> {
        /**
         * @param request The request
         * @param match   The chosen match
         * @return The resolution of the match
         */
        RouteResolution<T, R> resolve(HttpRequest<?> request, UriRouteMatch<T, R> match);
    }
}
