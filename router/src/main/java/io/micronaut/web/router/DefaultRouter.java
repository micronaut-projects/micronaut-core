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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpAttributes;
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
import io.micronaut.web.router.exceptions.RoutingException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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

    private final Map<String, UriRouteInfo<Object, Object>[]> routesByMethod;
    private final StatusRouteInfo<Object, Object>[] statusRoutes;
    private final ErrorRouteInfo<Object, Object>[] errorRoutes;
    private final Set<Integer> exposedPorts;
    @Nullable
    private Set<Integer> ports;
    private final List<FilterRoute> alwaysMatchesFilterRoutes = new ArrayList<>();
    private final List<FilterRoute> preconditionFilterRoutes = new ArrayList<>();
    private final Supplier<List<GenericHttpFilter>> alwaysMatchesHttpFilters = SupplierUtil.memoized(() -> {
        if (alwaysMatchesFilterRoutes.isEmpty()) {
            return Collections.emptyList();
        }
        List<GenericHttpFilter> httpFilters = new ArrayList<>(alwaysMatchesFilterRoutes.size());
        for (FilterRoute filterRoute : alwaysMatchesFilterRoutes) {
            httpFilters.add(filterRoute.getFilter());
        }
        FilterRunner.sort(httpFilters);
        return httpFilters;
    });

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
        List<FilterRoute> filterRoutes = new ArrayList<>();
        Map<String, List<UriRouteInfo<Object, Object>>> routesByMethod = CollectionUtils.newHashMap(HttpMethod.values().length);
        Set<StatusRouteInfo<Object, Object>> statusRoutes = new LinkedHashSet<>();
        Set<ErrorRouteInfo<Object, Object>> errorRoutes = new LinkedHashSet<>();
        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                String key = route.getHttpMethodName();
                UriRouteInfo<Object, Object> uriRouteInfo = route.toRouteInfo();
                routesByMethod.computeIfAbsent(key, x -> new ArrayList<>()).add(uriRouteInfo);
            }

            for (StatusRoute statusRoute : builder.getStatusRoutes()) {
                StatusRouteInfo<Object, Object> routeInfo = statusRoute.toRouteInfo();
                if (statusRoutes.contains(routeInfo)) {
                    final StatusRouteInfo<Object, Object> existing = statusRoutes.stream().filter(r -> r.equals(routeInfo)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for http status [" + statusRoute.status() + "]. New route: " + statusRoute + ". Existing: " + existing);
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
            filterRoutes.addAll(builder.getFilterRoutes());
            exposedPorts.addAll(builder.getExposedPorts());
        }

        if (CollectionUtils.isNotEmpty(exposedPorts)) {
            this.exposedPorts = exposedPorts;
        } else {
            this.exposedPorts = Collections.emptySet();
        }

        routesByMethod.values().forEach(this::finalizeRoutes);
        for (FilterRoute filterRoute : filterRoutes) {
            if (isMatchesAll(filterRoute)) {
                alwaysMatchesFilterRoutes.add(filterRoute);
            } else {
                preconditionFilterRoutes.add(filterRoute);
            }
        }
        Map<String, UriRouteInfo<Object, Object>[]> map = CollectionUtils.newHashMap(routesByMethod.size());
        for (Map.Entry<String, List<UriRouteInfo<Object, Object>>> e : routesByMethod.entrySet()) {
            map.put(e.getKey(), finalizeRoutes(e.getValue()));
        }
        this.routesByMethod = map;
        this.statusRoutes = statusRoutes.toArray(StatusRouteInfo[]::new);
        this.errorRoutes = errorRoutes.toArray(ErrorRouteInfo[]::new);
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

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request, @NonNull CharSequence uri) {
        return this.<T, R>toMatches(uri.toString(), findInternal(request)).stream();
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request) {
        return this.<T, R>toMatches(request.getPath(), findInternal(request)).stream();
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri, @Nullable HttpRequest<?> context) {
        return this.<T, R>toMatches(
                uri.toString(),
                routesByMethod.getOrDefault(httpMethod.name(), EMPTY)
        ).stream();
    }

    @NonNull
    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return routesByMethod.values().stream().flatMap(Arrays::stream);
    }

    @NonNull
    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(@NonNull HttpRequest<?> request) {
        List<UriRouteInfo<Object, Object>> routes = findInternal(request);
        if (routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<UriRouteMatch<T, R>> uriRoutes = toMatches(request.getPath(), routes);
        if (routes.size() == 1) {
            return uriRoutes;
        }

        // if there are multiple routes, try to resolve the ambiguity

        final Collection<MediaType> acceptedProducedTypes = request.accept();
        if (CollectionUtils.isNotEmpty(acceptedProducedTypes)) {
            // take the highest priority accepted type
            final MediaType mediaType = acceptedProducedTypes.iterator().next();
            List<UriRouteMatch<T, R>> mostSpecific = new ArrayList<>(routes.size());
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
            List<UriRouteMatch<T, R>> explicitlyConsumedRoutes = new ArrayList<>(routeCount);
            List<UriRouteMatch<T, R>> consumesRoutes = new ArrayList<>(routeCount);

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

            List<UriRouteMatch<T, R>> closestMatches = new ArrayList<>(routeCount);

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
        List<UriRouteMatch<T, R>> uriRoutes = new ArrayList<>(routes.size());
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
        List<UriRouteMatch<T, R>> uriRoutes = new ArrayList<>(routes.length);
        for (UriRouteInfo<Object, Object> route : routes) {
            UriRouteMatch match = route.tryMatch(path);
            if (match != null) {
                uriRoutes.add(match);
            }
        }
        return uriRoutes;
    }

    @NonNull
    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri) {
        for (UriRouteInfo<Object, Object> uriRouteInfo : routesByMethod.getOrDefault(httpMethod.name(), EMPTY)) {
            Optional<UriRouteMatch<Object, Object>> match = uriRouteInfo.match(uri.toString());
            if (match.isPresent()) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull HttpStatus status) {
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
    public <R> Optional<RouteMatch<R>> route(@NonNull Class<?> originatingClass, @NonNull HttpStatus status) {
        for (StatusRouteInfo<Object, Object> statusRouteInfo : statusRoutes) {
            Optional<RouteMatch<Object>> match = statusRouteInfo.match(originatingClass, status);
            if (match.isPresent()) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Class<?> originatingClass, @NonNull Throwable error) {
        List<RouteMatch<R>> matchedRoutes = new ArrayList<>();
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
            @NonNull Class<?> originatingClass,
            @NonNull Throwable error,
            HttpRequest<?> request) {
        return findErrorRouteInternal(originatingClass, error, request);
    }

    private <R> Optional<RouteMatch<R>> findErrorRouteInternal(
            @Nullable Class<?> originatingClass,
            @NonNull Throwable error, HttpRequest<?> request) {
        Collection<MediaType> accept = request.accept();
        final boolean hasAcceptHeader = CollectionUtils.isNotEmpty(accept);
        if (hasAcceptHeader) {
            List<RouteMatch<R>> matchedRoutes = new ArrayList<>();
            for (ErrorRouteInfo<Object, Object> errorRoute : errorRoutes) {
                if (!errorRoute.doesProduce(accept)) {
                    continue;
                }
                if (!errorRoute.matching(request)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                final RouteMatch<R> match = (RouteMatch<R>) errorRoute.match(originatingClass, error).orElse(null);
                if (match != null) {
                    matchedRoutes.add(match);
                }
            }
            return findRouteMatch(matchedRoutes, error);
        } else {
            List<RouteMatch<R>> producesAllMatchedRoutes = new ArrayList<>(errorRoutes.length);
            List<RouteMatch<R>> producesSpecificMatchedRoutes = new ArrayList<>(errorRoutes.length);
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
    public <R> Optional<RouteMatch<R>> findErrorRoute(@NonNull Throwable error, HttpRequest<?> request) {
        return findErrorRouteInternal(null, error, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(
            @NonNull Class<?> originatingClass,
            @NonNull HttpStatus status,
            HttpRequest<?> request) {
        return findStatusInternal(originatingClass, status, request);
    }

    @Override
    public <R> Optional<RouteMatch<R>> findStatusRoute(@NonNull HttpStatus status, HttpRequest<?> request) {
        return findStatusInternal(null, status, request);
    }

    private <R> Optional<RouteMatch<R>> findStatusInternal(@Nullable Class<?> originatingClass, @NonNull HttpStatus status, HttpRequest<?> request) {
        Collection<MediaType> accept =
                request.accept();
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
    public <R> Optional<RouteMatch<R>> route(@NonNull Throwable error) {
        List<RouteMatch<R>> matchedRoutes = new ArrayList<>();
        for (ErrorRouteInfo<Object, Object> errorRouteInfo : errorRoutes) {
            if (errorRouteInfo.originatingType() == null) {
                Optional match = errorRouteInfo.match(error);
                match.ifPresent(m -> matchedRoutes.add((RouteMatch<R>) m));
            }
        }
        return findRouteMatch(matchedRoutes, error);
    }

    @NonNull
    @Override
    public List<GenericHttpFilter> findFilters(@NonNull HttpRequest<?> request) {
        if (preconditionFilterRoutes.isEmpty()) {
            return alwaysMatchesHttpFilters.get();
        }
        List<GenericHttpFilter> httpFilters = new ArrayList<>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
        httpFilters.addAll(alwaysMatchesHttpFilters.get());
        RouteMatch routeMatch = (RouteMatch) request.getAttribute(HttpAttributes.ROUTE_MATCH).filter(o -> o instanceof RouteMatch).orElse(null);
        HttpMethod method = request.getMethod();
        URI uri = request.getUri();
        for (FilterRoute filterRoute : preconditionFilterRoutes) {
            if (routeMatch != null) {
                if (!matchesFilterMatcher(filterRoute, routeMatch)) {
                    continue;
                }
            }
            filterRoute.match(method, uri).ifPresent(httpFilters::add);
        }
        FilterRunner.sort(httpFilters);
        return Collections.unmodifiableList(httpFilters);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@NonNull CharSequence uri, @Nullable HttpRequest<?> request) {
        List matchedRoutes = new ArrayList<>(5);
        final String uriStr = uri.toString();
        for (UriRouteInfo<Object, Object>[] routes : routesByMethod.values()) {
            for (UriRouteInfo<Object, Object> route : routes) {
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
        List matchedRoutes = new ArrayList<>(5);
        for (UriRouteInfo<Object, Object>[] routes : routesByMethod.values()) {
            for (UriRouteInfo<Object, Object> route : routes) {
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

    private List<UriRouteInfo<Object, Object>> findInternal(HttpRequest<?> request) {
        String httpMethodName = request.getMethodName();
        boolean permitsBody = request.getMethod().permitsRequestBody();
        final Collection<MediaType> acceptedProducedTypes = request.accept();
        UriRouteInfo<Object, Object>[] routes = routesByMethod.getOrDefault(httpMethodName, EMPTY);
        if (routes.length == 0) {
            return Collections.emptyList();
        }
        List<UriRouteInfo<Object, Object>> result = new ArrayList<>(routes.length);
        for (UriRouteInfo<Object, Object> route : routes) {
            if (shouldSkipForPort(request, route)) {
                continue;
            }
            if (permitsBody) {
                if (!route.isPermitsRequestBody()) {
                    continue;
                }
                if (!route.doesConsume(request.getContentType().orElse(null))) {
                    continue;
                }
            }
            if (!route.doesProduce(acceptedProducedTypes)) {
                continue;
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
        if (!ports.contains(request.getServerAddress().getPort())) {
            return true;
        }
        return false;
    }

    private UriRouteInfo<Object, Object>[] finalizeRoutes(List<UriRouteInfo<Object, Object>> routes) {
        Collections.sort(routes);
        return routes.toArray(new UriRouteInfo[0]);
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
        filterEntries.sort(OrderUtil.COMPARATOR);
        return Collections.unmodifiableList(filterEntries);
    }

    @Override
    public List<GenericHttpFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry> filterEntries) {
        List<GenericHttpFilter> httpFilters = new ArrayList<>(filterEntries.size());
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
        httpFilters.sort(OrderUtil.COMPARATOR);
        return Collections.unmodifiableList(httpFilters);
    }

    private boolean matchesFilterMatcher(FilterRoute filterRoute, RouteMatch<?> context) {
        AnnotationMetadata annotationMetadata = filterRoute.getAnnotationMetadata();
        boolean matches = !annotationMetadata.hasStereotype(FilterMatcher.NAME);
        if (!matches) {
            String filterAnnotation = annotationMetadata.getAnnotationNameByStereotype(FilterMatcher.NAME).orElse(null);
            if (filterAnnotation != null) {
                matches = context.getRouteInfo().getAnnotationMetadata().hasStereotype(filterAnnotation);
            }
        }
        return matches;
    }
}
