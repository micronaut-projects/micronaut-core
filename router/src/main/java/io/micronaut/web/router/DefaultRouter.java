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
import io.micronaut.core.annotation.Internal;
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
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
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
    private static final String VARIABLE_SEGMENT = "{}";
    private static final String ANY_SEGMENTS = "{*}";
    private static final Pattern SIMPLE_VARIABLE = Pattern.compile("\\{\\w[\\w-]*}");

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
                // declared routes, built when first used
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
        if (uriRoutes.size() == 1) {
            Object obj = uriRoutes.get(0);
            // type pollution avoidance (should be covered by type pollution test)
            return obj instanceof DefaultUriRouteMatch<?, ?> def ? (DefaultUriRouteMatch<T, R>) def : (UriRouteMatch<T, R>) obj;
        }
        uriRoutes = resolveAmbiguity(request, uriRoutes);
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
        return locate(request, findAllClosestRoutes(request, null), null);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, Predicate<UriRouteMatch<T, R>> filter) {
        return locate(request, findAllClosestRoutes(request, filter), filter);
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
                    ? located.router().findAllClosest(located.request())
                    // the filter sees the match of the request, as it does for the other routes
                    : located.router().findAllClosest(located.request(), targetMatch -> filter.test(located.wrap(targetMatch)));
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
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestRoutes(HttpRequest<?> request, @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (compiledRoutes.length != 0) {
            UriRouteMatch<T, R> compiledMatch = findCompiled(request);
            // a compiled match the filter rejects can hide a less specific route it accepts
            if (compiledMatch != null && (filter == null || accepts(filter, compiledMatch))) {
                return List.of(compiledMatch);
            }
        }
        List<UriRouteInfo<Object, Object>> routes = findInternal(request);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = filter(toMatches(request.getPath(), routes), filter);
        if (uriRoutes.size() < 2) {
            return uriRoutes;
        }
        return resolveAmbiguity(request, uriRoutes);
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
     * Narrows the given route matches for a request down to the closest ones.
     *
     * @param request   The request
     * @param uriRoutes The route matches of the request
     * @param <T>       The target type
     * @param <R>       The result type
     * @return The closest matches
     * @since 5.2.2
     */
    @Internal
    public static <T, R> List<UriRouteMatch<T, R>> resolveAmbiguity(HttpRequest<?> request,
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
        if (routeCount > 1) {
            long variableCount = 0;
            long rawLength = 0;

            var closestMatches = new ArrayList<UriRouteMatch<T, R>>(routeCount);

            for (int i = 0; i < routeCount; i++) {
                UriRouteMatch<T, R> match = uriRoutes.get(i);
                UriMatchTemplate template = match.getRouteInfo().getUriMatchTemplate();
                long variable = template.getPathVariableSegmentCount();
                long raw = template.getRawSegmentLength();
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
        Map<UriRouteInfo<Object, Object>, String[]> segments = new IdentityHashMap<>();
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

    private boolean isExclusive(@Nullable UriRouteInfo<Object, Object> route, Map<UriRouteInfo<Object, Object>, String[]> segments) {
        if (!(route instanceof LazyUriRouteInfo lazy)) {
            return false;
        }
        String[] own = segments.computeIfAbsent(route, DefaultRouter::templateSegments);
        for (UriRouteInfo<Object, Object> other : allRoutesByMethod.getOrDefault(lazy.methodKey(), EMPTY)) {
            if (other != route && mayOverlap(own, segments.computeIfAbsent(other, DefaultRouter::templateSegments))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The path segments of a route's template: a literal, {@link #VARIABLE_SEGMENT} for a
     * variable that is a whole segment, or {@link #ANY_SEGMENTS} for anything else, which may
     * match any number of segments, e.g. {@code {+path}}, {@code {/id}} or a regular expression.
     */
    private static String[] templateSegments(UriRouteInfo<Object, Object> route) {
        String template = route instanceof LazyUriRouteInfo lazy ? lazy.uriTemplate() : route.getUriMatchTemplate().toString();
        int query = template.indexOf("{?");
        if (query >= 0) {
            template = template.substring(0, query);
        }
        List<String> result = new ArrayList<>();
        for (String segment : template.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.indexOf('{') < 0) {
                result.add(segment);
            } else if (SIMPLE_VARIABLE.matcher(segment).matches()) {
                result.add(VARIABLE_SEGMENT);
            } else {
                result.add(ANY_SEGMENTS);
            }
        }
        return result.toArray(String[]::new);
    }

    /**
     * Whether two templates may match the same path. Only {@code false} is certain.
     */
    private static boolean mayOverlap(String[] a, String[] b) {
        int common = Math.min(a.length, b.length);
        for (int i = 0; i < common; i++) {
            String x = a[i];
            String y = b[i];
            if (ANY_SEGMENTS.equals(x) || ANY_SEGMENTS.equals(y)) {
                return true;
            }
            if (!VARIABLE_SEGMENT.equals(x) && !VARIABLE_SEGMENT.equals(y) && !x.equals(y)) {
                return false;
            }
        }
        // the longer template matches the same paths only if its remaining segments can be empty
        String[] longer = a.length > b.length ? a : b;
        for (int i = common; i < longer.length; i++) {
            if (!ANY_SEGMENTS.equals(longer[i])) {
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
