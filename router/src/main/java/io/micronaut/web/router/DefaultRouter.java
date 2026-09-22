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
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.web.router.builder.CompiledRouteMatcher;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
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
    @Inject
    public DefaultRouter(Collection<RouteBuilder> builders) {
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
        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                HttpMethod httpMethod = route.getHttpMethod();
                UriRouteInfo<Object, Object> uriRouteInfo = route.toRouteInfo();
                if (httpMethod == HttpMethod.CUSTOM) {
                    String key = route.getHttpMethodName();
                    customRoutesByMethod.computeIfAbsent(key, x -> new ArrayList<>()).add(uriRouteInfo);
                } else {
                    routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
                }
            }

            for (StatusRoute statusRoute : builder.getStatusRoutes()) {
                StatusRouteInfo<Object, Object> routeInfo = statusRoute.toRouteInfo();
                if (statusRoutes.contains(routeInfo)) {
                    final StatusRouteInfo<Object, Object> existing = statusRoutes.stream().filter(r -> r.equals(routeInfo)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for http status [" + statusRoute.statusCode() + "]. New route: " + statusRoute + ". Existing: " + existing);
                }
                statusRoutes.add(routeInfo);
            }
            for (ErrorRoute errorRoute : builder.getErrorRoutes()) {
                ErrorRouteInfo<Object, Object> routeInfo = errorRoute.toRouteInfo();
                if (errorRoutes.contains(routeInfo)) {
                    final ErrorRouteInfo<Object, Object> existing = errorRoutes.stream().filter(r -> r.equals(routeInfo)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for error [" + errorRoute.exceptionType().getSimpleName() + "]. New route: " + errorRoute + ". Existing: " + existing);
                }
                errorRoutes.add(routeInfo);
            }
            for (FilterRoute filterRoute : builder.getFilterRoutes()) {
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
            if (builder instanceof DefaultRouteBuilder defaultBuilder) {
                // precompiled controller routes and declared handler routes, built when first used
                for (LazyUriRouteInfo uriRouteInfo : defaultBuilder.lazyRouteInfos()) {
                    addCompiled(compiled, uriRouteInfo);
                    HttpMethod httpMethod = uriRouteInfo.getHttpMethod();
                    if (httpMethod == HttpMethod.CUSTOM) {
                        customRoutesByMethod.computeIfAbsent(uriRouteInfo.methodKey(), x -> new ArrayList<>()).add(uriRouteInfo);
                    } else {
                        routesByMethod.computeIfAbsent(httpMethod, x -> new ArrayList<>()).add(uriRouteInfo);
                    }
                }
            }
            exposedPorts.addAll(builder.getExposedPorts());
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
        this.compiledRoutes = compiled.values().toArray(CompiledRoutes[]::new);
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
            throw new DuplicateRouteException(path, (List) uriRoutes);
        } else if (uriRoutes.size() == 1) {
            return uriRoutes.get(0);
        }
        return null;
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
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
            uriRoutes = closestMatches;
        }
        return uriRoutes;
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
        return matchedRoutes;
    }

    /**
     * Match the request with the generated URL parsers: the answered ordinal selects the bound
     * route directly, and the match is built from the captured path variables.
     *
     * @param request The request
     * @return The match, or {@code null} if no parser answers a bound route the request is acceptable for
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
            int ordinal = compiled.matcher.match(method, path, captured);
            if (ordinal >= 0 && ordinal < compiled.byOrdinal.length) {
                route = compiled.byOrdinal[ordinal];
            } else if (method == HttpMethod.HEAD) {
                // the implicit HEAD route of a GET route
                ordinal = compiled.matcher.match(HttpMethod.GET, path, captured);
                if (ordinal >= 0 && ordinal < compiled.headByOrdinal.length) {
                    route = compiled.headByOrdinal[ordinal];
                }
            }
            if (route == null || !isAcceptable(request, route)) {
                continue;
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
        RouteDeclaration declaration = route.declaration();
        if (declaration == null || !(declaration instanceof Enum<?> constant)) {
            return;
        }
        CompiledRouteMatcher matcher = declaration.matcher();
        if (matcher == null) {
            return;
        }
        int size = constant.getDeclaringClass().getEnumConstants().length;
        CompiledRoutes routes = compiled.computeIfAbsent(matcher, m -> new CompiledRoutes(m, new UriRouteInfo[size], new UriRouteInfo[size]));
        (route.isImplicitHead() ? routes.headByOrdinal : routes.byOrdinal)[constant.ordinal()] = route;
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
     */
    private record CompiledRoutes(CompiledRouteMatcher matcher,
                                  UriRouteInfo<Object, Object>[] byOrdinal,
                                  UriRouteInfo<Object, Object>[] headByOrdinal) {
    }
}
