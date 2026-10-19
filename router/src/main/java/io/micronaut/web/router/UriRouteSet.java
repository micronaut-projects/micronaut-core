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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpRequestWrapper;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * An immutable set of URI routes, sorted by specificity for each HTTP method, including the custom
 * ones, and the matching of a request against them: its port, method, consumed and produced media
 * types and conditions, and the ambiguity between the routes that match. The application routes
 * of a {@link DefaultRouter} are one set. A route with a {@link DynamicRouteTarget} is replaced
 * by the matches its target resolves.
 * <p>The default ports are not part of the set: the router that matches it gives them, see
 * {@link Router#applyDefaultPorts(List)}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class UriRouteSet {

    /**
     * A set without routes.
     */
    static final UriRouteSet NONE = UriRouteSet.of(List.of());

    private static final UriRouteInfo<Object, Object>[] EMPTY = new UriRouteInfo[0];

    /**
     * The most routes of a method that are matched by trying each of them, without an index: a
     * lookup in the index walks the request path through a trie, which costs more than it saves
     * when the method has few routes, see {@link RouteIndex}.
     */
    private static final int DIRECT_SCAN_MAX_ROUTES = 16;

    private final Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodRoutesByMethod;
    private final Map<String, UriRouteInfo<Object, Object>[]> allRoutesByMethod;
    /**
     * The index of the routes of each method with more than {@link #DIRECT_SCAN_MAX_ROUTES}
     * routes, by method name, see {@link #allRoutesByMethod}.
     */
    private final Map<String, RouteIndex> indexesByMethod;
    /**
     * Whether a route has a dynamic target, see {@link DynamicRouteTarget}.
     */
    private final boolean hasDynamicTargets;
    /**
     * Whether a route has constraints on its path variables.
     */
    private final boolean constrained;
    /**
     * Whether a route has conditions on the body, see {@link UriRouteInfo#getBodyConditions()}.
     */
    private final boolean bodyConditioned;
    /**
     * Whether a route has a route selector with its own order of specificity, see
     * {@link RouteMatchSelector#order()}.
     */
    private final boolean hasSelectorOrders;
    /**
     * Whether a route has a route selector, which selects among its matches, see
     * {@link RouteMatchSelector}.
     */
    private final boolean hasSelectors;
    private final boolean noRoutes;

    private UriRouteSet(Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod,
                        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod,
                        boolean hasDynamicTargets) {
        boolean selectorOrders = false;
        boolean selectors = false;
        for (List<UriRouteInfo<Object, Object>> routes : routesByMethod.values()) {
            selectorOrders |= hasSelectorOrder(routes);
            selectors |= hasSelector(routes);
        }
        for (List<UriRouteInfo<Object, Object>> routes : customRoutesByMethod.values()) {
            selectorOrders |= hasSelectorOrder(routes);
            selectors |= hasSelector(routes);
        }
        this.hasSelectorOrders = selectorOrders;
        this.hasSelectors = selectors;
        Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodMap = CollectionUtils.newEnumMap(HttpMethod.values());
        Map<String, UriRouteInfo<Object, Object>[]> customMethodMap = CollectionUtils.newHashMap(routesByMethod.size() + customRoutesByMethod.size());
        for (Map.Entry<HttpMethod, List<UriRouteInfo<Object, Object>>> e : routesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] values = finalizeRoutes(e.getValue());
            methodMap.put(e.getKey(), values);
            customMethodMap.put(e.getKey().name(), values);
        }
        // the routes of any method match every custom method: of a custom method with routes of its own too
        List<UriRouteInfo<Object, Object>> anyCustomMethod = customRoutesByMethod.getOrDefault(AnyMethodRoutes.CUSTOM_METHODS, List.of());
        for (Map.Entry<String, List<UriRouteInfo<Object, Object>>> e : customRoutesByMethod.entrySet()) {
            List<UriRouteInfo<Object, Object>> routes = e.getValue();
            if (!anyCustomMethod.isEmpty() && !AnyMethodRoutes.CUSTOM_METHODS.equals(e.getKey())) {
                routes = new ArrayList<>(routes);
                routes.addAll(anyCustomMethod);
            }
            customMethodMap.put(e.getKey(), finalizeRoutes(routes));
        }
        this.methodRoutesByMethod = methodMap;
        this.allRoutesByMethod = customMethodMap;
        Map<String, RouteIndex> indexes = CollectionUtils.newHashMap(customMethodMap.size());
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> e : customMethodMap.entrySet()) {
            if (e.getValue().length > DIRECT_SCAN_MAX_ROUTES) {
                indexes.put(e.getKey(), indexRoutes(e.getValue()));
            }
        }
        this.indexesByMethod = indexes;
        this.hasDynamicTargets = hasDynamicTargets;
        this.constrained = customMethodMap.values().stream().flatMap(Arrays::stream)
            .anyMatch(route -> route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained());
        this.bodyConditioned = customMethodMap.values().stream().flatMap(Arrays::stream)
            .anyMatch(route -> !route.getBodyConditions().isEmpty());
        this.noRoutes = customMethodMap.isEmpty();
    }

    /**
     * The set of the given routes.
     *
     * @param routes The routes, in the order they were declared: routes that are equally specific
     *               keep it
     * @return The set
     */
    static UriRouteSet of(Collection<? extends UriRoute> routes) {
        Builder builder = new Builder();
        for (UriRoute route : routes) {
            builder.add(route);
        }
        return builder.build();
    }

    /**
     * @return Whether the set has no routes
     */
    boolean isEmpty() {
        return noRoutes;
    }

    /**
     * @return The routes of the set
     */
    Stream<UriRouteInfo<?, ?>> uriRoutes() {
        // the routes of any custom method are merged into the routes of each custom method: list them once, under their own key
        return allRoutesByMethod.entrySet().stream()
            .flatMap(e -> Arrays.stream(e.getValue()).filter(route -> isOwnRoute(e.getKey(), route)));
    }

    /**
     * @param methodKey The method key of the routes the route is listed under
     * @param route     The route
     * @return Whether the route is a route of that method, rather than a route of any custom
     * method merged into the routes of the method
     */
    private static boolean isOwnRoute(String methodKey, UriRouteInfo<Object, Object> route) {
        return methodKey.equals(route.getHttpMethodName());
    }

    /**
     * @param methodKey The method key of a set of routes
     * @param request   The request, or {@code null}
     * @return Whether {@link #findAny} skips the routes of that key: the routes of any custom
     * method, except for a request of a custom method, since the route of any method has a
     * route of each standard method too
     */
    private static boolean skipForAny(String methodKey, @Nullable HttpRequest<?> request) {
        return AnyMethodRoutes.CUSTOM_METHODS.equals(methodKey)
            && (request == null || request.getMethod() != HttpMethod.CUSTOM);
    }

    /**
     * The matches of the routes that accept the request, see {@link Router#find(HttpRequest, CharSequence)}.
     *
     * @param request The request
     * @param uri     The URI to match
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> find(HttpRequest<?> request, String uri, @Nullable Set<Integer> ports) {
        return findMatches(request, uri, ports);
    }

    /**
     * The matches of the routes of a method, see {@link Router#find(HttpMethod, CharSequence, HttpRequest)}.
     *
     * @param httpMethod The method
     * @param uri        The URI to match
     * @param <T>        The target type
     * @param <R>        The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> find(HttpMethod httpMethod, String uri) {
        List<UriRouteMatch<T, R>> matches = toMatches(uri, allRoutesByMethod.getOrDefault(httpMethod.name(), EMPTY));
        if (!constrained || matches.isEmpty()) {
            return matches;
        }
        List<UriRouteMatch<T, R>> accepted = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            if (acceptsVariables(match.getRouteInfo(), match)) {
                accepted.add(match);
            }
        }
        return accepted;
    }

    /**
     * The closest match of the request, see {@link Router#findClosest(HttpRequest)}.
     *
     * @param request The request
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The match, or {@code null}
     * @throws DuplicateRouteException if several routes match equally closely
     */
    @Nullable <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request, @Nullable Set<Integer> ports) throws DuplicateRouteException {
        String path = request.getPath();
        return resolveDynamic(request, closestRoute(path, request, findMatches(request, path, ports)));
    }

    /**
     * @return Whether a route of the set has conditions on the body, or may resolve to routes
     * with them: a route with a dynamic target, e.g. of a route locator, whose routes are known
     * when it is resolved
     */
    boolean hasBodyConditions() {
        return bodyConditioned || hasDynamicTargets;
    }

    /**
     * The closest match of the request, or the matches still pending on its body, see
     * {@link Router#resolve(HttpRequest, Predicate)}.
     *
     * @param request The request
     * @param ports   The default ports, or {@code null}
     * @param filter  The filter of the candidates, applied before the ambiguity is resolved, or
     *                {@code null}: a route with a dynamic target is no candidate itself, the
     *                filter applies to the matches its target resolves
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The resolution: the closest match, none, or the matches that depend on the body
     * @throws DuplicateRouteException if several routes match equally closely
     */
    <T, R> RouteResolution<T, R> resolve(HttpRequest<?> request,
                                         @Nullable Set<Integer> ports,
                                         @Nullable Predicate<UriRouteMatch<T, R>> filter) throws DuplicateRouteException {
        if (filter == null && !bodyConditioned && !hasDynamicTargets) {
            return RouteResolution.of(findClosest(request, ports));
        }
        String path = request.getPath();
        List<UriRouteMatch<T, R>> matches = filter(findMatches(request, path, ports), filter);
        if (!PendingRouteMatch.anyHasBodyConditions(matches)) {
            if (filter != null && hasDynamicTargets) {
                // as findAllClosest: the targets resolve their routes, which the filter sees, before
                // the ambiguity is resolved, e.g. a route of each version under the same prefix
                List<UriRouteMatch<T, R>> resolved = findAllClosest(request, filter, ports);
                if (!PendingRouteMatch.anyHasBodyConditions(resolved)) {
                    return RouteResolution.of(closest(path, resolved));
                }
                // the body decides: all the candidates, with the less specific routes it falls through to
                return pendingOrClosest(path, request, findAllCandidates(request, filter, ports), null);
            }
            return resolveDynamic(request, closestRoute(path, request, matches), filter);
        }
        List<List<UriRouteMatch<T, R>>> ranks = BodyFallThrough.DEFAULT.rank(request, matches);
        if (!PendingRouteMatch.anyHasBodyConditions(ranks.get(0))) {
            // the body does not decide: as without body conditions
            return resolveDynamic(request, closest(path, ranks.get(0)), filter);
        }
        return new RouteResolution.Pending<>(new PendingRouteMatch<>(path, ranks,
            hasDynamicTargets ? (chosenRequest, chosen) -> resolveDynamic(chosenRequest, chosen, filter) : null));
    }

    /**
     * All the matches of the request among the candidates a filter accepts, with the matches the
     * targets of the routes with a dynamic target resolve instead of them, see
     * {@link DynamicRouteTarget#findAll}, without resolving their ambiguity: the candidates of a
     * choice by the body.
     *
     * @param request The request
     * @param filter  The filter of the candidates, or {@code null}
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAllCandidates(HttpRequest<?> request,
                                                       @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                       @Nullable Set<Integer> ports) {
        List<UriRouteMatch<T, R>> matches = filter(findMatches(request, request.getPath(), ports), filter);
        return hasDynamicTargets ? resolveAll(request, matches, filter) : matches;
    }

    /**
     * @return The matches, with the matches their targets resolve instead of the matches of
     * routes with a dynamic target
     */
    static <T, R> List<UriRouteMatch<T, R>> resolveAll(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches,
                                                       @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target == null) {
                result.add(match);
            } else {
                result.addAll(target.findAll(request, match, filter));
            }
        }
        return result;
    }

    /**
     * The closest of resolved matches, or their matches pending on the body.
     */
    static <T, R> RouteResolution<T, R> pendingOrClosest(String path, HttpRequest<?> request,
                                                         List<UriRouteMatch<T, R>> resolved,
                                                         PendingRouteMatch.@Nullable Resolver<T, R> resolver) throws DuplicateRouteException {
        List<List<UriRouteMatch<T, R>>> ranks = BodyFallThrough.DEFAULT.rank(request, resolved);
        if (!PendingRouteMatch.anyHasBodyConditions(ranks.get(0))) {
            return RouteResolution.of(closest(path, ranks.get(0)));
        }
        return new RouteResolution.Pending<>(new PendingRouteMatch<>(path, ranks, resolver));
    }

    /**
     * Resolve the match of a route with a dynamic target, whose routes may depend on the body.
     */
    private <T, R> RouteResolution<T, R> resolveDynamic(HttpRequest<?> request, @Nullable UriRouteMatch<T, R> match,
                                                        @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (hasDynamicTargets && match != null) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target != null) {
                return target.resolve(request, match, filter);
            }
        }
        return RouteResolution.of(match);
    }

    private @Nullable <T, R> UriRouteMatch<T, R> resolveDynamic(HttpRequest<?> request, @Nullable UriRouteMatch<T, R> match) {
        if (hasDynamicTargets && match != null) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target != null) {
                return target.findClosest(request, match);
            }
        }
        return match;
    }

    private @Nullable <T, R> UriRouteMatch<T, R> closestRoute(String path, HttpRequest<?> request, List<UriRouteMatch<T, R>> uriRoutes) throws DuplicateRouteException {
        int size = uriRoutes.size();
        if (size == 0) {
            return null;
        }
        // the route selector of every match, found once
        RouteMatchSelector common = hasSelectors(request) ? commonSelector(request, uriRoutes) : null;
        if (common != null && hasNoDynamicTarget(uriRoutes)) {
            // the route selector of the matches selects among them, instead of the Micronaut policy
            uriRoutes = select(common, request, request, uriRoutes);
            return uriRoutes.size() == 1 ? uriRoutes.get(0) : closest(path, uriRoutes);
        }
        if (size == 1) {
            Object obj = uriRoutes.get(0);
            // type pollution avoidance (should be covered by type pollution test)
            return obj instanceof DefaultUriRouteMatch<?, ?> def ? (DefaultUriRouteMatch<T, R>) def : (UriRouteMatch<T, R>) obj;
        }
        uriRoutes = DefaultRouter.resolveAmbiguity(request, uriRoutes, common != null && hasSelectorOrders(request));
        if (common != null && hasNoDynamicTarget(uriRoutes) && !uriRoutes.isEmpty()) {
            // the routes of the selector left once its locator routes are less specific: it selects among them
            uriRoutes = select(common, request, request, uriRoutes);
            return uriRoutes.size() == 1 ? uriRoutes.get(0) : closest(path, uriRoutes);
        }
        return closest(path, uriRoutes);
    }

    /**
     * The closest of the given matches of a request, as when the request is routed: the matches
     * the route selector they all have selects, otherwise the closest ones, see
     * {@link DefaultRouter#resolveAmbiguity(HttpRequest, List)}.
     *
     * @param request The request
     * @param matches The matches
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    static <T, R> List<UriRouteMatch<T, R>> selectClosest(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        if (matches.isEmpty()) {
            return matches;
        }
        RouteMatchSelector common = commonSelector(request, matches);
        if (common != null && hasNoDynamicTarget(matches)) {
            return select(common, request, request, matches);
        }
        List<UriRouteMatch<T, R>> closest = matches.size() > 1 ? DefaultRouter.resolveAmbiguity(request, matches) : matches;
        if (common != null && hasNoDynamicTarget(closest) && !closest.isEmpty()) {
            return select(common, request, request, closest);
        }
        return closest;
    }

    /**
     * The closest of equally close matches: a route of a specific method over a route of any
     * method, an explicit route over an implicit {@code HEAD} route, then the route of the lowest
     * order.
     *
     * @param path    The path
     * @param matches The closest matches
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The match, or {@code null} if there is none
     * @throws DuplicateRouteException if several routes match equally closely
     */
    static @Nullable <T, R> UriRouteMatch<T, R> closest(String path, List<UriRouteMatch<T, R>> matches) throws DuplicateRouteException {
        if (matches.size() > 1) {
            matches = AnyMethodRoutes.preferSpecificMethod(matches);
        }
        if (matches.size() > 1) {
            matches = ImplicitHeadRoutes.preferExplicit(matches);
        }
        if (matches.size() > 1) {
            matches = RouteOrders.preferLowest(matches);
        }
        if (matches.size() > 1) {
            throw new DuplicateRouteException(path, (List) matches);
        } else if (matches.size() == 1) {
            return matches.get(0);
        }
        return null;
    }

    /**
     * The closest matches of the request, see {@link Router#findAllClosest(HttpRequest)}.
     *
     * @param request The request
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        return findAllClosest(request, null, ports);
    }

    /**
     * The closest matches of the request among the candidates a filter accepts, see
     * {@link Router#findAllClosest(HttpRequest, Predicate)}. A route with a dynamic target is no
     * candidate itself: the filter applies to the matches its target resolves.
     *
     * @param request The request
     * @param filter  The filter of the candidates, applied before the ambiguity is resolved, or {@code null}
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request,
                                                    @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                    @Nullable Set<Integer> ports) {
        List<UriRouteMatch<T, R>> matches = findAllClosestRoutes(request, filter, ports);
        if (!hasDynamicTargets || matches.isEmpty()) {
            return matches;
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target == null) {
                result.add(match);
            } else {
                result.addAll(target.findAllClosest(request, match, filter));
            }
        }
        return result;
    }

    /**
     * The closest matches of a request.
     *
     * @param request The request
     * @param filter  The filter of the candidates, or {@code null}, applied before the ambiguity
     *                is resolved and before a route selector selects among them
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request,
                                                                  @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                                  @Nullable Set<Integer> ports) {
        List<UriRouteMatch<T, R>> uriRoutes = filter(findMatches(request, request.getPath(), ports), filter);
        // the route selector of every match, found once
        RouteMatchSelector common = hasSelectors(request) && !uriRoutes.isEmpty() ? commonSelector(request, uriRoutes) : null;
        if (common != null && hasNoDynamicTarget(uriRoutes)) {
            return select(common, request, request, uriRoutes);
        }
        if (uriRoutes.size() < 2) {
            return uriRoutes;
        }
        uriRoutes = DefaultRouter.resolveAmbiguity(request, uriRoutes, common != null && hasSelectorOrders(request));
        if (common != null && hasNoDynamicTarget(uriRoutes) && !uriRoutes.isEmpty()) {
            // the routes of the selector left once its locator routes are less specific: it selects among them
            return select(common, request, request, uriRoutes);
        }
        return uriRoutes;
    }

    private <T, R> List<UriRouteMatch<T, R>> filter(List<UriRouteMatch<T, R>> matches, @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (filter == null || matches.isEmpty()) {
            return matches;
        }
        var filtered = new ArrayList<UriRouteMatch<T, R>>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            if (accepts(filter, match)) {
                filtered.add(match);
            }
        }
        return filtered;
    }

    /**
     * Whether the filter accepts a candidate. A route with a dynamic target is no candidate: the
     * filter applies to the matches its target resolves.
     */
    private <T, R> boolean accepts(Predicate<UriRouteMatch<T, R>> filter, UriRouteMatch<T, R> match) {
        return hasDynamicTargets && DynamicRouteTarget.of(match.getRouteInfo()) != null || filter.test(match);
    }

    private <T, R> List<UriRouteMatch<T, R>> toMatches(String path, UriRouteInfo<Object, Object>[] routes) {
        if (routes.length == 1) {
            UriRouteMatch match = routes[0].tryMatch(path);
            if (match != null) {
                return List.of(match);
            }
            return List.of();
        }
        var uriRoutes = new ArrayList<UriRouteMatch<T, R>>(routes.length);
        for (UriRouteInfo<Object, Object> route : routes) {
            UriRouteMatch match = route.tryMatch(path);
            if (match != null) {
                uriRoutes.add(match);
            }
        }
        return uriRoutes;
    }

    /**
     * The first route of a method that matches the URI, see {@link Router#route(HttpMethod, CharSequence)}.
     *
     * @param httpMethod The method
     * @param uri        The URI
     * @param <T>        The target type
     * @param <R>        The result type
     * @return The match
     */
    <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, String uri) {
        for (UriRouteInfo<Object, Object> uriRouteInfo : methodRoutesByMethod.getOrDefault(httpMethod, EMPTY)) {
            Optional<UriRouteMatch<Object, Object>> match = uriRouteInfo.match(uri);
            if (match.isPresent() && acceptsVariables(uriRouteInfo, match.get())) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    /**
     * The matches of the routes of any method, see {@link Router#findAny(CharSequence, HttpRequest)}.
     *
     * @param uri     The URI
     * @param request The request whose port and conditions the routes must accept, or {@code null}
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    @SuppressWarnings("unchecked")
    <T, R> List<UriRouteMatch<T, R>> findAny(String uri, @Nullable HttpRequest<?> request, @Nullable Set<Integer> ports) {
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
            if (skipForAny(entry.getKey(), request)) {
                continue;
            }
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            int[] candidates = candidates(entry.getKey(), uri);
            int count = candidates == null ? routes.length : candidates.length;
            for (int i = 0; i < count; i++) {
                UriRouteInfo<Object, Object> route = routes[candidates == null ? i : candidates[i]];
                if (!isOwnRoute(entry.getKey(), route)) {
                    continue;
                }
                if (request != null) {
                    if (shouldSkipForPort(request, route, ports)) {
                        continue;
                    }
                    if (!route.matching(request)) {
                        continue;
                    }
                }
                UriRouteMatch match = route.tryMatch(uri);
                if (match != null && acceptsVariables(route, match)) {
                    matchedRoutes.add(match);
                }
            }
        }
        return matchedRoutes;
    }

    /**
     * The matches of the routes of any method that accept the port and the conditions of the
     * request, see {@link Router#findAny(HttpRequest)}.
     *
     * @param request The request
     * @param filter  The filter of the candidates, applied before a route selector selects, or
     *                {@code null}. A route with a dynamic target is no candidate itself: the
     *                filter applies to the matches its target resolves
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request, @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                             @Nullable Set<Integer> ports) {
        String path = request.getPath();
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        boolean selecting = hasSelectors(request);
        // the matches of the routes of any method among the routes of a custom method: a route
        // selector of that method is given them too, as when a request of the method is routed
        Map<String, List<UriRouteMatch<T, R>>> anyMethodMatches = null;
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
            if (skipForAny(entry.getKey(), request)) {
                continue;
            }
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            int[] candidates = candidates(entry.getKey(), path);
            int count = candidates == null ? routes.length : candidates.length;
            for (int i = 0; i < count; i++) {
                UriRouteInfo<Object, Object> route = routes[candidates == null ? i : candidates[i]];
                boolean own = isOwnRoute(entry.getKey(), route);
                if (!own && !selecting) {
                    continue;
                }
                if (shouldSkipForPort(request, route, ports)) {
                    continue;
                }
                if (!route.matching(request)) {
                    continue;
                }
                UriRouteMatch match = route.tryMatch(path);
                if (match != null && acceptsVariables(request, route, match) && (filter == null || accepts(filter, match))) {
                    if (own) {
                        matchedRoutes.add(match);
                    } else {
                        if (anyMethodMatches == null) {
                            anyMethodMatches = new HashMap<>(4);
                        }
                        anyMethodMatches.computeIfAbsent(entry.getKey(), method -> new ArrayList<>(2)).add(match);
                    }
                }
            }
        }
        List<UriRouteMatch<T, R>> selected = selecting && !matchedRoutes.isEmpty() ? selectAny(request, matchedRoutes, anyMethodMatches) : matchedRoutes;
        return hasDynamicTargets ? resolveAny(request, selected, filter) : selected;
    }

    /**
     * Remove the matches that their route selector does not select, for each HTTP
     * method: the allowed methods of a path, and whether a method is allowed, count only the routes
     * the selector would route a request of that method to. As when the router finds the routes of a
     * method, the selector is given the matches of the method whose content type and accepted
     * types are compatible with the request, when they all have that selector; otherwise the
     * Micronaut policy selects and every match is kept. The matches whose types are not compatible
     * are kept: they are the unsupported or not acceptable types of the path.
     *
     * @param request          The request
     * @param matches          The matches of the path, of every method
     * @param anyMethodMatches The matches of the routes of any method, by the custom method whose routes they are among, or {@code null}
     * @return The matches, without the ones a route selector did not select
     */
    private <T, R> List<UriRouteMatch<T, R>> selectAny(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches,
                                                       @Nullable Map<String, List<UriRouteMatch<T, R>>> anyMethodMatches) {
        if (!anySelector(request, matches)) {
            // nothing to select: no grouping
            return matches;
        }
        Map<String, List<UriRouteMatch<T, R>>> byMethod = new LinkedHashMap<>();
        MediaType contentType = request.getContentType().orElse(null);
        Collection<MediaType> accepted = request.accept();
        for (UriRouteMatch<T, R> match : matches) {
            // as when a request is routed, the located routes of a locator route decide which media types they accept
            if (DynamicRouteTarget.of(match.getRouteInfo()) != null || isCompatible(atLocation(request, match.getRouteInfo()), contentType, accepted)) {
                byMethod.computeIfAbsent(match.getRouteInfo().getHttpMethodName(), method -> new ArrayList<>()).add(match);
            }
        }
        List<UriRouteMatch<?, ?>> rejected = null;
        for (List<UriRouteMatch<T, R>> methodCandidates : byMethod.values()) {
            List<UriRouteMatch<T, R>> candidates = withAnyMethodMatches(methodCandidates, anyMethodMatches, contentType, accepted);
            RouteMatchSelector selector = commonSelector(request, candidates);
            if (selector == null) {
                continue;
            }
            // a request of the method of the candidates, e.g. GET for a CORS preflight request
            String methodName = candidates.get(0).getRouteInfo().getHttpMethodName();
            HttpRequest<?> selectorRequest = methodName.equals(request.getMethodName()) || AnyMethodRoutes.CUSTOM_METHODS.equals(methodName)
                ? request : new MethodRequest<>(request, methodName);
            List<UriRouteMatch<T, R>> selected = candidates;
            if (!hasNoDynamicTarget(selected) && selected.size() > 1) {
                // as when a request of the method is routed: with locator routes, the order of the
                // selector or the Micronaut policy narrows them first
                selected = DefaultRouter.resolveAmbiguity(request, selected, true);
            }
            if (hasNoDynamicTarget(selected) && !selected.isEmpty()) {
                selected = select(selector, request, selectorRequest, selected);
            }
            for (UriRouteMatch<T, R> candidate : methodCandidates) {
                if (!isSelected(selected, candidate)) {
                    if (rejected == null) {
                        rejected = new ArrayList<>();
                    }
                    rejected.add(candidate);
                }
            }
        }
        if (rejected == null) {
            return matches;
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            if (!containsIdentical(rejected, match)) {
                result.add(match);
            }
        }
        return result;
    }

    /**
     * @param candidates       The compatible matches of the routes of a method
     * @param anyMethodMatches The matches of the routes of any method, by the custom method whose routes they are among, or {@code null}
     * @param contentType      The content type of the request
     * @param accepted         The accepted types of the request
     * @return The candidates, with the compatible matches of the routes of any method if the method is custom
     */
    private static <T, R> List<UriRouteMatch<T, R>> withAnyMethodMatches(List<UriRouteMatch<T, R>> candidates,
                                                                         @Nullable Map<String, List<UriRouteMatch<T, R>>> anyMethodMatches,
                                                                         @Nullable MediaType contentType, Collection<MediaType> accepted) {
        List<UriRouteMatch<T, R>> anyMethod = anyMethodMatches == null ? null : anyMethodMatches.get(candidates.get(0).getRouteInfo().getHttpMethodName());
        if (anyMethod == null) {
            return candidates;
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(candidates);
        for (UriRouteMatch<T, R> match : anyMethod) {
            if (DynamicRouteTarget.of(match.getRouteInfo()) != null || isCompatible(match.getRouteInfo(), contentType, accepted)) {
                result.add(match);
            }
        }
        return result;
    }

    /**
     * The body and media type checks of {@link #matchRoute}, for a request of the method of the
     * route.
     */
    private static boolean isCompatible(UriRouteInfo<?, ?> route, @Nullable MediaType contentType, Collection<MediaType> accepted) {
        if (route.getHttpMethod().permitsRequestBody()
            && (!route.isPermitsRequestBody() || (!route.consumesAll() && !route.doesConsume(contentType)))) {
            return false;
        }
        // a route that produces all types still rejects a request that accepts no type, see AcceptedMediaTypes
        return route.producesAll() && !AcceptedMediaTypes.hasRejected(accepted) || route.doesProduce(accepted);
    }

    /**
     * @param selected The selected matches, possibly copies with a negotiated media type
     * @param match    A candidate
     * @return Whether the candidate was selected
     */
    private static boolean isSelected(List<? extends UriRouteMatch<?, ?>> selected, UriRouteMatch<?, ?> match) {
        for (UriRouteMatch<?, ?> candidate : selected) {
            if (candidate == match || candidate.getRouteInfo() == match.getRouteInfo()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Replace the matches of routes with a dynamic target with the matches they resolve, e.g. to
     * find the allowed methods: for each HTTP method, the matches of that method the most specific
     * of its dynamic routes resolves, as when a request of the method is routed.
     */
    private <T, R> List<UriRouteMatch<T, R>> resolveAny(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches,
                                                        @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        Map<String, List<UriRouteMatch<T, R>>> byMethod = null;
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            if (DynamicRouteTarget.of(match.getRouteInfo()) == null) {
                result.add(match);
            } else if (byMethod == null) {
                byMethod = new LinkedHashMap<>();
            }
        }
        if (byMethod == null) {
            return matches;
        }
        if (!hasSelectors(request)) {
            // the Micronaut policy: the locator routes of every method have the same templates, so
            // the most specific of them is the same for every method
            UriRouteMatch<T, R> dynamicMatch = mostSpecificDynamic(request, matches);
            result.addAll(Objects.requireNonNull(DynamicRouteTarget.of(Objects.requireNonNull(dynamicMatch).getRouteInfo()))
                .findAny(request, dynamicMatch, filter));
            return result;
        }
        for (UriRouteMatch<T, R> match : matches) {
            byMethod.computeIfAbsent(match.getRouteInfo().getHttpMethodName(), method -> new ArrayList<>(4)).add(match);
        }
        // the most specific dynamic route of each method, and the methods it is the most specific of:
        // the routes of a locator, one per method, have the same target
        Map<DynamicRouteTarget, UriRouteMatch<T, R>> dynamicMatches = new IdentityHashMap<>(4);
        Map<DynamicRouteTarget, Set<String>> dynamicMethods = new IdentityHashMap<>(4);
        // the target whose routes of custom methods are kept: the one of any custom method, else the first
        DynamicRouteTarget customTarget = null;
        for (Map.Entry<String, List<UriRouteMatch<T, R>>> entry : byMethod.entrySet()) {
            UriRouteMatch<T, R> dynamicMatch = mostSpecificDynamic(request, entry.getValue());
            if (dynamicMatch != null) {
                DynamicRouteTarget target = Objects.requireNonNull(DynamicRouteTarget.of(dynamicMatch.getRouteInfo()));
                dynamicMatches.putIfAbsent(target, dynamicMatch);
                dynamicMethods.computeIfAbsent(target, t -> new HashSet<>(4)).add(entry.getKey());
                if (customTarget == null || AnyMethodRoutes.CUSTOM_METHODS.equals(entry.getKey())) {
                    customTarget = target;
                }
            }
        }
        for (Map.Entry<DynamicRouteTarget, UriRouteMatch<T, R>> entry : dynamicMatches.entrySet()) {
            Set<String> methods = Objects.requireNonNull(dynamicMethods.get(entry.getKey()));
            boolean custom = entry.getKey() == customTarget;
            for (UriRouteMatch<T, R> resolved : entry.getKey().findAny(request, entry.getValue(), filter)) {
                UriRouteInfo<T, R> route = resolved.getRouteInfo();
                if (methods.contains(route.getHttpMethodName()) || custom && route.getHttpMethod() == HttpMethod.CUSTOM) {
                    result.add(resolved);
                }
            }
        }
        return result;
    }

    /**
     * @param request The request
     * @param matches The matches of one HTTP method
     * @return The most specific of the matches with a dynamic target, or {@code null} if none:
     * by the order of the route selector of every match, otherwise by the order of the table
     */
    private static <T, R> @Nullable UriRouteMatch<T, R> mostSpecificDynamic(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        RouteMatchSelector common = commonSelector(request, matches);
        Comparator<UriRouteInfo<?, ?>> order = common == null ? null : DefaultRouter.selectorOrder(request, matches.get(0).getRouteInfo());
        UriRouteMatch<T, R> dynamicMatch = null;
        UriRouteInfo<?, ?> dynamicRoute = null;
        for (UriRouteMatch<T, R> match : matches) {
            if (DynamicRouteTarget.of(match.getRouteInfo()) != null) {
                // compared with the settings it inherits at the location of the request
                UriRouteInfo<?, ?> route = RouteLocator.atLocationOf(request, match.getRouteInfo());
                if (dynamicRoute == null || isMoreSpecific(order, route, dynamicRoute)) {
                    dynamicMatch = match;
                    dynamicRoute = route;
                }
            }
        }
        return dynamicMatch;
    }

    /**
     * Finds the routes whose filters accept the request and whose URI matches the given path, in
     * route order, in a single pass. Returns a shared immutable list when there is at most one
     * match and only allocates a mutable list once a second match is found.
     *
     * @param request The request
     * @param uri     The URI to match
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findMatches(HttpRequest<?> request, String uri, @Nullable Set<Integer> ports) {
        HttpMethod httpMethod = request.getMethod();
        String methodKey = httpMethod == HttpMethod.CUSTOM ? request.getMethodName() : httpMethod.name();
        UriRouteInfo<Object, Object>[] routes = allRoutesByMethod.get(methodKey);
        if (routes == null && httpMethod == HttpMethod.CUSTOM) {
            // a custom method without routes of its own: the routes of any method
            methodKey = AnyMethodRoutes.CUSTOM_METHODS;
            routes = allRoutesByMethod.get(methodKey);
        }
        if (routes == null || routes.length == 0) {
            return Collections.emptyList();
        }
        // few routes are tried one by one, more are narrowed by the index: neither allocates
        int[] candidates = candidates(methodKey, uri);
        int count = candidates == null ? routes.length : candidates.length;
        boolean permitsBody = httpMethod.permitsRequestBody();
        MediaType contentType = null;
        Collection<MediaType> acceptedProducedTypes = null;
        // most requests match a single route: keep it in a local and only allocate a list once a second match shows up
        UriRouteMatch<T, R> first = null;
        List<UriRouteMatch<T, R>> matches = null;
        for (int i = 0; i < count; i++) {
            UriRouteInfo<Object, Object> route = routes[candidates == null ? i : candidates[i]];
            UriRouteInfo<Object, Object> mediaTypes = mediaTypes(request, route);
            if (mediaTypes != null) {
                if (permitsBody && contentType == null && !mediaTypes.consumesAll()) {
                    contentType = request.getContentType().orElse(null);
                }
                if (acceptedProducedTypes == null) {
                    acceptedProducedTypes = request.accept();
                }
            }
            UriRouteMatch match = matchRoute(request, route, mediaTypes, uri, ports, permitsBody, contentType, acceptedProducedTypes);
            if (match == null) {
                continue;
            }
            if (first == null) {
                first = match;
            } else {
                if (matches == null) {
                    matches = new ArrayList<>(4);
                    matches.add(first);
                }
                matches.add(match);
            }
        }
        if (matches != null) {
            return matches;
        }
        return first == null ? Collections.emptyList() : Collections.singletonList(first);
    }

    /**
     * The route whose media types a request must have and accept to match a route.
     *
     * @param request The request
     * @param route   The route
     * @return The route itself; for a request a locator route located, the route with the
     * annotations of the location, see {@link RouteLocator#atLocation}; or {@code null} for a
     * locator route, whose located routes decide which media types they consume and produce,
     * not the annotations of the groups of the locator route, which they inherit
     */
    private static @Nullable UriRouteInfo<Object, Object> mediaTypes(HttpRequest<?> request, UriRouteInfo<Object, Object> route) {
        if (route instanceof DefaultUrlRouteInfo<Object, Object> info && info.hasDynamicTarget()) {
            return null;
        }
        return RouteLocator.atLocation(request, route);
    }

    /**
     * Applies the port, path variable constraint, body, Content-Type, Accept and request matcher
     * checks and then matches the URI.
     *
     * @return The match, or {@code null} if the route does not apply to the request
     */
    private static @Nullable UriRouteMatch<Object, Object> matchRoute(HttpRequest<?> request,
                                                                      UriRouteInfo<Object, Object> route,
                                                                      @Nullable UriRouteInfo<Object, Object> mediaTypes,
                                                                      String uri,
                                                                      @Nullable Set<Integer> ports,
                                                                      boolean permitsBody,
                                                                      @Nullable MediaType contentType,
                                                                      @Nullable Collection<MediaType> acceptedProducedTypes) {
        if (shouldSkipForPort(request, route, ports)) {
            return null;
        }
        UriRouteMatch<Object, Object> constrainedMatch = null;
        if (route instanceof DefaultUrlRouteInfo<Object, Object> info && info.isConstrained()) {
            constrainedMatch = info.tryMatch(uri);
            if (constrainedMatch == null || !info.acceptsVariables(constrainedValues(request, constrainedMatch),
                rawConstrainedValues(request, constrainedMatch))) {
                return null;
            }
        }
        if (permitsBody && !route.isPermitsRequestBody()) {
            return null;
        }
        if (mediaTypes != null) {
            if (permitsBody && !mediaTypes.consumesAll() && !mediaTypes.doesConsume(contentType)) {
                return null;
            }
            if (!(mediaTypes.producesAll() && !AcceptedMediaTypes.hasRejected(acceptedProducedTypes)) && !mediaTypes.doesProduce(acceptedProducedTypes)) {
                return null;
            }
        }
        if (!route.matching(request)) {
            return null;
        }
        return constrainedMatch != null ? constrainedMatch : route.tryMatch(uri);
    }

    /**
     * @param route The route
     * @param match A match of the route
     * @return Whether the constraints of the route, if any, accept the variables of the match
     */
    private static boolean acceptsVariables(UriRouteInfo<?, ?> route, UriRouteMatch<?, ?> match) {
        return !(route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained())
            || info.acceptsVariables(match.getVariableValues(), match.getRawVariableValues());
    }

    /**
     * @param request The request
     * @param route   The route
     * @param match   A match of the route
     * @return Whether the constraints of the route, if any, accept the variables of the match
     */
    private static boolean acceptsVariables(HttpRequest<?> request, UriRouteInfo<?, ?> route, UriRouteMatch<?, ?> match) {
        return !(route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained())
            || info.acceptsVariables(constrainedValues(request, match),
                rawConstrainedValues(request, match));
    }

    /**
     * The variables the constraints of a route see: those its handler gets, with the variables of
     * the decoded prefixes of the locator routes for a route of a located target.
     *
     * @param request The request, a {@link RouteLocator.LocatedRequest} for a route of a located target
     * @param match   The match
     * @return The values
     */
    private static Map<String, Object> constrainedValues(HttpRequest<?> request, UriRouteMatch<?, ?> match) {
        return request instanceof RouteLocator.LocatedRequest<?> located
            ? located.location().withDecodedPrefixValues(match.getVariableValues(), match.getVariables())
            : match.getVariableValues();
    }

    /**
     * The raw variables the constraints of a route see, see {@link #constrainedValues(HttpRequest, UriRouteMatch)}:
     * those its handler gets. The captures of the match, also of its absent optional variables,
     * override those of the prefixes of the locator routes, before the absent ones are left out, as
     * for the match the handler gets.
     *
     * @param request The request, a {@link RouteLocator.LocatedRequest} for a route of a located target
     * @param match   The match
     * @return The raw values, with those of the prefixes of the locator routes
     */
    private static Map<String, Object> rawConstrainedValues(HttpRequest<?> request, UriRouteMatch<?, ?> match) {
        if (!(request instanceof RouteLocator.LocatedRequest<?> located)) {
            return match.getRawVariableValues();
        }
        Map<String, Object> captures = match instanceof DefaultUriRouteMatch<?, ?> defaultMatch
            ? defaultMatch.matchInfo().getVariableValues()
            : match.getRawVariableValues();
        Map<String, Object> values = located.location().withPrefixValues(captures, match.getVariables());
        values.values().removeIf(Objects::isNull);
        return values;
    }

    private static boolean shouldSkipForPort(HttpRequest<?> request, UriRouteInfo<Object, Object> route, @Nullable Set<Integer> ports) {
        if (ports == null || route.getPort() != null) {
            return false;
        }
        return !ports.contains(request.getServerAddress().getPort());
    }

    /**
     * @param methodKey The method key of the routes
     * @param path      The request path
     * @return The positions of the routes of the method that can match the path, shared and
     * read-only, or {@code null} to try every route: the method has too few routes for an index
     */
    private int @Nullable [] candidates(String methodKey, String path) {
        RouteIndex index = indexesByMethod.get(methodKey);
        return index == null ? null : index.candidates(path);
    }

    private static RouteIndex indexRoutes(UriRouteInfo<Object, Object>[] routes) {
        String[] prefixes = new String[routes.length];
        for (int i = 0; i < routes.length; i++) {
            prefixes[i] = routes[i] instanceof IndexedRoute route ? route.getRequiredPathPrefix() : "";
        }
        return RouteIndex.build(prefixes);
    }

    /**
     * Let a route selector select among the matches of its routes.
     *
     * @param selector The route selector
     * @param request  The request
     * @param matches  The matches, of the routes of the selector
     * @return The selected matches, with their negotiated media types
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, R> List<UriRouteMatch<T, R>> select(RouteMatchSelector selector, HttpRequest<?> request, HttpRequest<?> selectorRequest,
                                                           List<UriRouteMatch<T, R>> matches) {
        // for a request a locator route located, the routes with the settings of the location
        List<UriRouteMatch<T, R>> given = atLocation(request, matches);
        List<RouteMatchSelector.Selection> selections = selector.select(selectorRequest, Collections.unmodifiableList((List) given));
        if (selections.isEmpty()) {
            return List.of();
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(selections.size());
        for (RouteMatchSelector.Selection selection : selections) {
            int index = indexOfIdentical(given, selection.match());
            if (index < 0) {
                throw new IllegalStateException("The route selector " + selector + " selected a match that it was not given: " + selection.match());
            }
            UriRouteMatch<T, R> selected = matches.get(index);
            MediaType mediaType = selection.responseMediaType();
            if (mediaType != null && selected instanceof DefaultUriRouteMatch<T, R> defaultMatch) {
                selected = defaultMatch.withSelectedMediaType(mediaType);
            }
            result.add(selected);
        }
        return result;
    }

    /**
     * The matches a route selector selects among: for a request a locator route located, the
     * matches of the routes with the settings they inherit at the location, see
     * {@link RouteLocator#atLocation}, in the same order.
     *
     * @param request The request
     * @param matches The matches of the routes of the table
     * @return The matches the selector is given
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, R> List<UriRouteMatch<T, R>> atLocation(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        List<UriRouteMatch<T, R>> result = null;
        for (int i = 0; i < matches.size(); i++) {
            UriRouteMatch<T, R> match = matches.get(i);
            if (match instanceof DefaultUriRouteMatch<T, R> defaultMatch && match.getRouteInfo() instanceof DefaultUrlRouteInfo<T, R> route) {
                UriRouteInfo<Object, Object> located = RouteLocator.atLocation(request, (UriRouteInfo) route);
                if (located != route && located instanceof DefaultUrlRouteInfo<?, ?> locatedRoute) {
                    if (result == null) {
                        result = new ArrayList<>(matches);
                    }
                    result.set(i, ((DefaultUrlRouteInfo<T, R>) locatedRoute).resolvedMatch(defaultMatch.matchInfo()));
                }
            }
        }
        return result == null ? matches : result;
    }

    /**
     * @param request The request
     * @param route   A route of the table
     * @return The route at the location of the request, see {@link RouteLocator#atLocation}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static UriRouteInfo<?, ?> atLocation(HttpRequest<?> request, UriRouteInfo<?, ?> route) {
        return RouteLocator.atLocation(request, (UriRouteInfo) route);
    }

    /**
     * @param order   The order of the route selector of every match, or {@code null}
     * @param route   A route with a dynamic target
     * @param current The most specific of them so far
     * @return Whether the route is more specific: by the order of the selector, otherwise by the
     * order of the table
     */
    private static boolean isMoreSpecific(@Nullable Comparator<UriRouteInfo<?, ?>> order, UriRouteInfo<?, ?> route, UriRouteInfo<?, ?> current) {
        return order != null ? order.compare(route, current) < 0 : compareRoutes(route, current) < 0;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareRoutes(UriRouteInfo<?, ?> route, UriRouteInfo<?, ?> current) {
        return ((UriRouteInfo) route).compareTo((UriRouteInfo) current);
    }

    private static int indexOfIdentical(List<? extends UriRouteMatch<?, ?>> matches, UriRouteMatch<?, ?> match) {
        for (int i = 0; i < matches.size(); i++) {
            if (matches.get(i) == match) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsIdentical(List<? extends UriRouteMatch<?, ?>> matches, UriRouteMatch<?, ?> match) {
        for (UriRouteMatch<?, ?> candidate : matches) {
            if (candidate == match) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param request The request
     * @param matches The matches of a path
     * @return The route selector of all the matches at the location of the request, the same
     * instance, including the routes with a dynamic target, or {@code null} if they have
     * different selectors, or none
     */
    private static @Nullable RouteMatchSelector commonSelector(HttpRequest<?> request, List<? extends UriRouteMatch<?, ?>> matches) {
        RouteMatchSelector selector = null;
        for (UriRouteMatch<?, ?> match : matches) {
            RouteMatchSelector own = DefaultRouter.selector(request, match.getRouteInfo());
            if (own == null || selector != null && own != selector) {
                return null;
            }
            selector = own;
        }
        return selector;
    }

    /**
     * @param matches Matches
     * @return Whether no match is of a route with a dynamic target, e.g. a locator route
     */
    private static boolean hasNoDynamicTarget(List<? extends UriRouteMatch<?, ?>> matches) {
        for (UriRouteMatch<?, ?> match : matches) {
            if (DynamicRouteTarget.of(match.getRouteInfo()) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param request The request
     * @return Whether a route may have a route selector: a route of the table, or a route a
     * locator route located, which inherits the selector of the location
     */
    private boolean hasSelectors(HttpRequest<?> request) {
        return hasSelectors || RouteLocator.locatedSelector(request) != null;
    }

    /**
     * @param request The request
     * @return Whether a route may have a route selector with its own order
     */
    private boolean hasSelectorOrders(HttpRequest<?> request) {
        return hasSelectorOrders || RouteLocator.locatedSelectorOrder(request) != null;
    }

    /**
     * @param request The request
     * @param matches Matches
     * @return Whether a match has a route selector at the location of the request
     */
    private static boolean anySelector(HttpRequest<?> request, List<? extends UriRouteMatch<?, ?>> matches) {
        for (UriRouteMatch<?, ?> match : matches) {
            if (DefaultRouter.selector(request, match.getRouteInfo()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param routes The routes of a method
     * @return Whether a route has a route selector
     */
    private static boolean hasSelector(List<UriRouteInfo<Object, Object>> routes) {
        for (UriRouteInfo<Object, Object> route : routes) {
            if (DefaultRouter.selector(route) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param routes The routes of a method
     * @return Whether a route has a route selector with its own order
     */
    private static boolean hasSelectorOrder(List<UriRouteInfo<Object, Object>> routes) {
        for (UriRouteInfo<Object, Object> route : routes) {
            if (DefaultRouter.selectorOrder(route) != null) {
                return true;
            }
        }
        return false;
    }

    private static UriRouteInfo<Object, Object>[] finalizeRoutes(List<UriRouteInfo<Object, Object>> routes) {
        Collections.sort(routes);
        return routes.toArray(EMPTY);
    }

    /**
     * Collects the routes of a set, in the order they are declared: routes that are equally
     * specific keep it.
     */
    static final class Builder {
        private final Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod = new HashMap<>();
        private final Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod = CollectionUtils.newEnumMap(HttpMethod.values());
        private boolean hasDynamicTargets;

        /**
         * Add a route.
         *
         * @param route The route
         */
        void add(UriRoute route) {
            HttpMethod httpMethod = route.getHttpMethod();
            UriRouteInfo<Object, Object> uriRouteInfo = route.toRouteInfo();
            add(httpMethod, route.getHttpMethodName(), uriRouteInfo);
        }

        private void add(HttpMethod httpMethod, String httpMethodName, UriRouteInfo<Object, Object> uriRouteInfo) {
            hasDynamicTargets = hasDynamicTargets || DynamicRouteTarget.of(uriRouteInfo) != null;
            if (httpMethod == HttpMethod.CUSTOM) {
                customRoutesByMethod.computeIfAbsent(httpMethodName, x -> new ArrayList<>()).add(uriRouteInfo);
            } else {
                routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
            }
        }

        /**
         * @return The set of the added routes
         */
        UriRouteSet build() {
            return new UriRouteSet(routesByMethod, customRoutesByMethod, hasDynamicTargets);
        }
    }

    /**
     * A request of another HTTP method, which a route selector is given for the routes of that
     * method, see {@link RouteMatchSelector}.
     *
     * @param <B> The body type
     */
    private static final class MethodRequest<B> extends HttpRequestWrapper<B> {
        private final HttpMethod method;
        private final String methodName;

        MethodRequest(HttpRequest<B> request, String methodName) {
            super(request);
            this.method = HttpMethod.parse(methodName);
            this.methodName = methodName;
        }

        @Override
        public HttpMethod getMethod() {
            return method;
        }

        @Override
        public String getMethodName() {
            return methodName;
        }
    }
}
