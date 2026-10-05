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
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private final boolean noRoutes;

    private UriRouteSet(Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod,
                        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod,
                        boolean hasDynamicTargets) {
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
        UriRouteMatch<T, R> match = findClosestRoute(request, ports);
        if (hasDynamicTargets && match != null) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target != null) {
                return target.findClosest(request, match);
            }
        }
        return match;
    }

    private @Nullable <T, R> UriRouteMatch<T, R> findClosestRoute(HttpRequest<?> request, @Nullable Set<Integer> ports) throws DuplicateRouteException {
        String path = request.getPath();
        List<UriRouteMatch<T, R>> uriRoutes = findMatches(request, path, ports);
        int size = uriRoutes.size();
        if (size == 0) {
            return null;
        }
        if (size == 1) {
            Object obj = uriRoutes.get(0);
            // type pollution avoidance (should be covered by type pollution test)
            return obj instanceof DefaultUriRouteMatch<?, ?> def ? (DefaultUriRouteMatch<T, R>) def : (UriRouteMatch<T, R>) obj;
        }
        uriRoutes = DefaultRouter.resolveAmbiguity(request, uriRoutes);
        return closest(path, uriRoutes);
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
     * @param filter  The filter of the candidates, or {@code null}
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request,
                                                                  @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                                  @Nullable Set<Integer> ports) {
        List<UriRouteMatch<T, R>> uriRoutes = filter(findMatches(request, request.getPath(), ports), filter);
        if (uriRoutes.size() < 2) {
            return uriRoutes;
        }
        return DefaultRouter.resolveAmbiguity(request, uriRoutes);
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
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        String path = request.getPath();
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
            if (skipForAny(entry.getKey(), request)) {
                continue;
            }
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            int[] candidates = candidates(entry.getKey(), path);
            int count = candidates == null ? routes.length : candidates.length;
            for (int i = 0; i < count; i++) {
                UriRouteInfo<Object, Object> route = routes[candidates == null ? i : candidates[i]];
                if (!isOwnRoute(entry.getKey(), route)) {
                    continue;
                }
                if (shouldSkipForPort(request, route, ports)) {
                    continue;
                }
                if (!route.matching(request)) {
                    continue;
                }
                UriRouteMatch match = route.tryMatch(path);
                if (match != null && acceptsVariables(request, route, match)) {
                    matchedRoutes.add(match);
                }
            }
        }
        return hasDynamicTargets ? resolveAny(request, matchedRoutes) : matchedRoutes;
    }

    /**
     * Replace the matches of routes with a dynamic target with the matches the most specific of
     * them resolves, e.g. to find the allowed methods.
     */
    private <T, R> List<UriRouteMatch<T, R>> resolveAny(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        UriRouteMatch<T, R> dynamicMatch = null;
        DynamicRouteTarget dynamicTarget = null;
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            DynamicRouteTarget target = DynamicRouteTarget.of(match.getRouteInfo());
            if (target == null) {
                result.add(match);
            } else if (dynamicMatch == null || match.getRouteInfo().compareTo((UriRouteInfo) dynamicMatch.getRouteInfo()) < 0) {
                dynamicMatch = match;
                dynamicTarget = target;
            }
        }
        if (dynamicTarget == null || dynamicMatch == null) {
            return matches;
        }
        result.addAll(dynamicTarget.findAny(request, dynamicMatch));
        return result;
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
            if (permitsBody && contentType == null && !route.consumesAll()) {
                contentType = request.getContentType().orElse(null);
            }
            if (acceptedProducedTypes == null && !route.producesAll()) {
                acceptedProducedTypes = request.accept();
            }
            UriRouteMatch match = matchRoute(request, route, uri, ports, permitsBody, contentType, acceptedProducedTypes);
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
     * Applies the port, path variable constraint, body, Content-Type, Accept and request matcher
     * checks and then matches the URI.
     *
     * @return The match, or {@code null} if the route does not apply to the request
     */
    private static @Nullable UriRouteMatch<Object, Object> matchRoute(HttpRequest<?> request,
                                                                      UriRouteInfo<Object, Object> route,
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
            if (constrainedMatch == null || !info.acceptsVariables(constrainedValues(request, constrainedMatch.getVariableValues()))) {
                return null;
            }
        }
        if (permitsBody) {
            if (!route.isPermitsRequestBody()) {
                return null;
            }
            if (!route.consumesAll() && !route.doesConsume(contentType)) {
                return null;
            }
        }
        if (!route.producesAll() && !route.doesProduce(acceptedProducedTypes)) {
            return null;
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
    private static boolean acceptsVariables(UriRouteInfo<?, ?> route, UriMatchInfo match) {
        return !(route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained())
            || info.acceptsVariables(match.getVariableValues());
    }

    /**
     * @param request The request
     * @param route   The route
     * @param match   A match of the route
     * @return Whether the constraints of the route, if any, accept the variables of the match
     */
    private static boolean acceptsVariables(HttpRequest<?> request, UriRouteInfo<?, ?> route, UriMatchInfo match) {
        return !(route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained())
            || info.acceptsVariables(constrainedValues(request, match.getVariableValues()));
    }

    /**
     * The variables the constraints of a route see: those its handler gets, with the variables of
     * the prefixes of the locator routes for a route of a located target.
     *
     * @param request The request, a {@link RouteLocator.LocatedRequest} for a route of a located target
     * @param values  The raw values of the variables of the match
     * @return The values
     */
    private static Map<String, Object> constrainedValues(HttpRequest<?> request, Map<String, Object> values) {
        return request instanceof RouteLocator.LocatedRequest<?> located ? located.location().withPrefixValues(values) : values;
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
}
