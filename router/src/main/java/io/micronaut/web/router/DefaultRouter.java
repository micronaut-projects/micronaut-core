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
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.FilterRunner;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.filter.HttpServerFilterResolver;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.RouteTemplateSegment;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import io.micronaut.web.router.spi.RouteMatchSelector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * <p>The default {@link Router} implementation. This implementation does not perform any additional caching of
 * route discovery.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRouter implements Router, HttpServerFilterResolver<RouteMatch<?>> {

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
    private final StatusRouteInfo<Object, Object>[] statusRoutes;
    private final ErrorRouteInfo<Object, Object>[] errorRoutes;
    private final Set<Integer> exposedPorts;
    @Nullable
    private volatile Set<Integer> ports;
    private final List<FilterRoute> alwaysMatchesFilterRoutes;
    private final List<FilterRoute> preconditionFilterRoutes;
    private final List<FilterRoute> preMatchingAlwaysMatchesFilterRoutes;
    private final List<FilterRoute> preMatchingPreconditionFilterRoutes;
    // ArrayList to avoid interface checkcast
    private final Supplier<ArrayList<GenericHttpFilter>> alwaysMatchesHttpFilters;
    private final Supplier<ArrayList<GenericHttpFilter>> preMatchingAlwaysMatchesHttpFilters;

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    public DefaultRouter(RouteBuilder... builders) {
        this(Arrays.asList(builders));
    }

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    public DefaultRouter(Collection<RouteBuilder> builders) {
        this(builders, List.of());
    }

    /**
     * Construct a new router for the given route builders and the routes assembled without one.
     *
     * @param builders  The builders
     * @param assembled The routes assembled without a builder, e.g. the routes of the {@link io.micronaut.web.router.builder.HttpRoutes} beans
     * @since 5.3.0
     */
    @Inject
    public DefaultRouter(Collection<RouteBuilder> builders, List<AssembledRoutes> assembled) {
        Set<Integer> exposedPorts = new HashSet<>(5);
        Map<CompiledRouteMatcher, CompiledRoutes> compiled = new IdentityHashMap<>(2);
        Map<String, List<UriRouteInfo<Object, Object>>> customRoutesByMethod = new HashMap<>();
        HttpMethod[] httpMethods = HttpMethod.values();
        Map<HttpMethod, List<UriRouteInfo<Object, Object>>> routesByMethod = CollectionUtils.newEnumMap(httpMethods);
        Set<StatusRouteInfo<Object, Object>> statusRoutes = new LinkedHashSet<>();
        Set<ErrorRouteInfo<Object, Object>> errorRoutes = new LinkedHashSet<>();
        alwaysMatchesFilterRoutes = new ArrayList<>(20);
        preconditionFilterRoutes = new ArrayList<>(20);
        preMatchingAlwaysMatchesFilterRoutes = new ArrayList<>(10);
        preMatchingPreconditionFilterRoutes = new ArrayList<>(10);
        List<RouteSet> routeSets = new ArrayList<>(builders.size() + assembled.size());
        boolean hasLocators = false;
        for (RouteBuilder builder : builders) {
            routeSets.add(new RouteSet(builder.getUriRoutes(), builder.getStatusRoutes(), builder.getErrorRoutes(), builder.getFilterRoutes(),
                // precompiled controller routes and declared routes, built when first used
                builder instanceof DefaultRouteBuilder defaultBuilder ? defaultBuilder.lazyRouteInfos() : List.of(),
                builder.getExposedPorts()));
        }
        for (AssembledRoutes routes : assembled) {
            RouteAssembly assembly = routes.routes();
            routeSets.add(new RouteSet(assembly.uriRoutes(), assembly.statusRoutes(), assembly.errorRoutes(), List.of(),
                assembly.lazyRouteInfos(), assembly.exposedPorts()));
        }
        for (RouteSet routeSet : routeSets) {
            for (UriRoute route : routeSet.uriRoutes()) {
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

            for (StatusRoute statusRoute : routeSet.statusRoutes()) {
                StatusRouteInfo<Object, Object> routeInfo = statusRoute.toRouteInfo();
                if (statusRoutes.contains(routeInfo)) {
                    final StatusRouteInfo<Object, Object> existing = statusRoutes.stream().filter(r -> r.equals(routeInfo)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for http status [" + statusRoute.statusCode() + "]. New route: " + statusRoute + ". Existing: " + existing);
                }
                statusRoutes.add(routeInfo);
            }
            for (ErrorRoute errorRoute : routeSet.errorRoutes()) {
                ErrorRouteInfo<Object, Object> routeInfo = errorRoute.toRouteInfo();
                if (errorRoutes.contains(routeInfo)) {
                    final ErrorRouteInfo<Object, Object> existing = errorRoutes.stream().filter(r -> r.equals(routeInfo)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for error [" + errorRoute.exceptionType().getSimpleName() + "]. New route: " + errorRoute + ". Existing: " + existing);
                }
                errorRoutes.add(routeInfo);
            }
            for (FilterRoute filterRoute : routeSet.filterRoutes()) {
                if (filterRoute.isPreMatching()) {
                    if (isMatchesAll(filterRoute)) {
                        preMatchingAlwaysMatchesFilterRoutes.add(filterRoute);
                    } else {
                        preMatchingPreconditionFilterRoutes.add(filterRoute);
                    }
                } else if (isMatchesAll(filterRoute)) {
                    alwaysMatchesFilterRoutes.add(filterRoute);
                } else {
                    preconditionFilterRoutes.add(filterRoute);
                }
            }
            for (LazyUriRouteInfo uriRouteInfo : routeSet.lazyRoutes()) {
                addCompiled(compiled, uriRouteInfo);
                HttpMethod httpMethod = uriRouteInfo.getHttpMethod();
                if (httpMethod == HttpMethod.CUSTOM) {
                    customRoutesByMethod.computeIfAbsent(uriRouteInfo.methodKey(), x -> new ArrayList<>()).add(uriRouteInfo);
                } else {
                    routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
                }
            }
            exposedPorts.addAll(routeSet.exposedPorts());
        }

        if (CollectionUtils.isNotEmpty(exposedPorts)) {
            this.exposedPorts = exposedPorts;
        } else {
            this.exposedPorts = Collections.emptySet();
        }
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
        Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodMap = CollectionUtils.newEnumMap(httpMethods);
        Map<String, UriRouteInfo<Object, Object>[]> customMethodMap = CollectionUtils.newHashMap(routesByMethod.size() + customRoutesByMethod.size());
        for (Map.Entry<HttpMethod, List<UriRouteInfo<Object, Object>>> e : routesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] values = finalizeRoutes(e.getValue());
            methodMap.put(e.getKey(), values);
            customMethodMap.put(e.getKey().name(), values);
        }
        for (Map.Entry<String, List<UriRouteInfo<Object, Object>>> e : customRoutesByMethod.entrySet()) {
            customMethodMap.put(e.getKey(), finalizeRoutes(e.getValue()));
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
        this.statusRoutes = statusRoutes.toArray(StatusRouteInfo[]::new);
        this.errorRoutes = errorRoutes.toArray(ErrorRouteInfo[]::new);
        this.alwaysMatchesHttpFilters = SupplierUtil.memoized(() -> {
            if (alwaysMatchesFilterRoutes.isEmpty()) {
                return new ArrayList<>(0);
            }
            ArrayList<GenericHttpFilter> httpFilters = new ArrayList<>(alwaysMatchesFilterRoutes.size());
            for (FilterRoute filterRoute : alwaysMatchesFilterRoutes) {
                httpFilters.add(filterRoute.getFilter());
            }
            FilterRunner.sort(httpFilters);
            return httpFilters;
        });
        this.preMatchingAlwaysMatchesHttpFilters = SupplierUtil.memoized(() -> {
            if (preMatchingAlwaysMatchesFilterRoutes.isEmpty()) {
                return new ArrayList<>(0);
            }
            ArrayList<GenericHttpFilter> httpFilters = new ArrayList<>(preMatchingAlwaysMatchesFilterRoutes.size());
            for (FilterRoute filterRoute : preMatchingAlwaysMatchesFilterRoutes) {
                httpFilters.add(filterRoute.getFilter());
            }
            FilterRunner.sort(httpFilters);
            return httpFilters;
        });
    }

    private boolean isMatchesAll(FilterRoute filterRoute) {
        if (filterRoute.getAnnotationMetadata().hasStereotype(FilterMatcher.NAME)) {
            return false;
        }
        if (filterRoute.hasMethods()) {
            return false;
        }
        if (filterRoute.hasPatterns()) {
            for (String pattern : filterRoute.getPatterns()) {
                if (!Filter.MATCH_ALL_PATTERN.equals(pattern)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public Set<Integer> getExposedPorts() {
        return exposedPorts;
    }

    @Override
    public void applyDefaultPorts(List<Integer> ports) {
        this.ports = new HashSet<>(ports);
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        return this.<T, R>toMatches(uri.toString(), findInternal(request)).stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        return this.<T, R>toMatches(request.getPath(), findInternal(request)).stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        return this.<T, R>toMatches(
                uri.toString(),
            allRoutesByMethod.getOrDefault(httpMethod.name(), EMPTY)
        ).stream();
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return allRoutesByMethod.values().stream().flatMap(Arrays::stream);
    }

    @Override
    public @Nullable <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        UriRouteMatch<T, R> match = findClosestRoute(request);
        if (hasLocators && match != null) {
            RouteLocator locator = RouteLocator.of(match.getRouteInfo());
            if (locator != null) {
                // the rest of the path is matched with the routes of the located target
                RouteLocator.Located located = locator.locate(request, match);
                if (located == null) {
                    return null;
                }
                UriRouteMatch<T, R> target = located.router().findClosest(located.request());
                return target == null ? null : located.wrap(target);
            }
        }
        return match;
    }

    private @Nullable <T, R> UriRouteMatch<T, R> findClosestRoute(HttpRequest<?> request) throws DuplicateRouteException {
        if (compiledRoutes.length != 0) {
            UriRouteMatch<T, R> compiledMatch = findCompiled(request);
            if (compiledMatch != null) {
                return compiledMatch;
            }
        }
        List<UriRouteInfo<Object, Object>> routes = findInternal(request);
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
            uriRoutes = resolveAmbiguity(request, uriRoutes);
        }
        if (uriRoutes.size() > 1) {
            uriRoutes = ImplicitHeadRoutes.preferExplicit(uriRoutes);
        }
        if (uriRoutes.size() > 1) {
            throw new DuplicateRouteException(path, (List) uriRoutes);
        } else if (uriRoutes.size() == 1) {
            return uriRoutes.get(0);
        }
        return null;
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> matches = findAllClosestRoutes(request);
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
                result.addAll(located.wrap(located.router().<T, R>findAllClosest(located.request())));
            }
        }
        return result;
    }

    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request) {
        if (compiledRoutes.length != 0) {
            UriRouteMatch<T, R> compiledMatch = findCompiled(request);
            if (compiledMatch != null) {
                return List.of(compiledMatch);
            }
        }
        List<UriRouteInfo<Object, Object>> routes = findInternal(request);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = toMatches(request.getPath(), routes);
        if (hasEngineSelectors && !uriRoutes.isEmpty()) {
            RouteMatchSelector selector = sameEngineSelector(uriRoutes);
            if (selector != null) {
                return select(selector, request, uriRoutes);
            }
        }
        if (uriRoutes.size() == 1) {
            return uriRoutes;
        }
        return resolveAmbiguity(request, uriRoutes);
    }

    private <T, R> List<UriRouteMatch<T, R>> resolveAmbiguity(HttpRequest<?> request,
                                                              List<UriRouteMatch<T, R>> uriRoutes) {
        // if there are multiple routes, try to resolve the ambiguity

        final Collection<MediaType> acceptedProducedTypes = request.accept();
        if (CollectionUtils.isNotEmpty(acceptedProducedTypes)) {
            // take the highest priority accepted type
            final MediaType mediaType = acceptedProducedTypes.iterator().next();
            var mostSpecific = new ArrayList<UriRouteMatch<T, R>>(uriRoutes.size());
            for (UriRouteMatch<T, R> routeMatch : uriRoutes) {
                if (routeMatch.getRouteInfo().explicitlyProduces(mediaType)) {
                    mostSpecific.add(routeMatch);
                }
            }
            if (!mostSpecific.isEmpty()) {
                uriRoutes = mostSpecific;
            }
        }
        boolean permitsBody = request.getMethod().permitsRequestBody();
        int routeCount = uriRoutes.size();
        if (routeCount > 1 && permitsBody) {
            final MediaType contentType = request.getContentType().orElse(MediaType.ALL_TYPE);
            var explicitlyConsumedRoutes = new ArrayList<UriRouteMatch<T, R>>(routeCount);
            var consumesRoutes = new ArrayList<UriRouteMatch<T, R>>(routeCount);

            for (UriRouteMatch<T, R> match : uriRoutes) {
                if (match.getRouteInfo().explicitlyConsumes(contentType)) {
                    explicitlyConsumedRoutes.add(match);
                }
                if (explicitlyConsumedRoutes.isEmpty()) {
                    consumesRoutes.add(match);
                }
            }

            uriRoutes = explicitlyConsumedRoutes.isEmpty() ? consumesRoutes : explicitlyConsumedRoutes;
        }

        /*
         * Any changes to the logic below may also need changes to {@link io.micronaut.http.uri.UriTemplate#compareTo(UriTemplate)}
         */
        routeCount = uriRoutes.size();
        if (routeCount > 1 && hasEngineOrders) {
            Comparator<ParsedRouteTemplate> engineOrder = sameEngineOrder(uriRoutes);
            if (engineOrder != null) {
                return mostSpecific(uriRoutes, engineOrder);
            }
            // the routes of an engine with its own order are not in the Micronaut order in the table
            uriRoutes = new ArrayList<>(uriRoutes);
            uriRoutes.sort(DefaultRouter::compareMicronaut);
        }
        if (routeCount > 1) {
            long variableCount = 0;
            long rawLength = 0;

            var closestMatches = new ArrayList<UriRouteMatch<T, R>>(routeCount);

            for (int i = 0; i < routeCount; i++) {
                UriRouteMatch<T, R> match = uriRoutes.get(i);
                UriRouteInfo<T, R> routeInfo = match.getRouteInfo();
                long variable;
                long raw;
                if (routeInfo instanceof DefaultUrlRouteInfo<?, ?> info && !info.isMicronautTemplate()) {
                    // the facts the engine of the template described for this policy
                    variable = info.getPathVariableCount();
                    raw = info.getRawLength();
                } else {
                    UriMatchTemplate template = routeInfo.getUriMatchTemplate();
                    variable = template.getPathVariableSegmentCount();
                    raw = template.getRawSegmentLength();
                }
                if (i == 0) {
                    variableCount = variable;
                    rawLength = raw;
                }
                if (variable > variableCount || raw < rawLength) {
                    break;
                }
                closestMatches.add(match);
            }
            uriRoutes = closestMatches.size() > 1 ? fewestPatternVariables(closestMatches) : closestMatches;
        }
        return uriRoutes;
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
        ParsedRouteTemplate template = engineTemplate(route);
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareMicronaut(UriRouteMatch<?, ?> a, UriRouteMatch<?, ?> b) {
        return ((UriRouteInfo) a.getRouteInfo()).compareTo((UriRouteInfo) b.getRouteInfo());
    }

    /**
     * @param matches The matches of a path
     * @return The order of the engine of the templates of all the matches, or {@code null} if they
     * are of different engines or their engine has no order of its own
     */
    private static @Nullable Comparator<ParsedRouteTemplate> sameEngineOrder(List<? extends UriRouteMatch<?, ?>> matches) {
        String engineId = null;
        for (UriRouteMatch<?, ?> match : matches) {
            ParsedRouteTemplate template = engineTemplate(match.getRouteInfo());
            if (template == null) {
                return null;
            }
            if (engineId == null) {
                engineId = template.engineId();
            } else if (!engineId.equals(template.engineId())) {
                return null;
            }
        }
        return engineId == null ? null : RouteTemplateEngines.defaults().comparator(engineId);
    }

    /**
     * The matches whose templates are the most specific by the order of their engine.
     *
     * @param matches The matches, of routes of one engine
     * @param order   The order of the engine
     * @return The most specific matches, in their order
     */
    private static <T, R> List<UriRouteMatch<T, R>> mostSpecific(List<UriRouteMatch<T, R>> matches, Comparator<ParsedRouteTemplate> order) {
        int size = matches.size();
        ParsedRouteTemplate[] templates = new ParsedRouteTemplate[size];
        ParsedRouteTemplate best = null;
        for (int i = 0; i < size; i++) {
            ParsedRouteTemplate template = Objects.requireNonNull(engineTemplate(matches.get(i).getRouteInfo()));
            templates[i] = template;
            if (best == null || order.compare(template, best) < 0) {
                best = template;
            }
        }
        var result = new ArrayList<UriRouteMatch<T, R>>(size);
        for (int i = 0; i < size; i++) {
            if (order.compare(templates[i], Objects.requireNonNull(best)) == 0) {
                result.add(matches.get(i));
            }
        }
        return result;
    }

    /**
     * The template of a route of an engine other than the Micronaut one, as the engine parsed it,
     * without building a route that is not built yet.
     *
     * @param route The route
     * @return The template, or {@code null} for a Micronaut template
     */
    static @Nullable ParsedRouteTemplate engineTemplate(UriRouteInfo<?, ?> route) {
        if (route instanceof DefaultUrlRouteInfo<?, ?> info) {
            return info.isMicronautTemplate() ? null : info.parsedTemplate();
        }
        if (route instanceof LazyUriRouteInfo lazy) {
            return lazy.isMicronautTemplate() ? null : lazy.parsedTemplate();
        }
        RouteTemplate template = route.getRouteTemplate();
        return template.isMicronaut() ? null : RouteTemplateEngines.defaults().parse(template);
    }

    /**
     * @param routes The routes of a method
     * @return Whether a route is of an engine with its own order
     */
    private static boolean hasEngineOrder(List<UriRouteInfo<Object, Object>> routes) {
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        for (UriRouteInfo<Object, Object> route : routes) {
            ParsedRouteTemplate template = engineTemplate(route);
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
            ParsedRouteTemplate template = engineTemplate(routes.get(i));
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
            engineRoutes.sort((a, b) -> order.compare(Objects.requireNonNull(engineTemplate(a)), Objects.requireNonNull(engineTemplate(b))));
            for (int i = 0; i < indexes.size(); i++) {
                routes.set(indexes.get(i), engineRoutes.get(i));
            }
        });
    }

    /**
     * The third key of specificity, among routes with the same literal length and number of
     * variables: fewer variables constrained by a regular expression is more specific, so
     * {@code /t/{id}} is selected over {@code /t/{id:.+}}. It only breaks ties.
     *
     * @param matches The equally specific matches by the first two keys
     * @return The matches with the fewest variables with a regular expression
     */
    private static <T, R> List<UriRouteMatch<T, R>> fewestPatternVariables(List<UriRouteMatch<T, R>> matches) {
        int size = matches.size();
        int[] counts = new int[size];
        int min = Integer.MAX_VALUE;
        for (int i = 0; i < size; i++) {
            counts[i] = patternVariableCount(matches.get(i).getRouteInfo());
            min = Math.min(min, counts[i]);
        }
        var result = new ArrayList<UriRouteMatch<T, R>>(size);
        for (int i = 0; i < size; i++) {
            if (counts[i] == min) {
                result.add(matches.get(i));
            }
        }
        return result;
    }

    private static int patternVariableCount(UriRouteInfo<?, ?> route) {
        if (route instanceof IndexedRoute indexed) {
            return indexed.getPatternVariableCount();
        }
        return new UriTemplateMatcher(route.getUriMatchTemplate().toString()).getPatternVariableCount();
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

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        for (UriRouteInfo<Object, Object> uriRouteInfo : methodRoutesByMethod.getOrDefault(httpMethod, EMPTY)) {
            Optional<UriRouteMatch<Object, Object>> match = uriRouteInfo.match(uri.toString());
            if (match.isPresent()) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(HttpStatus status) {
        for (StatusRouteInfo<Object, Object> statusRouteInfo : statusRoutes) {
            if (statusRouteInfo.originatingType() == null) {
                Optional<RouteMatch<Object>> match = statusRouteInfo.match(status);
                if (match.isPresent()) {
                    return (Optional) match;
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, HttpStatus status) {
        for (StatusRouteInfo<Object, Object> statusRouteInfo : statusRoutes) {
            Optional<RouteMatch<Object>> match = statusRouteInfo.match(originatingClass, status);
            if (match.isPresent()) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Class<?> originatingClass, Throwable error) {
        var matchedRoutes = new ArrayList<RouteMatch<R>>();
        for (ErrorRouteInfo<Object, Object> errorRouteInfo : errorRoutes) {
            Optional match = errorRouteInfo.match(originatingClass, error);
            match.ifPresent(m ->
                    matchedRoutes.add((RouteMatch<R>) m)
            );
        }
        return findRouteMatch(matchedRoutes, error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(
        Class<?> originatingClass,
        Throwable error,
        HttpRequest<?> request) {
        return findErrorRouteInternal(originatingClass, error, request);
    }

    private <R> Optional<RouteMatch<R>> findErrorRouteInternal(
        @Nullable Class<?> originatingClass,
        Throwable error, HttpRequest<?> request) {
        Collection<MediaType> accept = request.accept();
        final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(accept);
        if (hasAcceptHeader) {
            var matchedRoutes = new ArrayList<RouteMatch<R>>();
            for (ErrorRouteInfo<Object, Object> errorRoute : errorRoutes) {
                if (!errorRoute.doesProduce(accept)) {
                    continue;
                }
                if (!errorRoute.matching(request)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                final var match = (RouteMatch<R>) errorRoute.match(originatingClass, error).orElse(null);
                if (match != null) {
                    matchedRoutes.add(match);
                }
            }
            return findRouteMatch(matchedRoutes, error);
        } else {
            var producesAllMatchedRoutes = new ArrayList<RouteMatch<R>>(errorRoutes.length);
            var producesSpecificMatchedRoutes = new ArrayList<RouteMatch<R>>(errorRoutes.length);
            for (ErrorRouteInfo<Object, Object> errorRouteInfo : errorRoutes) {
                if (!errorRouteInfo.matching(request)) {
                    continue;
                }
                @SuppressWarnings("unchecked") final RouteMatch<R> match = (RouteMatch<R>) errorRouteInfo
                        .match(originatingClass, error).orElse(null);
                if (match != null) {
                    final List<MediaType> produces = match.getRouteInfo().getProduces();
                    if (CollectionUtils.isEmpty(produces) || produces.contains(MediaType.ALL_TYPE)) {
                        producesAllMatchedRoutes.add(match);
                    } else {
                        producesSpecificMatchedRoutes.add(match);
                    }
                }
            }
            if (producesAllMatchedRoutes.isEmpty()) {
                return findRouteMatch(producesSpecificMatchedRoutes, error);
            }
            return findRouteMatch(producesAllMatchedRoutes, error);
        }
    }

    @Override
    public <R> Optional<RouteMatch<R>> findErrorRoute(Throwable error, HttpRequest<?> request) {
        return findErrorRouteInternal(null, error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(
        Class<?> originatingClass,
        HttpStatus status,
        HttpRequest<?> request) {
        return findStatusInternal(originatingClass, status.getCode(), request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(HttpStatus status, HttpRequest<?> request) {
        return findStatusInternal(null, status.getCode(), request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(Class<?> originatingClass, int statusCode, HttpRequest<?> request) {
        return findStatusInternal(originatingClass, statusCode, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(int statusCode, HttpRequest<?> request) {
        return findStatusInternal(null, statusCode, request);
    }

    private <R> Optional<RouteMatch<R>> findStatusInternal(@Nullable Class<?> originatingClass, int status, HttpRequest<?> request) {
        Collection<MediaType> accept = request.accept();
        final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(accept);
        if (hasAcceptHeader) {
            for (StatusRouteInfo<Object, Object> statusRouteInfo : statusRoutes) {
                if (!statusRouteInfo.doesProduce(accept)) {
                    continue;
                }
                if (!statusRouteInfo.matching(request)) {
                    continue;
                }
                @SuppressWarnings("unchecked") final RouteMatch<R> match = (RouteMatch<R>) statusRouteInfo
                        .match(originatingClass, status).orElse(null);
                if (match != null) {
                    return Optional.of(match);
                }
            }
        } else {
            RouteMatch<R> firstMatch = null;
            for (StatusRouteInfo<Object, Object> statusRouteInfo : statusRoutes) {
                if (!statusRouteInfo.matching(request)) {
                    continue;
                }
                @SuppressWarnings("unchecked") final RouteMatch<R> match = (RouteMatch<R>) statusRouteInfo
                        .match(originatingClass, status).orElse(null);
                if (match != null) {
                    final List<MediaType> produces = match.getRouteInfo().getProduces();
                    if (CollectionUtils.isEmpty(produces) || produces.contains(MediaType.ALL_TYPE)) {
                        return Optional.of(match);
                    } else if (firstMatch == null) {
                        firstMatch = match;
                    }
                }
            }

            return Optional.ofNullable(firstMatch);

        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(Throwable error) {
        var matchedRoutes = new ArrayList<RouteMatch<R>>();
        for (ErrorRouteInfo<Object, Object> errorRouteInfo : errorRoutes) {
            if (errorRouteInfo.originatingType() == null) {
                Optional match = errorRouteInfo.match(error);
                match.ifPresent(m -> matchedRoutes.add((RouteMatch<R>) m));
            }
        }
        return findRouteMatch(matchedRoutes, error);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request) {
        if (preconditionFilterRoutes.isEmpty()) {
            // for perf, this needs to be placed in an ArrayList variable first
            @SuppressWarnings("UnnecessaryLocalVariable")
            ArrayList<GenericHttpFilter> always = alwaysMatchesHttpFilters.get();
            return always;
        }
        var httpFilters = new ArrayList<GenericHttpFilter>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
        httpFilters.addAll(alwaysMatchesHttpFilters.get());
        var routeMatch = RouteAttributes.getRouteMatch(request).orElse(null);
        HttpMethod method = request.getMethod();
        String path = request.getPath();
        for (FilterRoute filterRoute : preconditionFilterRoutes) {
            if (routeMatch != null) {
                if (!matchesFilterMatcher(filterRoute, routeMatch)) {
                    continue;
                }
            }
            filterRoute.match(method, path).ifPresent(httpFilters::add);
        }
        FilterRunner.sort(httpFilters);
        return Collections.unmodifiableList(httpFilters);
    }

    @Override
    public List<GenericHttpFilter> findFilters(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch) {
        List<GenericHttpFilter> routeFilters = routeMatch != null && routeMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> routeInfo
            ? routeInfo.routeFilters
            : List.of();
        if (!routeFilters.isEmpty()) {
            // the filters of the route run after the application's filters, closest to the route
            List<GenericHttpFilter> applicationFilters = findApplicationFilters(request, routeMatch);
            List<GenericHttpFilter> filters = new ArrayList<>(applicationFilters.size() + routeFilters.size());
            filters.addAll(applicationFilters);
            filters.addAll(routeFilters);
            return filters;
        }
        return findApplicationFilters(request, routeMatch);
    }

    private List<GenericHttpFilter> findApplicationFilters(HttpRequest<?> request, @Nullable RouteMatch<?> routeMatch) {
        if (preconditionFilterRoutes.isEmpty()) {
            // for perf, this needs to be placed in an ArrayList variable first
            @SuppressWarnings("UnnecessaryLocalVariable")
            ArrayList<GenericHttpFilter> always = alwaysMatchesHttpFilters.get();
            return always;
        }
        var httpFilters = new ArrayList<GenericHttpFilter>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
        httpFilters.addAll(alwaysMatchesHttpFilters.get());
        HttpMethod method = request.getMethod();
        String path = request.getPath();
        for (FilterRoute filterRoute : preconditionFilterRoutes) {
            if (routeMatch != null && !matchesFilterMatcher(filterRoute, routeMatch)) {
                continue;
            }
            filterRoute.match(method, path).ifPresent(httpFilters::add);
        }
        FilterRunner.sort(httpFilters);
        return Collections.unmodifiableList(httpFilters);
    }

    @Override
    public List<GenericHttpFilter> findPreMatchingFilters(HttpRequest<?> request) {
        if (preMatchingPreconditionFilterRoutes.isEmpty()) {
            // for perf, this needs to be placed in an ArrayList variable first
            @SuppressWarnings("UnnecessaryLocalVariable")
            ArrayList<GenericHttpFilter> always = preMatchingAlwaysMatchesHttpFilters.get();
            return always;
        }
        var httpFilters = new ArrayList<GenericHttpFilter>(preMatchingAlwaysMatchesFilterRoutes.size() + preMatchingPreconditionFilterRoutes.size());
        httpFilters.addAll(preMatchingAlwaysMatchesHttpFilters.get());
        HttpMethod method = request.getMethod();
        String path = request.getPath();
        for (FilterRoute filterRoute : preMatchingPreconditionFilterRoutes) {
            filterRoute.match(method, path).ifPresent(httpFilters::add);
        }
        FilterRunner.sort(httpFilters);
        return Collections.unmodifiableList(httpFilters);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> request) {
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        final String uriStr = uri.toString();
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            for (int candidate : index(entry.getKey()).candidates(uriStr)) {
                UriRouteInfo<Object, Object> route = routes[candidate];
                if (request != null) {
                    if (shouldSkipForPort(request, route)) {
                        continue;
                    }
                    if (!route.matching(request)) {
                        continue;
                    }
                }
                UriRouteMatch match = route.tryMatch(uriStr);
                if (match != null) {
                    matchedRoutes.add(match);
                }
            }
        }
        return matchedRoutes.stream();
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        String path = request.getPath();
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (Map.Entry<String, UriRouteInfo<Object, Object>[]> entry : allRoutesByMethod.entrySet()) {
            UriRouteInfo<Object, Object>[] routes = entry.getValue();
            for (int candidate : index(entry.getKey()).candidates(path)) {
                UriRouteInfo<Object, Object> route = routes[candidate];
                if (shouldSkipForPort(request, route)) {
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
        RouteLocator.Located located = locator.locate(request, locatorMatch);
        if (located != null) {
            result.addAll(located.wrap(located.router().<T, R>findAny(located.request())));
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
    private <T, R> @Nullable UriRouteMatch<T, R> findCompiled(HttpRequest<?> request) {
        HttpMethod method = request.getMethod();
        if (method == HttpMethod.CUSTOM) {
            return null;
        }
        String path = UriTemplateMatcher.normalizeForMatching(request.getPath());
        for (CompiledRoutes compiled : compiledRoutes) {
            String[] captured = new String[compiled.matcher.maxVariables()];
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
            if (!exclusive || !isAcceptable(request, route)) {
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
            result[i++] = new CompiledRoutes(routes.matcher, routes.byOrdinal, routes.headByOrdinal, exclusive, headExclusive);
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
        Optional<List<RouteTemplateSegment>> own = segments.computeIfAbsent(route, DefaultRouter::templateSegments);
        for (UriRouteInfo<Object, Object> other : allRoutesByMethod.getOrDefault(lazy.methodKey(), EMPTY)) {
            if (other != route && mayOverlap(own, segments.computeIfAbsent(other, DefaultRouter::templateSegments))) {
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
     * The same checks as {@link #findInternal(HttpRequest)} for one route.
     */
    private boolean isAcceptable(HttpRequest<?> request, UriRouteInfo<Object, Object> route) {
        if (shouldSkipForPort(request, route)) {
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

    private List<UriRouteInfo<Object, Object>> findInternal(HttpRequest<?> request) {
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
            if (shouldSkipForPort(request, route)) {
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

    private boolean shouldSkipForPort(HttpRequest<?> request, UriRouteInfo<Object, Object> route) {
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

    private UriRouteInfo<Object, Object>[] finalizeRoutes(List<UriRouteInfo<Object, Object>> routes) {
        Collections.sort(routes);
        if (hasEngineOrders) {
            orderByEngines(routes);
        }
        return routes.toArray(EMPTY);
    }

    private <T> Optional<RouteMatch<T>> findRouteMatch(List<RouteMatch<T>> matchedRoutes, Throwable error) {
        if (matchedRoutes.size() == 1) {
            return matchedRoutes.stream().findFirst();
        } else if (matchedRoutes.size() > 1) {
            int minCount = Integer.MAX_VALUE;

            Supplier<List<Class<?>>> hierarchySupplier = () -> ClassUtils.resolveHierarchy(error.getClass());
            Optional<RouteMatch<T>> match = Optional.empty();
            Class<?> errorClass = error.getClass();

            for (RouteMatch<T> errorMatch : matchedRoutes) {
                ErrorRouteInfo<T, ?> routeInfo = (ErrorRouteInfo<T, ?>) errorMatch.getRouteInfo();
                Class<?> exceptionType = routeInfo.exceptionType();
                if (exceptionType.equals(errorClass)) {
                    match = Optional.of(errorMatch);
                    break;
                } else {
                    List<Class<?>> hierarchy = hierarchySupplier.get();
                    //measures the distance in the hierarchy from the error and the route error type
                    int index = hierarchy.indexOf(exceptionType);
                    //the class closest in the hierarchy should be chosen
                    if (index > -1 && index < minCount) {
                        minCount = index;
                        match = Optional.of(errorMatch);
                    }
                }
            }

            return match;
        }
        return Optional.empty();
    }

    @Override
    public List<FilterEntry> resolveFilterEntries(RouteMatch<?> routeMatch) {
        if (preconditionFilterRoutes.isEmpty()) {
            return new ArrayList<>(alwaysMatchesFilterRoutes);
        }
        List<FilterEntry> filterEntries = new ArrayList<>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
        filterEntries.addAll(alwaysMatchesFilterRoutes);
        for (FilterRoute filterRoute : preconditionFilterRoutes) {
            if (!matchesFilterMatcher(filterRoute, routeMatch)) {
                filterEntries.add(filterRoute);
            }
        }
        filterEntries.sort(OrderUtil.COMPARATOR_ZERO);
        return Collections.unmodifiableList(filterEntries);
    }

    @Override
    public List<GenericHttpFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry> filterEntries) {
        var httpFilters = new ArrayList<GenericHttpFilter>(filterEntries.size());
        for (FilterEntry entry : filterEntries) {
            if (entry.hasMethods() && !entry.getFilterMethods().contains(request.getMethod())) {
                continue;
            }
            if (entry.hasPatterns()) {
                String path = request.getPath();
                String[] patterns = entry.getPatterns();
                FilterPatternStyle patternStyle = entry.getAnnotationMetadata()
                        .enumValue("patternStyle", FilterPatternStyle.class)
                        .orElse(FilterPatternStyle.ANT);
                boolean matches = true;
                for (String pattern : patterns) {
                    if (!matches) {
                        break;
                    }
                    matches = Filter.MATCH_ALL_PATTERN.equals(pattern) || patternStyle.getPathMatcher().matches(pattern, path);
                }
                if (!matches) {
                    continue;
                }
            }
            httpFilters.add(entry.getFilter());
        }
        httpFilters.sort(OrderUtil.COMPARATOR_ZERO);
        return Collections.unmodifiableList(httpFilters);
    }

    private boolean matchesFilterMatcher(FilterRoute filterRoute, RouteMatch<?> context) {
        String matchingAnnotation = filterRoute.findMatchingAnnotation();
        if (matchingAnnotation == null) {
            return true;
        }
        return context.getRouteInfo().getAnnotationMetadata().hasStereotype(matchingAnnotation);
    }

    /**
     * The routes bound to the declarations of a generated URL parser.
     *
     * @param matcher       The parser
     * @param byOrdinal     The bound routes, by the ordinal of their declarations
     * @param headByOrdinal The implicit {@code HEAD} routes of the bound {@code GET} routes, by ordinal
     * @param exclusive     Whether the route of an ordinal is the only route that can match its paths
     * @param headExclusive Whether the implicit {@code HEAD} route of an ordinal is the only route that can match its paths
     */
    private record CompiledRoutes(CompiledRouteMatcher matcher,
                                  UriRouteInfo<Object, Object>[] byOrdinal,
                                  UriRouteInfo<Object, Object>[] headByOrdinal,
                                  boolean[] exclusive,
                                  boolean[] headExclusive) {

        CompiledRoutes(CompiledRouteMatcher matcher, UriRouteInfo<Object, Object>[] byOrdinal, UriRouteInfo<Object, Object>[] headByOrdinal) {
            this(matcher, byOrdinal, headByOrdinal, new boolean[byOrdinal.length], new boolean[headByOrdinal.length]);
        }
    }

    /**
     * The routes of a route builder, or assembled without one.
     *
     * @param uriRoutes    The URI routes
     * @param statusRoutes The status routes
     * @param errorRoutes  The error routes
     * @param filterRoutes The filter routes
     * @param lazyRoutes   The routes built when first used
     * @param exposedPorts The exposed ports, read after the lazy routes: building one can expose a port
     */
    private record RouteSet(List<UriRoute> uriRoutes,
                            List<StatusRoute> statusRoutes,
                            List<ErrorRoute> errorRoutes,
                            List<FilterRoute> filterRoutes,
                            List<LazyUriRouteInfo> lazyRoutes,
                            Set<Integer> exposedPorts) {
    }
}
