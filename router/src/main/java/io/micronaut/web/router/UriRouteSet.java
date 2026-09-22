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
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngine;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.spi.RouteCandidateSink;
import io.micronaut.web.router.spi.RouteMatchSelector;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An immutable set of URI routes, sorted by specificity for each HTTP method, including the custom
 * ones, and the matching of a request against them: its port, method, consumed and produced media
 * types and conditions, and the ambiguity between the routes that match. The application routes
 * of a {@link DefaultRouter} are one set, and the {@link RouteTable} of a {@link RouteSource} is
 * another: the router consults them in the order of their precedence.
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
    private static final Logger LOG = LoggerFactory.getLogger(UriRouteSet.class);

    private final Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodRoutesByMethod;
    private final Map<String, UriRouteInfo<Object, Object>[]> allRoutesByMethod;
    /**
     * The index of the routes of each method, by method name, see {@link #allRoutesByMethod}. A
     * route bound to a compiled slot of a route plan is not in the index: the parser of the plan
     * finds it.
     */
    private final Map<String, RouteIndex> indexesByMethod;
    /**
     * The route plans with the slots the routes of this set are bound to.
     */
    private final PlanBinding[] plans;
    /**
     * The index of {@link #plans} by the literal every path their parsers match starts with.
     */
    private final RouteIndex planIndex;
    /**
     * Whether a route is a locator route, see {@link RouteLocator}.
     */
    private final boolean hasLocators;
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
    /**
     * The engines, other than the Micronaut one, of the templates of the routes: each gives the
     * path its routes are matched against, see {@link RouteTemplateEngine#matchingPath(String)}.
     * Empty when every route is a Micronaut route: the router then matches the request path as
     * is, with no extra work.
     */
    private final RouteTemplateEngine[] pathEngines;
    /**
     * The identifiers of the {@link #pathEngines}.
     */
    private final String[] pathEngineIds;
    private final boolean empty;

    private UriRouteSet(Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod,
                        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod,
                        boolean hasLocators) {
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
        Set<String> engineIds = new LinkedHashSet<>();
        for (List<UriRouteInfo<Object, Object>> routes : routesByMethod.values()) {
            addEngineIds(engineIds, routes);
        }
        for (List<UriRouteInfo<Object, Object>> routes : customRoutesByMethod.values()) {
            addEngineIds(engineIds, routes);
        }
        this.pathEngineIds = engineIds.toArray(String[]::new);
        this.pathEngines = new RouteTemplateEngine[pathEngineIds.length];
        for (int i = 0; i < pathEngineIds.length; i++) {
            pathEngines[i] = RouteTemplateEngines.defaults().engine(pathEngineIds[i]);
        }
        Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodMap = CollectionUtils.newEnumMap(HttpMethod.values());
        Map<String, UriRouteInfo<Object, Object>[]> customMethodMap = CollectionUtils.newHashMap(routesByMethod.size() + customRoutesByMethod.size());
        for (Map.Entry<HttpMethod, List<UriRouteInfo<Object, Object>>> e : routesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] values = finalizeRoutes(e.getValue(), hasEngineOrders);
            methodMap.put(e.getKey(), values);
            customMethodMap.put(e.getKey().name(), values);
        }
        for (Map.Entry<String, List<UriRouteInfo<Object, Object>>> e : customRoutesByMethod.entrySet()) {
            customMethodMap.put(e.getKey(), finalizeRoutes(e.getValue(), hasEngineOrders));
        }
        this.methodRoutesByMethod = methodMap;
        this.allRoutesByMethod = customMethodMap;
        Map<String, RouteIndex> indexes = CollectionUtils.newHashMap(customMethodMap.size());
        Map<String, PlanBinding> bindings = new LinkedHashMap<>();
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> e : customMethodMap.entrySet()) {
            indexes.put(e.getKey(), indexRoutes(e.getKey(), e.getValue(), bindings));
        }
        this.indexesByMethod = indexes;
        this.hasLocators = hasLocators;
        List<PlanBinding> boundPlans = new ArrayList<>(bindings.size());
        for (PlanBinding binding : bindings.values()) {
            if (binding.isBound()) {
                binding.freeze();
                boundPlans.add(binding);
            }
        }
        this.plans = boundPlans.toArray(PlanBinding[]::new);
        String[] planPrefixes = new String[plans.length];
        for (int i = 0; i < plans.length; i++) {
            planPrefixes[i] = plans[i].plan.commonPrefix();
        }
        this.planIndex = RouteIndex.build(planPrefixes);
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
        return of(routes, List.of());
    }

    /**
     * The set of the given routes.
     *
     * @param routes     The routes, in the order they were declared: routes that are equally
     *                   specific keep it
     * @param lazyRoutes The routes built when first used, after the routes
     * @return The set
     */
    static UriRouteSet of(Collection<? extends UriRoute> routes, Collection<LazyUriRouteInfo> lazyRoutes) {
        Builder builder = new Builder();
        for (UriRoute route : routes) {
            builder.add(route);
        }
        for (LazyUriRouteInfo route : lazyRoutes) {
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
        // the candidates of the given URI, matched with their templates
        String[] paths = matchingPaths(uri);
        List<Candidate> candidates = findInternal(request, uri, paths, ports);
        var matches = new ArrayList<UriRouteMatch<T, R>>(candidates.size());
        for (Candidate candidate : candidates) {
            UriRouteMatch match = candidate.route.tryMatch(pathFor(candidate.route, uri, paths));
            if (match != null) {
                matches.add(match);
            }
        }
        return matches;
    }

    /**
     * The matches of the routes that accept the request, see {@link Router#find(HttpRequest)}.
     *
     * @param request The request
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The matches
     */
    <T, R> List<UriRouteMatch<T, R>> find(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        return toCandidateMatches(path, paths, findInternal(request, path, paths, ports));
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
        return toMatches(uri, matchingPaths(uri), allRoutesByMethod.getOrDefault(httpMethod.name(), EMPTY));
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
        if (hasLocators && match != null) {
            RouteLocator locator = RouteLocator.of(match.getRouteInfo());
            if (locator != null) {
                // the rest of the path is matched with the routes of the located target
                RouteLocator.Located located = locator.locate(request, match);
                if (located == null) {
                    return null;
                }
                UriRouteMatch<T, R> target = located.routes().findClosest(located.request(), null);
                return target == null ? null : located.wrap(target);
            }
        }
        return match;
    }

    private @Nullable <T, R> UriRouteMatch<T, R> findClosestRoute(HttpRequest<?> request, @Nullable Set<Integer> ports) throws DuplicateRouteException {
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        List<Candidate> routes = findInternal(request, path, paths, ports);
        if (routes.isEmpty()) {
            return null;
        }
        if (hasEngineSelectors) {
            List<UriRouteMatch<T, R>> matches = toCandidateMatches(path, paths, routes);
            RouteMatchSelector selector = matches.isEmpty() ? null : sameEngineSelector(matches);
            return closest(request, path, selector == null ? matches : select(selector, request, matches), selector == null);
        }
        if (routes.size() == 1) {
            Candidate candidate = routes.get(0);
            if (candidate.captured != null) {
                // the only accepted route: the parser of its plan matched it and captured its variables
                return (UriRouteMatch) candidate.capturedMatch();
            }
            Object o = candidate.route;
            // avoid type pollution perf issues
            UriRouteInfo next = o instanceof DefaultUrlRouteInfo def ? def : (UriRouteInfo<Object, Object>) o;
            return (UriRouteMatch) next.tryMatch(pathFor(candidate.route, path, paths));
        }
        List<UriRouteMatch<T, R>> uriRoutes = toCandidateMatches(path, paths, routes);
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
     * The closest of equally close matches: an explicit route over an implicit {@code HEAD} route,
     * then the route of the lowest order.
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
     * The closest matches of the request, see {@link Router#findAllClosest(HttpRequest, Predicate)}.
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
        return locate(request, findAllClosestRoutes(request, filter, ports), filter);
    }

    /**
     * Replace the matches of locator routes with the matches of the rest of the path in the
     * tables of their targets.
     *
     * @param request The request
     * @param matches The closest matches
     * @param filter  The filter of the candidates, applied to the matches of a target's table
     *                before its ambiguity is resolved
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches, located
     */
    private <T, R> List<UriRouteMatch<T, R>> locate(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches, @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (!hasLocators || matches.isEmpty()) {
            return matches;
        }
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            RouteLocator locator = RouteLocator.of(match.getRouteInfo());
            if (locator == null) {
                result.add(match);
                continue;
            }
            RouteLocator.Located located = locator.locate(request, match);
            if (located != null) {
                List<UriRouteMatch<T, R>> targetMatches = filter == null
                    ? located.routes().findAllClosest(located.request(), null, null)
                    // the filter sees the match of the request, as it does for the other routes
                    : located.routes().findAllClosest(located.request(), targetMatch -> filter.test(located.wrap(targetMatch)), null);
                result.addAll(located.wrap(targetMatches));
            }
        }
        return result;
    }

    /**
     * The closest matches of a request.
     *
     * @param request The request
     * @param filter  The filter of the candidates, applied before the ambiguity is resolved and
     *                before a route selector of an engine selects among them
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request,
                                                                  @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                                  @Nullable Set<Integer> ports) {
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        List<Candidate> routes = findInternal(request, path, paths, ports);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = filter(toCandidateMatches(path, paths, routes), filter);
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
     * Whether the filter accepts a candidate. The route of a locator is no candidate: the filter
     * applies to the routes of its target's table, see {@link #locate}.
     */
    private <T, R> boolean accepts(Predicate<UriRouteMatch<T, R>> filter, UriRouteMatch<T, R> match) {
        return hasLocators && RouteLocator.of(match.getRouteInfo()) != null || filter.test(match);
    }

    /**
     * Match candidates: a route the parser of its plan matched is matched with the captured
     * values, any other route with its template.
     */
    private <T, R> List<UriRouteMatch<T, R>> toCandidateMatches(String path, String @Nullable [] paths, List<Candidate> candidates) {
        var uriRoutes = new ArrayList<UriRouteMatch<T, R>>(candidates.size());
        for (Candidate candidate : candidates) {
            UriRouteMatch match = candidate.captured != null ? candidate.capturedMatch() : candidate.route.tryMatch(pathFor(candidate.route, path, paths));
            if (match != null) {
                uriRoutes.add(match);
            }
        }
        return uriRoutes;
    }

    private <T, R> List<UriRouteMatch<T, R>> toMatches(String path, String @Nullable [] paths, UriRouteInfo<Object, Object>[] routes) {
        if (paths != null) {
            var matches = new ArrayList<UriRouteMatch<T, R>>(routes.length);
            for (UriRouteInfo<Object, Object> route : routes) {
                UriRouteMatch match = route.tryMatch(pathFor(route, path, paths));
                if (match != null) {
                    matches.add(match);
                }
            }
            return matches;
        }
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
        String[] paths = matchingPaths(uri);
        for (UriRouteInfo<Object, Object> uriRouteInfo : methodRoutesByMethod.getOrDefault(httpMethod, EMPTY)) {
            Optional<UriRouteMatch<Object, Object>> match = uriRouteInfo.match(pathFor(uriRouteInfo, uri, paths));
            if (match.isPresent()) {
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
        String[] paths = matchingPaths(uri);
        for (String methodKey : allRoutesByMethod.keySet()) {
            for (Candidate candidate : candidates(methodKey, uri, paths)) {
                UriRouteInfo<Object, Object> route = candidate.route;
                if (request != null) {
                    if (shouldSkipForPort(request, route, ports)) {
                        continue;
                    }
                    if (!route.matching(request)) {
                        continue;
                    }
                }
                UriRouteMatch match = candidate.captured != null ? candidate.capturedMatch() : route.tryMatch(pathFor(route, uri, paths));
                if (match != null) {
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
        String[] paths = matchingPaths(path);
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (String methodKey : allRoutesByMethod.keySet()) {
            for (Candidate candidate : candidates(methodKey, path, paths)) {
                UriRouteInfo<Object, Object> route = candidate.route;
                if (shouldSkipForPort(request, route, ports)) {
                    continue;
                }
                if (!route.matching(request)) {
                    continue;
                }
                UriRouteMatch match = candidate.captured != null ? candidate.capturedMatch() : route.tryMatch(pathFor(route, path, paths));
                if (match != null) {
                    matchedRoutes.add(match);
                }
            }
        }
        List<UriRouteMatch<T, R>> selected = hasEngineSelectors && !matchedRoutes.isEmpty() ? selectAny(request, matchedRoutes) : matchedRoutes;
        return hasLocators ? locateAny(request, selected) : selected;
    }

    /**
     * Remove the matches that the route selector of their engine does not select, for each HTTP
     * method: the allowed methods of a path, and whether a method is allowed, count only the routes
     * the engine would route a request of that method to. As when the router finds the routes of a
     * method, the selector is given the matches of the method whose content type and accepted
     * types are compatible with the request, when they are all of its engine; otherwise the
     * Micronaut policy selects and every match is kept. The matches whose types are not compatible
     * are kept: they are the unsupported or not acceptable types of the path.
     *
     * @param request The request
     * @param matches The matches of the path, of every method
     * @return The matches, without the ones a route selector did not select
     */
    private <T, R> List<UriRouteMatch<T, R>> selectAny(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        Map<String, List<UriRouteMatch<T, R>>> byMethod = new LinkedHashMap<>();
        MediaType contentType = request.getContentType().orElse(null);
        Collection<MediaType> accepted = request.accept();
        for (UriRouteMatch<T, R> match : matches) {
            if (isCompatible(match.getRouteInfo(), contentType, accepted)) {
                byMethod.computeIfAbsent(match.getRouteInfo().getHttpMethodName(), method -> new ArrayList<>()).add(match);
            }
        }
        List<UriRouteMatch<?, ?>> rejected = null;
        for (List<UriRouteMatch<T, R>> candidates : byMethod.values()) {
            RouteMatchSelector selector = sameEngineSelector(candidates);
            if (selector == null) {
                continue;
            }
            List<UriRouteMatch<T, R>> selected = select(selector, request, candidates);
            for (UriRouteMatch<T, R> candidate : candidates) {
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
     * The checks of the types of {@link #findInternal(HttpRequest, String, String[], Set)}, for a
     * request of the method of the route.
     */
    private static boolean isCompatible(UriRouteInfo<?, ?> route, @Nullable MediaType contentType, Collection<MediaType> accepted) {
        if (route.getHttpMethod().permitsRequestBody()
            && (!route.isPermitsRequestBody() || (!route.consumesAll() && !route.doesConsume(contentType)))) {
            return false;
        }
        return route.producesAll() || route.doesProduce(accepted);
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
     * Replace the matches of locator routes, one per HTTP method, with the matches of the rest of
     * the path in the routes of the located target, e.g. to find the allowed methods.
     */
    private <T, R> List<UriRouteMatch<T, R>> locateAny(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches) {
        UriRouteMatch<T, R> locatorMatch = null;
        RouteLocator locator = null;
        List<UriRouteMatch<T, R>> result = new ArrayList<>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            RouteLocator matchLocator = RouteLocator.of(match.getRouteInfo());
            if (matchLocator == null) {
                result.add(match);
            } else if (locatorMatch == null || match.getRouteInfo().compareTo((UriRouteInfo) locatorMatch.getRouteInfo()) < 0) {
                // the most specific locator locates the target
                locatorMatch = match;
                locator = matchLocator;
            }
        }
        if (locator == null || locatorMatch == null) {
            return matches;
        }
        RouteLocator.Located located;
        try {
            located = locator.locate(request, locatorMatch);
        } catch (RuntimeException e) {
            if (RouteLocator.pendingLocation(e) == null) {
                throw e;
            }
            // an asynchronous locator that has not located its target: no route of it is known
            return result;
        }
        if (located != null) {
            result.addAll(located.wrap(located.routes().<T, R>findAny(located.request(), null)));
        }
        return result;
    }

    /**
     * The routes of a method that may match a path, in the order of the routes: the routes the
     * index of the method finds, and the routes the parsers of the route plans matched, with the
     * values they captured. A route of a plan is a candidate like any other: the caller applies
     * the same acceptance and selection rules to both.
     *
     * <p>Every route is looked up with the path its engine matches, see
     * {@link RouteTemplateEngine#matchingPath(String)}: the index and the parsers of the plans
     * are given that path, so a compiled plan answers exactly what the runtime matching does.</p>
     *
     * @param methodKey The method name
     * @param path      The path
     * @param paths     The paths of the {@link #pathEngines}, or {@code null} if they all match the path
     * @return The candidates
     */
    private List<Candidate> candidates(String methodKey, String path, String @Nullable [] paths) {
        UriRouteInfo<Object, Object>[] routes = allRoutesByMethod.getOrDefault(methodKey, EMPTY);
        if (routes.length == 0) {
            return List.of();
        }
        int[] ordinary = ordinaryCandidates(methodKey, routes, path, paths);
        PlanCandidates planned = null;
        if (plans.length != 0) {
            planned = matchPlans(methodKey, routes, path, path, paths, null);
            if (paths != null) {
                for (int i = 0; i < paths.length; i++) {
                    String enginePath = paths[i];
                    if (!enginePath.equals(path) && firstIndexOf(paths, enginePath) == i) {
                        planned = matchPlans(methodKey, routes, enginePath, path, paths, planned);
                    }
                }
            }
        }
        if (planned == null || planned.size == 0) {
            if (ordinary.length == 0) {
                return List.of();
            }
            List<Candidate> result = new ArrayList<>(ordinary.length);
            for (int rank : ordinary) {
                result.add(new Candidate(routes[rank], null, null));
            }
            return result;
        }
        planned.sort();
        List<Candidate> result = new ArrayList<>(ordinary.length + planned.size);
        int o = 0;
        int h = 0;
        while (o < ordinary.length || h < planned.size) {
            if (h == planned.size || o < ordinary.length && ordinary[o] < planned.ranks[h]) {
                result.add(new Candidate(routes[ordinary[o++]], null, null));
            } else {
                result.add(new Candidate(routes[planned.ranks[h]], planned.paths[h], planned.captured[h]));
                h++;
            }
        }
        return result;
    }

    private List<Candidate> findInternal(HttpRequest<?> request, String path, String @Nullable [] paths, @Nullable Set<Integer> ports) {
        HttpMethod httpMethod = request.getMethod();
        boolean permitsBody = httpMethod.permitsRequestBody();
        Collection<MediaType> acceptedProducedTypes = null;
        MediaType contentType = null;
        String methodKey = httpMethod == HttpMethod.CUSTOM ? request.getMethodName() : httpMethod.name();
        List<Candidate> candidates = candidates(methodKey, path, paths);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        var result = new ArrayList<Candidate>(candidates.size());
        for (Candidate candidate : candidates) {
            UriRouteInfo<Object, Object> route = candidate.route;
            if (shouldSkipForPort(request, route, ports)) {
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
            result.add(candidate);
        }
        return result;
    }

    /**
     * Match the parsers of the route plans against one path, and keep the routes that are
     * matched against that path.
     *
     * @param methodKey    The method name
     * @param routes       The routes of the method
     * @param matchingPath The path to match the parsers against
     * @param path         The request path
     * @param paths        The paths of the {@link #pathEngines}, or {@code null} if they all match the path
     * @param planned      The routes matched so far, or {@code null}
     * @return The routes matched so far and the routes of this path
     */
    private @Nullable PlanCandidates matchPlans(String methodKey,
                                                UriRouteInfo<Object, Object>[] routes,
                                                String matchingPath,
                                                String path,
                                                String @Nullable [] paths,
                                                @Nullable PlanCandidates planned) {
        String normalized = UriTemplateMatcher.normalizeForMatching(matchingPath);
        for (int p : planIndex.candidates(normalized)) {
            PlanBinding binding = plans[p];
            if (binding.hasMethod(methodKey)) {
                if (planned == null) {
                    planned = new PlanCandidates(methodKey);
                }
                planned.binding = binding;
                // a route of another engine matches another path: it is a candidate of that path
                planned.accept = paths == null ? null : rank -> pathFor(routes[rank], path, paths).equals(matchingPath);
                binding.plan.match(normalized, planned);
            }
        }
        return planned;
    }

    /**
     * @return The positions of the routes of the index that can match, each looked up with the
     * path its engine matches, in ascending order
     */
    private int[] ordinaryCandidates(String methodKey, UriRouteInfo<Object, Object>[] routes, String path, String @Nullable [] paths) {
        RouteIndex index = index(methodKey);
        if (paths == null) {
            return index.candidates(path);
        }
        IntStream result = candidatesFor(index, routes, path, path, paths);
        for (int i = 0; i < paths.length; i++) {
            String enginePath = paths[i];
            if (!enginePath.equals(path) && firstIndexOf(paths, enginePath) == i) {
                result = IntStream.concat(result, candidatesFor(index, routes, enginePath, path, paths));
            }
        }
        return result.sorted().toArray();
    }

    /**
     * @return The candidates of a path among the routes that are matched against that path
     */
    private IntStream candidatesFor(RouteIndex index, UriRouteInfo<Object, Object>[] routes, String matchingPath, String path, String[] paths) {
        return Arrays.stream(index.candidates(matchingPath)).filter(i -> pathFor(routes[i], path, paths).equals(matchingPath));
    }

    private static int firstIndexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The paths the routes of the engines other than the Micronaut one match for a request path,
     * see {@link RouteTemplateEngine#matchingPath(String)}.
     *
     * @param path The request path
     * @return The path of each of the {@link #pathEngines}, or {@code null} if they all match the
     * request path itself: always for a router of Micronaut routes only
     */
    private String @Nullable [] matchingPaths(String path) {
        RouteTemplateEngine[] engines = pathEngines;
        if (engines.length == 0) {
            return null;
        }
        String[] paths = null;
        for (int i = 0; i < engines.length; i++) {
            String enginePath = Objects.requireNonNull(engines[i].matchingPath(path), "The matching path");
            if (enginePath != path && !enginePath.equals(path)) {
                if (paths == null) {
                    paths = new String[engines.length];
                    Arrays.fill(paths, path);
                }
                paths[i] = enginePath;
            }
        }
        return paths;
    }

    /**
     * @param route A route
     * @param path  The request path
     * @param paths The paths of the {@link #pathEngines}, or {@code null} if they all match the request path
     * @return The path the route is matched against
     */
    private String pathFor(UriRouteInfo<?, ?> route, String path, String @Nullable [] paths) {
        if (paths == null) {
            return path;
        }
        String engineId = engineId(route);
        for (int i = 0; i < pathEngineIds.length; i++) {
            if (pathEngineIds[i].equals(engineId)) {
                return paths[i];
            }
        }
        return path;
    }

    /**
     * @param route A route
     * @return The identifier of the engine of the route's template, without building a route that is not built yet
     */
    private static String engineId(UriRouteInfo<?, ?> route) {
        if (route instanceof LazyUriRouteInfo lazy) {
            return lazy.engineId();
        }
        if (route instanceof DefaultUrlRouteInfo<?, ?> info) {
            return info.isMicronautTemplate() ? RouteTemplate.MICRONAUT : info.parsedTemplate().engineId();
        }
        return route.getRouteTemplate().engineId();
    }

    private static void addEngineIds(Set<String> engineIds, List<UriRouteInfo<Object, Object>> routes) {
        for (UriRouteInfo<Object, Object> route : routes) {
            String engineId = engineId(route);
            if (!RouteTemplate.MICRONAUT.equals(engineId)) {
                engineIds.add(engineId);
            }
        }
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

    /**
     * Index the routes of a method, and bind the routes of the compiled slots of route plans to
     * their slots: those are found by the parsers of the plans, not by the index.
     */
    private static RouteIndex indexRoutes(String methodKey, UriRouteInfo<Object, Object>[] routes, Map<String, PlanBinding> bindings) {
        @Nullable String[] prefixes = new String[routes.length];
        for (int i = 0; i < routes.length; i++) {
            UriRouteInfo<Object, Object> route = routes[i];
            if (route instanceof LazyUriRouteInfo lazy && bind(methodKey, i, lazy, bindings)) {
                prefixes[i] = null;
            } else {
                prefixes[i] = route instanceof IndexedRoute indexed ? indexed.getRequiredPathPrefix() : "";
            }
        }
        return RouteIndex.build(prefixes);
    }

    /**
     * Bind a route to the slot of its declaration in its route plan: the key of the declaration is
     * resolved to the slot once, here. A route whose plan the router cannot use, whose slot the
     * parser does not match, or that does not agree with its slot, stays an ordinary route.
     *
     * @return Whether the route is bound
     */
    private static boolean bind(String methodKey, int rank, LazyUriRouteInfo route, Map<String, PlanBinding> bindings) {
        RoutePlan plan = route.plan();
        String key = route.planKey();
        if (plan == null || key == null) {
            return false;
        }
        PlanBinding binding = bindings.computeIfAbsent(plan.id(), id -> PlanBinding.of(plan));
        if (binding == PlanBinding.UNUSABLE) {
            return false;
        }
        if (binding.plan != plan && !binding.plan.fingerprint().equals(plan.fingerprint())) {
            LOG.warn("Two different route plans have the identity {}: the routes of {} are matched at runtime", plan.id(), plan);
            return false;
        }
        Integer slot = binding.slotByKey.get(key);
        if (slot == null) {
            return false;
        }
        RouteSlot descriptor = binding.slots[slot];
        boolean sameMethod = descriptor.httpMethodName().equals(methodKey)
            // the implicit HEAD route of a declared GET route is bound to the slot of the GET route
            || route.isImplicitHead() && HttpMethod.GET.name().equals(descriptor.httpMethodName());
        if (!descriptor.compiled() || !sameMethod || !descriptor.template().equals(route.template())) {
            return false;
        }
        binding.bind(slot, new Bound(methodKey, rank, route, descriptor.captures().length));
        return true;
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
     * match is of a locator route, whose located routes are selected when the rest of the path
     * is matched
     */
    private static @Nullable RouteMatchSelector sameEngineSelector(List<? extends UriRouteMatch<?, ?>> matches) {
        RouteMatchSelector selector = null;
        for (UriRouteMatch<?, ?> match : matches) {
            if (RouteLocator.of(match.getRouteInfo()) != null) {
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
        private boolean hasLocators;

        /**
         * Add a route.
         *
         * @param route The route
         */
        void add(UriRoute route) {
            HttpMethod httpMethod = route.getHttpMethod();
            UriRouteInfo<Object, Object> uriRouteInfo = route.toRouteInfo();
            hasLocators = hasLocators || RouteLocator.of(uriRouteInfo) != null;
            if (httpMethod == HttpMethod.CUSTOM) {
                String key = route.getHttpMethodName();
                customRoutesByMethod.computeIfAbsent(key, x -> new ArrayList<>()).add(uriRouteInfo);
            } else {
                routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
            }
        }

        /**
         * Add a route built when first used.
         *
         * @param uriRouteInfo The route
         */
        void add(LazyUriRouteInfo uriRouteInfo) {
            HttpMethod httpMethod = uriRouteInfo.getHttpMethod();
            if (httpMethod == HttpMethod.CUSTOM) {
                customRoutesByMethod.computeIfAbsent(uriRouteInfo.methodKey(), x -> new ArrayList<>()).add(uriRouteInfo);
            } else {
                routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
            }
        }

        /**
         * @return The set of the added routes
         */
        UriRouteSet build() {
            return new UriRouteSet(routesByMethod, customRoutesByMethod, hasLocators);
        }
    }

    /**
     * A route that may match a path.
     *
     * @param route    The route
     * @param path     The normalised path the parser of the plan of the route matched, or {@code null}
     * @param captured The values the parser captured, or {@code null} for a route matched with its template
     */
    private record Candidate(UriRouteInfo<Object, Object> route, @Nullable String path, String @Nullable [] captured) {

        UriRouteMatch<Object, Object> capturedMatch() {
            return ((LazyUriRouteInfo) route).capturedMatch(Objects.requireNonNull(path), Objects.requireNonNull(captured));
        }
    }

    /**
     * A route bound to a slot of a route plan.
     *
     * @param methodKey The method name the route is registered under
     * @param rank      The position of the route among the routes of the method
     * @param route     The route
     * @param captures  The number of values the parser captures for the slot
     */
    private record Bound(String methodKey, int rank, LazyUriRouteInfo route, int captures) {
    }

    /**
     * The routes of this router bound to the slots of a route plan: the binding arrays of one
     * router, never shared with another router or kept in static state.
     */
    private static final class PlanBinding {
        static final PlanBinding UNUSABLE = new PlanBinding(null, new RouteSlot[0]);
        private static final Bound[] NONE = new Bound[0];

        final RoutePlan plan;
        final RouteSlot[] slots;
        final Map<String, Integer> slotByKey;
        private final List<Bound>[] bound;
        private Bound[][] bySlot = new Bound[0][];
        private final Set<String> methods = new HashSet<>();

        @SuppressWarnings("unchecked")
        private PlanBinding(@Nullable RoutePlan plan, RouteSlot[] slots) {
            this.plan = plan == null ? NoPlan.INSTANCE : plan;
            this.slots = slots;
            this.slotByKey = CollectionUtils.newHashMap(slots.length);
            for (int i = 0; i < slots.length; i++) {
                if (slotByKey.putIfAbsent(slots[i].key(), i) != null) {
                    throw new RoutingException("The route plan " + this.plan.id() + " has two slots with the key " + slots[i].key());
                }
            }
            this.bound = new List[slots.length];
        }

        static PlanBinding of(RoutePlan plan) {
            RouteSlot[] slots = plan.slots();
            return RoutePlans.usable(plan, slots) ? new PlanBinding(plan, slots) : UNUSABLE;
        }

        void bind(int slot, Bound route) {
            List<Bound> routes = bound[slot];
            if (routes == null) {
                routes = new ArrayList<>(2);
                bound[slot] = routes;
            }
            routes.add(route);
            methods.add(route.methodKey());
        }

        boolean isBound() {
            return !methods.isEmpty();
        }

        boolean hasMethod(String methodKey) {
            return methods.contains(methodKey);
        }

        void freeze() {
            Bound[][] frozen = new Bound[bound.length][];
            for (int i = 0; i < bound.length; i++) {
                frozen[i] = bound[i] == null ? NONE : bound[i].toArray(NONE);
            }
            bySlot = frozen;
        }

        Bound[] bound(int slot) {
            return slot >= 0 && slot < bySlot.length ? bySlot[slot] : NONE;
        }
    }

    /**
     * Collects the routes of one method whose slots the parser of a plan matched.
     */
    private static final class PlanCandidates implements RouteCandidateSink {
        final String methodKey;
        @Nullable PlanBinding binding;
        /**
         * Accepts the routes that are matched against the path the parser was given, or
         * {@code null} for every route.
         */
        @Nullable IntPredicate accept;
        int size;
        int[] ranks = new int[4];
        String[] paths = new String[4];
        String[][] captured = new String[4][];

        PlanCandidates(String methodKey) {
            this.methodKey = methodKey;
        }

        @Override
        public void candidate(int slot, String path, int[] spans) {
            for (Bound bound : Objects.requireNonNull(binding).bound(slot)) {
                if (!bound.methodKey().equals(methodKey)) {
                    continue;
                }
                if (accept != null && !accept.test(bound.rank())) {
                    continue;
                }
                String[] values = new String[bound.captures()];
                for (int i = 0; i < values.length; i++) {
                    values[i] = path.substring(spans[2 * i], spans[2 * i + 1]);
                }
                if (size == ranks.length) {
                    ranks = Arrays.copyOf(ranks, size * 2);
                    paths = Arrays.copyOf(paths, size * 2);
                    captured = Arrays.copyOf(captured, size * 2);
                }
                ranks[size] = bound.rank();
                paths[size] = path;
                captured[size] = values;
                size++;
            }
        }

        /**
         * Sort the matched routes by their rank; there are few.
         */
        void sort() {
            for (int i = 1; i < size; i++) {
                for (int j = i; j > 0 && ranks[j - 1] > ranks[j]; j--) {
                    int rank = ranks[j];
                    ranks[j] = ranks[j - 1];
                    ranks[j - 1] = rank;
                    String path = paths[j];
                    paths[j] = paths[j - 1];
                    paths[j - 1] = path;
                    String[] values = captured[j];
                    captured[j] = captured[j - 1];
                    captured[j - 1] = values;
                }
            }
        }
    }

    /**
     * Stands for the plan of a binding the router cannot use.
     */
    private static final class NoPlan implements RoutePlan {
        static final NoPlan INSTANCE = new NoPlan();

        @Override
        public String id() {
            return "none";
        }

        @Override
        public int abiVersion() {
            return ABI_VERSION;
        }

        @Override
        public String inputProfile() {
            return INPUT_PROFILE;
        }

        @Override
        public String selectionPolicy() {
            return SELECTION_POLICY;
        }

        @Override
        public String fingerprint() {
            return "";
        }

        @Override
        public String[] owners() {
            return new String[0];
        }

        @Override
        public RouteSlot[] slots() {
            return new RouteSlot[0];
        }

        @Override
        public String commonPrefix() {
            return "";
        }

        @Override
        public int maxCaptures() {
            return 0;
        }

        @Override
        public void match(String path, RouteCandidateSink sink) {
            // matches nothing
        }
    }
}
