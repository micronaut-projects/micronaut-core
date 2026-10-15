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
import io.micronaut.web.router.builder.RouteCondition;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The match of a request that depends on its body: the routes the request matched without its
 * body, some with {@link UriRouteInfo#getBodyConditions() body conditions}, ranked as the router
 * ranks them, see {@link Router#findClosest(HttpRequest, java.util.function.Consumer)}. The server
 * reads the body, evaluates the body conditions rank by rank, and {@link #choose chooses} the
 * route among the routes of the first rank that the body matches.
 *
 * <p>The ranks, and so where a request goes when the body conditions of its best routes fail,
 * are decided by a {@link BodyFallThrough}, see {@link BodyFallThrough#ACROSS_SPECIFICITY} for the
 * default.</p>
 *
 * <p>The chosen route may be a route locator, see {@link RouteLocator}: its target is located
 * then, and when the routes of the target depend on the body too, the choice is another pending
 * match, given to the caller of {@link #choose(HttpRequest, List, Consumer)}.</p>
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
        this(ranks, (request, matched, next) -> {
            UriRouteMatch<T, R> match = UriRouteSet.closest(path, matched);
            if (match == null || resolver == null) {
                return match;
            }
            return resolver.resolve(request, match, next);
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
     * @return The pending match of the request
     */
    PendingRouteMatch<T, R> located(HttpRequest<?> targetRequest, UnaryOperator<UriRouteMatch<T, R>> wrap) {
        return new PendingRouteMatch<>(ranks, (request, matched, next) -> {
            UriRouteMatch<T, R> match = choose(targetRequest, matched,
                nested -> next.accept(nested.located(targetRequest, wrap)));
            return match == null ? null : wrap.apply(match);
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
     * the body conditions of the routes, see {@link RouteCondition.Body#maxBytes(int)}.
     *
     * @param serverLimit The limit of the server, {@code micronaut.server.max-request-buffer-size}
     * @return The limit
     */
    public int getMaxBytes(int serverLimit) {
        int max = 0;
        for (List<UriRouteMatch<T, R>> rank : ranks) {
            for (UriRouteMatch<T, R> match : rank) {
                for (RouteCondition.Body<?> condition : match.getRouteInfo().getBodyConditions()) {
                    max = Math.max(max, condition.effectiveMaxBytes(serverLimit));
                }
            }
        }
        return max;
    }

    /**
     * Choose the route among the matches of a rank that the body matches. A chosen route
     * locator locates its target, see {@link RouteLocator}: when the routes of the target depend
     * on the body too, they are given to {@code next}, and no match is returned. An asynchronous
     * locator that has not located its target yet fails like it does for the router, see
     * {@link RouteLocator#pendingLocation(Throwable)}.
     *
     * @param request The request
     * @param matched The matches of a rank whose routes the body matches
     * @param next    Given the matches of a located target that depend on the body
     * @return The match, or {@code null} if there is none or it depends on the body again
     * @throws DuplicateRouteException if several routes match
     */
    public @Nullable UriRouteMatch<T, R> choose(HttpRequest<?> request,
                                               List<UriRouteMatch<T, R>> matched,
                                               Consumer<? super PendingRouteMatch<T, R>> next) throws DuplicateRouteException {
        return chooser.choose(request, matched, next);
    }

    /**
     * Choose the route among the matches of a rank that the body matches, see
     * {@link #choose(HttpRequest, List, Consumer)}, for routes whose resolution does not depend
     * on the body again.
     *
     * @param request The request
     * @param matched The matches of a rank whose routes the body matches
     * @return The match, or {@code null} if there is none
     * @throws DuplicateRouteException if several routes match
     * @throws IllegalStateException if the chosen route depends on the body again
     */
    public @Nullable UriRouteMatch<T, R> choose(HttpRequest<?> request, List<UriRouteMatch<T, R>> matched) throws DuplicateRouteException {
        return choose(request, matched, nested -> {
            throw new IllegalStateException("The chosen route of " + request + " depends on the body again");
        });
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
        @Nullable UriRouteMatch<T, R> choose(HttpRequest<?> request,
                                             List<UriRouteMatch<T, R>> matched,
                                             Consumer<? super PendingRouteMatch<T, R>> next);
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
         * @param next    Given the matches of the target that depend on the body
         * @return The match to use, or {@code null} if none or it depends on the body
         */
        @Nullable UriRouteMatch<T, R> resolve(HttpRequest<?> request, UriRouteMatch<T, R> match,
                                              Consumer<? super PendingRouteMatch<T, R>> next);
    }
}
