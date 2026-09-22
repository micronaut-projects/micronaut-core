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
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
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

    private final Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodRoutesByMethod;
    private final Map<String, UriRouteInfo<Object, Object>[]> allRoutesByMethod;
    /**
     * The index of the routes of each method, by method name, see {@link #allRoutesByMethod}.
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
     * Whether a route has a template of an engine that declares its own order of specificity, see
     * {@link io.micronaut.http.uri.spi.RouteTemplateEngine#comparator()}.
     */
    private final boolean hasEngineOrders;
    /**
     * Whether a route has a template of an engine that selects among its matches, see
     * {@link RouteMatchSelector}.
     */
    private final boolean hasEngineSelectors;
    private final boolean empty;

    private UriRouteSet(Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod,
                        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod,
                        boolean hasDynamicTargets) {
        boolean hasEngineOrders = false;
        if (RouteTemplateEngines.defaults().hasComparators()) {
            for (List<UriRouteInfo<Object, Object>> routes : routesByMethod.values()) {
                hasEngineOrders |= hasEngineOrder(routes);
            }
            for (List<UriRouteInfo<Object, Object>> routes : customRoutesByMethod.values()) {
                hasEngineOrders |= hasEngineOrder(routes);
            }
        }
        this.hasEngineOrders = hasEngineOrders;
        boolean hasEngineSelectors = false;
        for (List<UriRouteInfo<Object, Object>> routes : routesByMethod.values()) {
            hasEngineSelectors |= hasEngineSelector(routes);
        }
        for (List<UriRouteInfo<Object, Object>> routes : customRoutesByMethod.values()) {
            hasEngineSelectors |= hasEngineSelector(routes);
        }
        this.hasEngineSelectors = hasEngineSelectors;
        Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodMap = CollectionUtils.newEnumMap(HttpMethod.values());
        Map<String, UriRouteInfo<Object, Object>[]> customMethodMap = CollectionUtils.newHashMap(routesByMethod.size() + customRoutesByMethod.size());
        for (Map.Entry<HttpMethod, List<UriRouteInfo<Object, Object>>> e : routesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] values = finalizeRoutes(e.getValue(), hasEngineOrders);
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
            customMethodMap.put(e.getKey(), finalizeRoutes(routes, hasEngineOrders));
        }
        this.methodRoutesByMethod = methodMap;
        this.allRoutesByMethod = customMethodMap;
        Map<String, RouteIndex> indexes = CollectionUtils.newHashMap(customMethodMap.size());
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> e : customMethodMap.entrySet()) {
            indexes.put(e.getKey(), indexRoutes(e.getValue()));
        }
        this.indexesByMethod = indexes;
        this.hasDynamicTargets = hasDynamicTargets;
        this.constrained = customMethodMap.values().stream().flatMap(Arrays::stream)
            .anyMatch(route -> route instanceof DefaultUrlRouteInfo<?, ?> info && info.isConstrained());
        this.empty = customMethodMap.isEmpty();
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
        return empty;
    }

    /**
     * @return The routes of the set
     */
    Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return allRoutesByMethod.values().stream().flatMap(Arrays::stream);
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
        return toMatches(uri, findInternal(request, ports));
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
        List<UriRouteInfo<Object, Object>> routes = findInternal(request, ports);
        if (routes.isEmpty()) {
            return null;
        }
        String path = request.getPath();
        if (hasEngineSelectors) {
            List<UriRouteMatch<T, R>> matches = toMatches(path, routes);
            RouteMatchSelector selector = matches.isEmpty() ? null : sameEngineSelector(matches);
            return closest(request, path, selector == null ? matches : select(selector, request, matches), selector == null);
        }
        if (routes.size() == 1) {
            Object o = routes.iterator().next();
            // avoid type pollution perf issues
            UriRouteInfo next = o instanceof DefaultUrlRouteInfo def ? def : (UriRouteInfo<Object, Object>) o;
            return (UriRouteMatch) next.tryMatch(path);
        }
        List<UriRouteMatch<T, R>> uriRoutes = new ArrayList<>(routes.size());
        for (UriRouteInfo<Object, Object> route : routes) {
            UriRouteMatch match = route.tryMatch(path);
            if (match != null) {
                uriRoutes.add(match);
            }
        }
        return closest(request, path, uriRoutes, true);
    }

    /**
     * The closest of the matches of a path.
     *
     * @param request     The request
     * @param path        The path
     * @param uriRoutes   The matches
     * @param resolve     Whether to resolve an ambiguity with the Micronaut policy, or the matches
     *                    were selected by a route selector
     * @return The closest match, or {@code null}
     * @throws DuplicateRouteException if the matches are ambiguous
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable <T, R> UriRouteMatch<T, R> closest(HttpRequest<?> request, String path, List<UriRouteMatch<T, R>> uriRoutes, boolean resolve) {
        if (uriRoutes.size() == 1) {
            Object obj = uriRoutes.get(0);
            // type pollution avoidance (should be covered by type pollution test)
            return obj instanceof DefaultUriRouteMatch<?, ?> def ? (DefaultUriRouteMatch<T, R>) def : (UriRouteMatch<T, R>) obj;
        }
        if (resolve) {
            uriRoutes = DefaultRouter.resolveAmbiguity(request, uriRoutes, hasEngineOrders);
        }
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
        List<UriRouteInfo<Object, Object>> routes = findInternal(request, ports);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = filter(toMatches(request.getPath(), routes), filter);
        if (hasEngineSelectors && !uriRoutes.isEmpty()) {
            RouteMatchSelector selector = sameEngineSelector(uriRoutes);
            if (selector != null) {
                return select(selector, request, uriRoutes);
            }
        }
        if (uriRoutes.size() < 2) {
            return uriRoutes;
        }
        return DefaultRouter.resolveAmbiguity(request, uriRoutes, hasEngineOrders);
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

    private <T, R> List<UriRouteMatch<T, R>> toMatches(String path, List<UriRouteInfo<Object, Object>> routes) {
        if (routes.size() == 1) {
            UriRouteMatch match = routes.iterator().next().tryMatch(path);
            if (match != null) {
                return List.of(match);
            }
            return List.of();
        }
        var uriRoutes = new ArrayList<UriRouteMatch<T, R>>(routes.size());
        for (UriRouteInfo<Object, Object> route : routes) {
            UriRouteMatch match = route.tryMatch(path);
            if (match != null) {
                uriRoutes.add(match);
            }
        }
        return uriRoutes;
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
            if (AnyMethodRoutes.CUSTOM_METHODS.equals(entry.getKey())) {
                // the route of any method has a route of each standard method too
                continue;
            }
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            for (int candidate : index(entry.getKey()).candidates(uri)) {
                UriRouteInfo<Object, Object> route = routes[candidate];
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
            if (AnyMethodRoutes.CUSTOM_METHODS.equals(entry.getKey())) {
                // the route of any method has a route of each standard method too
                continue;
            }
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            for (int candidate : index(entry.getKey()).candidates(path)) {
                UriRouteInfo<Object, Object> route = routes[candidate];
                if (shouldSkipForPort(request, route, ports)) {
                    continue;
                }
                if (!route.matching(request)) {
                    continue;
                }
                UriRouteMatch match = route.tryMatch(path);
                if (match != null && acceptsVariables(route, match)) {
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

    private List<UriRouteInfo<Object, Object>> findInternal(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        HttpMethod httpMethod = request.getMethod();
        boolean permitsBody = httpMethod.permitsRequestBody();
        Collection<MediaType> acceptedProducedTypes = null;
        MediaType contentType = null;
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
        int[] candidates = index(methodKey).candidates(request.getPath());
        if (candidates.length == 0) {
            return Collections.emptyList();
        }
        var result = new ArrayList<UriRouteInfo<Object, Object>>(candidates.length);
        for (int candidate : candidates) {
            UriRouteInfo<Object, Object> route = routes[candidate];
            if (shouldSkipForPort(request, route, ports)) {
                continue;
            }
            if (rejectsVariables(route, request.getPath())) {
                continue;
            }
            if (permitsBody) {
                if (!route.isPermitsRequestBody()) {
                    continue;
                }
                if (!route.consumesAll()) {
                    if (contentType == null) {
                        contentType = request.getContentType().orElse(null);
                    }
                    if (!route.doesConsume(contentType)) {
                        continue;
                    }
                }
            }
            if (!route.producesAll()) {
                if (acceptedProducedTypes == null) {
                    acceptedProducedTypes = request.accept();
                }
                if (!route.doesProduce(acceptedProducedTypes)) {
                    continue;
                }
            }
            if (!route.matching(request)) {
                continue;
            }
            result.add(route);
        }
        return result;
    }

    /**
     * Whether the route has constraints on its path variables that the variables it binds from
     * the path reject, see {@code RouteSpec#constrain}: then the route is not a candidate. A route
     * without constraints is not matched here.
     *
     * @param route The route
     * @param path  The path
     * @return Whether the route rejects the path
     */
    private static boolean rejectsVariables(UriRouteInfo<Object, Object> route, String path) {
        if (route instanceof DefaultUrlRouteInfo<Object, Object> info && info.isConstrained()) {
            UriRouteMatch<Object, Object> match = info.tryMatch(path);
            return match == null || !info.acceptsVariables(match.getVariableValues());
        }
        return false;
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

    private static boolean shouldSkipForPort(HttpRequest<?> request, UriRouteInfo<Object, Object> route, @Nullable Set<Integer> ports) {
        if (ports == null || route.getPort() != null) {
            return false;
        }
        return !ports.contains(request.getServerAddress().getPort());
    }

    private RouteIndex index(String methodKey) {
        // every method with routes has an index
        return Objects.requireNonNull(indexesByMethod.get(methodKey));
    }

    private static RouteIndex indexRoutes(UriRouteInfo<Object, Object>[] routes) {
        String[] prefixes = new String[routes.length];
        for (int i = 0; i < routes.length; i++) {
            prefixes[i] = routes[i] instanceof IndexedRoute route ? route.getRequiredPathPrefix() : "";
        }
        return RouteIndex.build(prefixes);
    }

    /**
     * Let the route selector of an engine select among the matches of its routes.
     *
     * @param selector The route selector
     * @param request  The request
     * @param matches  The matches, of routes of the engine
     * @return The selected matches, with their negotiated media types
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T, R> List<UriRouteMatch<T, R>> select(RouteMatchSelector selector, HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        List<RouteMatchSelector.Selection> selections = selector.select(request, Collections.unmodifiableList((List) matches));
        if (selections.isEmpty()) {
            return List.of();
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(selections.size());
        for (RouteMatchSelector.Selection selection : selections) {
            UriRouteMatch<?, ?> selected = selection.match();
            if (!containsIdentical(matches, selected)) {
                throw new IllegalStateException("The route selector " + selector + " selected a match that it was not given: " + selected);
            }
            MediaType mediaType = selection.responseMediaType();
            if (mediaType != null && selected instanceof DefaultUriRouteMatch<?, ?> defaultMatch) {
                selected = defaultMatch.withSelectedMediaType(mediaType);
            }
            result.add((UriRouteMatch<T, R>) selected);
        }
        return result;
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
     * @param matches The matches of a path
     * @return The route selector of the engine of the templates of all the matches, or
     * {@code null} if they are of different engines, their engine has no route selector, or a
     * match is of a route with a dynamic target, e.g. a locator route, whose resolved routes are
     * selected when the rest of the path is matched
     */
    private static @Nullable RouteMatchSelector sameEngineSelector(List<? extends UriRouteMatch<?, ?>> matches) {
        RouteMatchSelector selector = null;
        for (UriRouteMatch<?, ?> match : matches) {
            if (DynamicRouteTarget.of(match.getRouteInfo()) != null) {
                return null;
            }
            RouteMatchSelector own = routeMatchSelector(match.getRouteInfo());
            if (own == null || selector != null && own != selector) {
                return null;
            }
            selector = own;
        }
        return selector;
    }

    /**
     * @param route A route
     * @return The route selector of the engine of the route's template, or {@code null}
     */
    private static @Nullable RouteMatchSelector routeMatchSelector(UriRouteInfo<?, ?> route) {
        if (route instanceof DefaultUrlRouteInfo<?, ?> info) {
            return info.routeMatchSelector();
        }
        ParsedRouteTemplate template = DefaultRouter.engineTemplate(route);
        return template != null && RouteTemplateEngines.defaults().engine(template.engineId()) instanceof RouteMatchSelector selector ? selector : null;
    }

    /**
     * @param routes The routes of a method
     * @return Whether a route is of an engine with a route selector
     */
    private static boolean hasEngineSelector(List<UriRouteInfo<Object, Object>> routes) {
        for (UriRouteInfo<Object, Object> route : routes) {
            if (routeMatchSelector(route) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param routes The routes of a method
     * @return Whether a route is of an engine with its own order
     */
    private static boolean hasEngineOrder(List<UriRouteInfo<Object, Object>> routes) {
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        for (UriRouteInfo<Object, Object> route : routes) {
            ParsedRouteTemplate template = DefaultRouter.engineTemplate(route);
            if (template != null && engines.comparator(template.engineId()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Order the routes of each engine that declares its own order of specificity by that order,
     * among the positions its routes have in the Micronaut order: the routes of other engines keep
     * their positions, and so does every route when no engine declares an order.
     *
     * @param routes The routes of a method, in the Micronaut order
     */
    private static void orderByEngines(List<UriRouteInfo<Object, Object>> routes) {
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        Map<String, List<Integer>> positions = null;
        for (int i = 0; i < routes.size(); i++) {
            ParsedRouteTemplate template = DefaultRouter.engineTemplate(routes.get(i));
            if (template != null && engines.comparator(template.engineId()) != null) {
                if (positions == null) {
                    positions = new HashMap<>(2);
                }
                positions.computeIfAbsent(template.engineId(), id -> new ArrayList<>()).add(i);
            }
        }
        if (positions == null) {
            return;
        }
        positions.forEach((engineId, indexes) -> {
            Comparator<ParsedRouteTemplate> order = Objects.requireNonNull(engines.comparator(engineId));
            List<UriRouteInfo<Object, Object>> engineRoutes = new ArrayList<>(indexes.size());
            for (int index : indexes) {
                engineRoutes.add(routes.get(index));
            }
            // stable: routes the engine considers equally specific keep their Micronaut order
            engineRoutes.sort((a, b) -> order.compare(Objects.requireNonNull(DefaultRouter.engineTemplate(a)), Objects.requireNonNull(DefaultRouter.engineTemplate(b))));
            for (int i = 0; i < indexes.size(); i++) {
                routes.set(indexes.get(i), engineRoutes.get(i));
            }
        });
    }

    private static UriRouteInfo<Object, Object>[] finalizeRoutes(List<UriRouteInfo<Object, Object>> routes, boolean hasEngineOrders) {
        Collections.sort(routes);
        if (hasEngineOrders) {
            orderByEngines(routes);
        }
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
