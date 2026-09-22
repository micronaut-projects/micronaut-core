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
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateSegment;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    private final Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodRoutesByMethod;
    private final Map<String, UriRouteInfo<Object, Object>[]> allRoutesByMethod;
    /**
     * The index of the routes of each method, by method name, see {@link #allRoutesByMethod}.
     */
    private final Map<String, RouteIndex> indexesByMethod;
    /**
     * The routes of generated URL parsers, by the ordinal of their declarations.
     */
    private final CompiledRoutes[] compiledRoutes;
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
    private final boolean empty;

    private UriRouteSet(Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod,
                        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod,
                        Map<CompiledRouteMatcher, CompiledRoutes> compiled,
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
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> e : customMethodMap.entrySet()) {
            indexes.put(e.getKey(), indexRoutes(e.getValue()));
        }
        this.indexesByMethod = indexes;
        this.hasLocators = hasLocators;
        this.compiledRoutes = compiled.isEmpty() ? new CompiledRoutes[0] : withExclusivity(compiled.values());
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
        return toMatches(uri, allRoutesByMethod.getOrDefault(httpMethod.name(), EMPTY));
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
        if (compiledRoutes.length != 0) {
            UriRouteMatch<T, R> compiledMatch = findCompiled(request, ports);
            if (compiledMatch != null) {
                return compiledMatch;
            }
        }
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
     * @param filter  The filter of the candidates, applied before the ambiguity is resolved
     * @param ports   The default ports, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request,
                                                                  @Nullable Predicate<UriRouteMatch<T, R>> filter,
                                                                  @Nullable Set<Integer> ports) {
        if (compiledRoutes.length != 0) {
            UriRouteMatch<T, R> compiledMatch = findCompiled(request, ports);
            // a compiled match the filter rejects can hide a less specific route it accepts
            if (compiledMatch != null && (filter == null || accepts(filter, compiledMatch))) {
                return List.of(compiledMatch);
            }
        }
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
     * Whether the filter accepts a candidate. The route of a locator is no candidate: the filter
     * applies to the routes of its target's table, see {@link #locate}.
     */
    private <T, R> boolean accepts(Predicate<UriRouteMatch<T, R>> filter, UriRouteMatch<T, R> match) {
        return hasLocators && RouteLocator.of(match.getRouteInfo()) != null || filter.test(match);
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
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
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
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
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
                if (match != null) {
                    matchedRoutes.add(match);
                }
            }
        }
        return hasLocators ? locateAny(request, matchedRoutes) : matchedRoutes;
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
     * Match the request with the generated URL parsers. A route the parser answers is returned
     * directly only when no other route of its method can match the same paths: then the normal
     * selection could not choose another route, and the match is built from the captured path
     * variables. Otherwise the router selects among all candidates as usual, so specificity,
     * media types, ambiguity and explicit {@code HEAD} routes decide, and the order of the
     * parsers does not.
     *
     * @param request The request
     * @return The match, or {@code null} to select among the candidates
     */
    @SuppressWarnings("unchecked")
    private <T, R> @Nullable UriRouteMatch<T, R> findCompiled(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        HttpMethod method = request.getMethod();
        if (method == HttpMethod.CUSTOM) {
            return null;
        }
        String path = UriTemplateMatcher.normalizeForMatching(request.getPath());
        for (CompiledRoutes compiled : compiledRoutes) {
            String[] captured = new String[compiled.capturedSize];
            UriRouteInfo<Object, Object> route = null;
            boolean exclusive = false;
            int ordinal = compiled.matcher.match(method, path, captured);
            if (ordinal >= 0 && ordinal < compiled.byOrdinal.length) {
                route = compiled.byOrdinal[ordinal];
                exclusive = compiled.exclusive[ordinal];
            } else if (method == HttpMethod.HEAD) {
                // the implicit HEAD route of a GET route
                ordinal = compiled.matcher.match(HttpMethod.GET, path, captured);
                if (ordinal >= 0 && ordinal < compiled.headByOrdinal.length) {
                    route = compiled.headByOrdinal[ordinal];
                    exclusive = compiled.headExclusive[ordinal];
                }
            }
            if (route == null) {
                // not a bound route of this parser: another parser, or the router, may have one
                continue;
            }
            if (!exclusive || !isAcceptable(request, route, ports)) {
                return null;
            }
            UriRouteInfo<Object, Object> built = route instanceof LazyUriRouteInfo lazy ? lazy.delegate() : route;
            if (built instanceof DefaultUrlRouteInfo<?, ?> defaultRoute) {
                return (UriRouteMatch<T, R>) defaultRoute.capturedMatch(request.getPath(), captured);
            }
            return (UriRouteMatch<T, R>) built.tryMatch(request.getPath());
        }
        return null;
    }

    /**
     * Mark the compiled routes that no other route of the same method can compete with.
     *
     * @param compiled The routes bound to the generated URL parsers
     * @return The routes with their exclusivity
     */
    private CompiledRoutes[] withExclusivity(Collection<CompiledRoutes> compiled) {
        Map<UriRouteInfo<Object, Object>, Optional<List<RouteTemplateSegment>>> segments = new IdentityHashMap<>();
        CompiledRoutes[] result = new CompiledRoutes[compiled.size()];
        int i = 0;
        for (CompiledRoutes routes : compiled) {
            boolean[] exclusive = new boolean[routes.byOrdinal.length];
            boolean[] headExclusive = new boolean[routes.headByOrdinal.length];
            for (int ordinal = 0; ordinal < exclusive.length; ordinal++) {
                exclusive[ordinal] = isExclusive(routes.byOrdinal[ordinal], segments);
            }
            for (int ordinal = 0; ordinal < headExclusive.length; ordinal++) {
                headExclusive[ordinal] = isExclusive(routes.headByOrdinal[ordinal], segments);
            }
            // room for the variables of every bound route, even if the matcher under-reports them
            int capturedSize = routes.matcher.maxVariables();
            for (UriRouteInfo<Object, Object> route : routes.byOrdinal) {
                if (route instanceof IndexedRoute indexed) {
                    capturedSize = Math.max(capturedSize, indexed.getPathVariableCount());
                }
            }
            for (UriRouteInfo<Object, Object> route : routes.headByOrdinal) {
                if (route instanceof IndexedRoute indexed) {
                    capturedSize = Math.max(capturedSize, indexed.getPathVariableCount());
                }
            }
            result[i++] = new CompiledRoutes(routes.matcher, routes.byOrdinal, routes.headByOrdinal, exclusive, headExclusive, capturedSize);
        }
        return result;
    }

    private boolean isExclusive(@Nullable UriRouteInfo<Object, Object> route, Map<UriRouteInfo<Object, Object>, Optional<List<RouteTemplateSegment>>> segments) {
        if (!(route instanceof LazyUriRouteInfo lazy)) {
            return false;
        }
        if (hasEngineSelectors && routeMatchSelector(route) != null) {
            // its engine selects among its matches and negotiates the media type
            return false;
        }
        Optional<List<RouteTemplateSegment>> own = segments.computeIfAbsent(route, UriRouteSet::templateSegments);
        for (UriRouteInfo<Object, Object> other : allRoutesByMethod.getOrDefault(lazy.methodKey(), EMPTY)) {
            if (other != route && mayOverlap(own, segments.computeIfAbsent(other, UriRouteSet::templateSegments))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The path segments of a route's template, as the engine of the template describes them in
     * the parsed template, without building a route that is not built yet. Empty when the engine
     * cannot tell: the route may then overlap any other.
     */
    private static Optional<List<RouteTemplateSegment>> templateSegments(UriRouteInfo<Object, Object> route) {
        if (route instanceof LazyUriRouteInfo lazy) {
            return Optional.ofNullable(lazy.parsedTemplate().pathSegments());
        }
        if (route instanceof DefaultUrlRouteInfo<?, ?> info) {
            return Optional.ofNullable(info.parsedTemplate().pathSegments());
        }
        RouteTemplate template = route.getRouteTemplate();
        if (template.isMicronaut()) {
            return Optional.ofNullable(MicronautRouteTemplateEngine.INSTANCE.parse(template).pathSegments());
        }
        return Optional.empty();
    }

    /**
     * Whether two templates may match the same path. Only {@code false} is certain; unknown
     * segments may overlap anything.
     */
    private static boolean mayOverlap(Optional<List<RouteTemplateSegment>> first, Optional<List<RouteTemplateSegment>> second) {
        if (first.isEmpty() || second.isEmpty()) {
            return true;
        }
        List<RouteTemplateSegment> a = first.get();
        List<RouteTemplateSegment> b = second.get();
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            RouteTemplateSegment x = a.get(i);
            RouteTemplateSegment y = b.get(i);
            if (x.kind() == RouteTemplateSegment.Kind.ANY || y.kind() == RouteTemplateSegment.Kind.ANY) {
                return true;
            }
            if (x.kind() == RouteTemplateSegment.Kind.LITERAL && y.kind() == RouteTemplateSegment.Kind.LITERAL && !x.literal().equals(y.literal())) {
                return false;
            }
        }
        // the longer template matches the same paths only if its remaining segments can be empty
        List<RouteTemplateSegment> longer = a.size() > b.size() ? a : b;
        for (int i = common; i < longer.size(); i++) {
            if (longer.get(i).kind() != RouteTemplateSegment.Kind.ANY) {
                return false;
            }
        }
        return true;
    }

    /**
     * The same checks as {@link #findInternal(HttpRequest, Set)} for one route.
     */
    private boolean isAcceptable(HttpRequest<?> request, UriRouteInfo<Object, Object> route, @Nullable Set<Integer> ports) {
        if (shouldSkipForPort(request, route, ports)) {
            return false;
        }
        if (request.getMethod().permitsRequestBody()) {
            if (!route.isPermitsRequestBody()) {
                return false;
            }
            if (!route.consumesAll() && !route.doesConsume(request.getContentType().orElse(null))) {
                return false;
            }
        }
        if (!route.producesAll() && !route.doesProduce(request.accept())) {
            return false;
        }
        return route.matching(request);
    }

    private static void addCompiled(Map<CompiledRouteMatcher, CompiledRoutes> compiled, LazyUriRouteInfo route) {
        IndexedRouteDeclaration declaration = route.declaration();
        if (declaration == null || !(declaration instanceof Enum<?> constant)) {
            return;
        }
        CompiledRouteMatcher matcher = declaration.matcher();
        if (matcher == null) {
            return;
        }
        if (matcher.maxVariables() < 0) {
            throw new IllegalStateException("The compiled route matcher " + matcher + " of the route declaration "
                + constant.getDeclaringClass().getName() + "." + constant.name() + " has a negative maxVariables(): " + matcher.maxVariables());
        }
        int ordinal = constant.ordinal();
        CompiledRoutes routes = compiled.get(matcher);
        if (routes == null || ordinal >= routes.byOrdinal.length) {
            // sized by the largest bound ordinal: the enum's constants are not read reflectively
            int size = Math.max(ordinal + 1, routes == null ? 0 : routes.byOrdinal.length);
            routes = routes == null
                ? new CompiledRoutes(matcher, new UriRouteInfo[size], new UriRouteInfo[size])
                : new CompiledRoutes(matcher, Arrays.copyOf(routes.byOrdinal, size), Arrays.copyOf(routes.headByOrdinal, size));
            compiled.put(matcher, routes);
        }
        (route.isImplicitHead() ? routes.headByOrdinal : routes.byOrdinal)[ordinal] = route;
    }

    private List<UriRouteInfo<Object, Object>> findInternal(HttpRequest<?> request, @Nullable Set<Integer> ports) {
        HttpMethod httpMethod = request.getMethod();
        boolean permitsBody = httpMethod.permitsRequestBody();
        Collection<MediaType> acceptedProducedTypes = null;
        MediaType contentType = null;
        String methodKey = httpMethod == HttpMethod.CUSTOM ? request.getMethodName() : httpMethod.name();
        UriRouteInfo<Object, Object>[] routes = allRoutesByMethod.getOrDefault(methodKey, EMPTY);
        if (routes.length == 0) {
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
        private final Map<CompiledRouteMatcher, CompiledRoutes> compiled = new IdentityHashMap<>(2);
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
            addCompiled(compiled, uriRouteInfo);
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
            return new UriRouteSet(routesByMethod, customRoutesByMethod, compiled, hasLocators);
        }
    }

    /**
     * The routes bound to the declarations of a generated URL parser.
     *
     * @param matcher       The parser
     * @param byOrdinal     The bound routes, by the ordinal of their declarations
     * @param headByOrdinal The implicit {@code HEAD} routes of the bound {@code GET} routes, by ordinal
     * @param exclusive     Whether the route of an ordinal is the only route that can match its paths
     * @param headExclusive Whether the implicit {@code HEAD} route of an ordinal is the only route that can match its paths
     * @param capturedSize  The size of the array the matcher captures the path variables into: at
     *                      least its {@link CompiledRouteMatcher#maxVariables()} and the number of
     *                      path variables of every bound route
     */
    private record CompiledRoutes(CompiledRouteMatcher matcher,
                                  UriRouteInfo<Object, Object>[] byOrdinal,
                                  UriRouteInfo<Object, Object>[] headByOrdinal,
                                  boolean[] exclusive,
                                  boolean[] headExclusive,
                                  int capturedSize) {

        CompiledRoutes(CompiledRouteMatcher matcher, UriRouteInfo<Object, Object>[] byOrdinal, UriRouteInfo<Object, Object>[] headByOrdinal) {
            this(matcher, byOrdinal, headByOrdinal, new boolean[byOrdinal.length], new boolean[headByOrdinal.length], matcher.maxVariables());
        }
    }
}
