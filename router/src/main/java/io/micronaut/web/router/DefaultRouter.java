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
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngine;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.spi.RouteCandidateSink;
import io.micronaut.web.router.spi.RoutePlan;
import io.micronaut.web.router.spi.RouteSlot;
import io.micronaut.web.router.spi.RouteMatchSelector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
import java.util.function.Supplier;
import java.util.stream.IntStream;
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
    private static final Logger LOG = LoggerFactory.getLogger(DefaultRouter.class);

    private final Map<HttpMethod, UriRouteInfo<Object, Object>[]> methodRoutesByMethod;
    private final Map<String, UriRouteInfo<Object, Object>[]> allRoutesByMethod;
    /**
     * The index of the routes of each method, by method name, see {@link #allRoutesByMethod}. A
     * route bound to a compiled slot of a route plan is not in the index: the parser of the plan
     * finds it.
     */
    private final Map<String, RouteIndex> indexesByMethod;
    /**
     * The route plans with the slots the routes of this router are bound to.
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
                // the routes of controllers with a route plan and declared routes, built when first used
                builder instanceof DefaultRouteBuilder defaultBuilder ? defaultBuilder.lazyRouteInfos() : List.of(),
                builder.getExposedPorts()));
        }
        for (AssembledRoutes routes : assembled) {
            RouteAssembly assembly = routes.routes();
            routeSets.add(new RouteSet(assembly.uriRoutes(), assembly.statusRoutes(), assembly.errorRoutes(), assembly.filterRoutes(),
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
        // the candidates of the given URI, matched with their templates
        String path = uri.toString();
        String[] paths = matchingPaths(path);
        List<Candidate> candidates = findInternal(request, path, paths);
        var matches = new ArrayList<UriRouteMatch<T, R>>(candidates.size());
        for (Candidate candidate : candidates) {
            UriRouteMatch match = candidate.route.tryMatch(pathFor(candidate.route, path, paths));
            if (match != null) {
                matches.add(match);
            }
        }
        return matches.stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        return this.<T, R>toCandidateMatches(path, paths, findInternal(request, path, paths)).stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        String path = uri.toString();
        return this.<T, R>toMatches(
            path,
            matchingPaths(path),
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
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        List<Candidate> routes = findInternal(request, path, paths);
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
            uriRoutes = resolveAmbiguity(request, uriRoutes);
        }
        if (uriRoutes.size() > 1) {
            uriRoutes = ImplicitHeadRoutes.preferExplicit(uriRoutes);
        }
        if (uriRoutes.size() > 1) {
            uriRoutes = RouteOrders.preferLowest(uriRoutes);
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
        String path = request.getPath();
        String[] paths = matchingPaths(path);
        List<Candidate> routes = findInternal(request, path, paths);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = toCandidateMatches(path, paths, routes);
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

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        String path = uri.toString();
        String[] paths = matchingPaths(path);
        for (UriRouteInfo<Object, Object> uriRouteInfo : methodRoutesByMethod.getOrDefault(httpMethod, EMPTY)) {
            Optional<UriRouteMatch<Object, Object>> match = uriRouteInfo.match(pathFor(uriRouteInfo, path, paths));
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
        return findErrorRoute(errorRoutes, originatingClass, error, request);
    }

    /**
     * The error route of the closest exception type among error routes, e.g. the global ones or
     * the ones of a group of handler routes.
     *
     * @param errorRoutes      The error routes
     * @param originatingClass The class the error routes are local to, or {@code null} for the global ones
     * @param error            The error
     * @param request          The request
     * @param <R>              The result type
     * @return The match of the error route, if one handles the error
     */
    static <R> Optional<RouteMatch<R>> findErrorRoute(ErrorRouteInfo<Object, Object>[] errorRoutes,
                                                      @Nullable Class<?> originatingClass,
                                                      Throwable error,
                                                      HttpRequest<?> request) {
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
        return findStatusRoute(statusRoutes, originatingClass, status, request);
    }

    /**
     * The status route of a status among status routes, e.g. the global ones or the ones of a
     * group of handler routes.
     *
     * @param statusRoutes     The status routes
     * @param originatingClass The class the status routes are local to, or {@code null} for the global ones
     * @param status           The status
     * @param request          The request
     * @param <R>              The result type
     * @return The match of the status route, if one handles the status
     */
    static <R> Optional<RouteMatch<R>> findStatusRoute(StatusRouteInfo<Object, Object>[] statusRoutes,
                                                       @Nullable Class<?> originatingClass,
                                                       int status,
                                                       HttpRequest<?> request) {
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
        List<GenericHttpFilter> routeFilters = routeFilters(routeMatch);
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

    /**
     * The filters of the matched route: of its groups and its own, and for a located route, first
     * the filters of the groups of the locator routes that located it.
     */
    private static List<GenericHttpFilter> routeFilters(@Nullable RouteMatch<?> routeMatch) {
        if (routeMatch == null || !(routeMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> routeInfo)) {
            return List.of();
        }
        if (routeMatch instanceof DefaultUriRouteMatch<?, ?> uriRouteMatch
            && uriRouteMatch.matchInfo() instanceof RouteLocator.LocatedUriMatchInfo located
            && !located.filters().isEmpty()) {
            List<GenericHttpFilter> filters = new ArrayList<>(located.filters().size() + routeInfo.routeFilters.size());
            filters.addAll(located.filters());
            filters.addAll(routeInfo.routeFilters);
            return filters;
        }
        return routeInfo.routeFilters;
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
        String[] paths = matchingPaths(uriStr);
        for (String methodKey : allRoutesByMethod.keySet()) {
            for (Candidate candidate : candidates(methodKey, uriStr, paths)) {
                UriRouteInfo<Object, Object> route = candidate.route;
                if (request != null) {
                    if (shouldSkipForPort(request, route)) {
                        continue;
                    }
                    if (!route.matching(request)) {
                        continue;
                    }
                }
                UriRouteMatch match = candidate.captured != null ? candidate.capturedMatch() : route.tryMatch(pathFor(route, uriStr, paths));
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
        String[] paths = matchingPaths(path);
        var matchedRoutes = new ArrayList<UriRouteMatch<T, R>>(5);
        for (String methodKey : allRoutesByMethod.keySet()) {
            for (Candidate candidate : candidates(methodKey, path, paths)) {
                UriRouteInfo<Object, Object> route = candidate.route;
                if (shouldSkipForPort(request, route)) {
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
     * The checks of the types of {@link #findInternal(HttpRequest, String, String[])}, for a
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
            result.addAll(located.wrap(located.router().<T, R>findAny(located.request())));
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

    private List<Candidate> findInternal(HttpRequest<?> request, String path, String @Nullable [] paths) {
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

    private UriRouteInfo<Object, Object>[] finalizeRoutes(List<UriRouteInfo<Object, Object>> routes) {
        Collections.sort(routes);
        if (hasEngineOrders) {
            orderByEngines(routes);
        }
        return routes.toArray(EMPTY);
    }

    private static <T> Optional<RouteMatch<T>> findRouteMatch(List<RouteMatch<T>> matchedRoutes, Throwable error) {
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
