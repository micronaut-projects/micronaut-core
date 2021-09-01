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
import io.micronaut.core.util.PathMatcher;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.*;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.filter.HttpServerFilterResolver;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.web.router.exceptions.RoutingException;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.net.URI;
import java.util.*;
import java.util.function.Predicate;
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

    private final Map<String, List<UriRoute>> routesByMethod = new HashMap<>();
    private final List<StatusRoute> statusRoutes = new ArrayList<>();
    private final List<ErrorRoute> errorRoutes = new ArrayList<>();
    private final Set<Integer> exposedPorts;
    private final List<FilterRoute> alwaysMatchesFilterRoutes = new ArrayList<>();
    private final List<FilterRoute> preconditionFilterRoutes = new ArrayList<>();
    private final Supplier<List<HttpFilter>> alwaysMatchesHttpFilters = SupplierUtil.memoized(() -> {
        if (alwaysMatchesFilterRoutes.isEmpty()) {
            return Collections.emptyList();
        }
        List<HttpFilter> httpFilters = new ArrayList<>(alwaysMatchesFilterRoutes.size());
        for (FilterRoute filterRoute : alwaysMatchesFilterRoutes) {
            httpFilters.add(filterRoute.getFilter());
        }
        httpFilters.sort(OrderUtil.COMPARATOR);
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
        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                String key = route.getHttpMethodName();
                routesByMethod.computeIfAbsent(key, x -> new ArrayList<>()).add(route);
            }

            for (StatusRoute statusRoute : builder.getStatusRoutes()) {
                if (statusRoutes.contains(statusRoute)) {
                    final StatusRoute existing = statusRoutes.stream().filter(r -> r.equals(statusRoute)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for http status [" + statusRoute.status() + "]. New route: " + statusRoute + ". Existing: " + existing);
                }
                this.statusRoutes.add(statusRoute);
            }
            for (ErrorRoute errorRoute : builder.getErrorRoutes()) {
                if (errorRoutes.contains(errorRoute)) {
                    final ErrorRoute existing = errorRoutes.stream().filter(r -> r.equals(errorRoute)).findFirst().orElse(null);
                    throw new RoutingException("Attempted to register multiple local routes for error [" + errorRoute.exceptionType().getSimpleName() + "]. New route: " + errorRoute + ". Existing: " + existing);
                }
                this.errorRoutes.add(errorRoute);
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
        Predicate<HttpRequest<?>> portMatches = (httpRequest -> ports.contains(httpRequest.getServerAddress().getPort()));
        for (List<UriRoute> routes : routesByMethod.values()) {
            for (int i = 0; i < routes.size(); i++) {
                UriRoute route = routes.get(i);
                if (route.getPort() == null) {
                    routes.set(i, route.where(portMatches));
                }
            }
        }
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request, @NonNull CharSequence uri) {
        return this.<T, R>find(request.getMethodName(), uri, null).stream();
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpRequest<?> request) {
        boolean permitsBody = HttpMethod.permitsRequestBody(request.getMethod());
        return this.<T, R>find(request, request.getPath())
                .filter(match -> match.test(request) && (!permitsBody || match.doesConsume(request.getContentType().orElse(null))));
    }

    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri, @Nullable HttpRequest<?> context) {
        return this.<T, R>find(httpMethod.name(), uri, null).stream();
    }

    @NonNull
    @Override
    public Stream<UriRoute> uriRoutes() {
        return routesByMethod.values().stream().flatMap(List::stream);
    }

    @NonNull
    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(@NonNull HttpRequest<?> request) {
        final HttpMethod httpMethod = request.getMethod();
        final MediaType contentType = request.getContentType().orElse(null);
        boolean permitsBody = HttpMethod.permitsRequestBody(httpMethod);
        final Collection<MediaType> acceptedProducedTypes = request.accept();
        List<UriRouteMatch<T, R>> uriRoutes = this.find(
                request.getMethodName(),
                request.getPath(),
                routeMatch -> routeMatch.test(request) && (!permitsBody || routeMatch.doesConsume(contentType)) && routeMatch.doesProduce(acceptedProducedTypes)
        );
        int routeCount = uriRoutes.size();
        if (routeCount <= 1) {
            return uriRoutes;
        }

        if (CollectionUtils.isNotEmpty(acceptedProducedTypes)) {
            // take the highest priority accepted type
            final MediaType mediaType = acceptedProducedTypes.iterator().next();
            List<UriRouteMatch<T, R>> mostSpecific = new ArrayList<>(uriRoutes.size());
            for (UriRouteMatch<T, R> routeMatch : uriRoutes) {
                if (routeMatch.explicitlyProduces(mediaType)) {
                    mostSpecific.add(routeMatch);
                }
            }
            if (!mostSpecific.isEmpty() || !acceptedProducedTypes.contains(MediaType.ALL_TYPE)) {
                uriRoutes = mostSpecific;
            }
        }
        routeCount = uriRoutes.size();
        if (routeCount > 1 && permitsBody) {

            List<UriRouteMatch<T, R>> explicitlyConsumedRoutes = new ArrayList<>(routeCount);
            List<UriRouteMatch<T, R>> consumesRoutes = new ArrayList<>(routeCount);

            for (UriRouteMatch<T, R> match: uriRoutes) {
                if (match.explicitlyConsumes(contentType != null ? contentType : MediaType.ALL_TYPE)) {
                    explicitlyConsumedRoutes.add(match);
                }
                if (explicitlyConsumedRoutes.isEmpty() && match.doesConsume(contentType)) {
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
                UriMatchTemplate template = match.getRoute().getUriMatchTemplate();
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

    @NonNull
    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(@NonNull HttpMethod httpMethod, @NonNull CharSequence uri) {
        List<UriRoute> routes = routesByMethod.getOrDefault(httpMethod.name(), Collections.emptyList());
        for (UriRoute uriRoute : routes) {
            Optional<UriRouteMatch> match = uriRoute.match(uri.toString());
            if (match.isPresent()) {
                return (Optional) match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull HttpStatus status) {
        for (StatusRoute statusRoute : statusRoutes) {
            if (statusRoute.originatingType() == null) {
                Optional<RouteMatch<R>> match = statusRoute.match(status);
                if (match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Class originatingClass, @NonNull HttpStatus status) {
        for (StatusRoute statusRoute : statusRoutes) {
            Optional<RouteMatch<R>> match = statusRoute.match(originatingClass, status);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@NonNull Class originatingClass, @NonNull Throwable error) {
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            Optional<RouteMatch<R>> match = errorRoute.match(originatingClass, error);
            match.ifPresent(m ->
                    matchedRoutes.put(errorRoute, m)
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
            Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
            for (ErrorRoute errorRoute : errorRoutes) {
                @SuppressWarnings("unchecked")
                final RouteMatch<R> match = (RouteMatch<R>) errorRoute
                        .match(originatingClass, error).orElse(null);
                if (match != null && match.doesProduce(accept)) {
                    matchedRoutes.put(errorRoute, match);
                }
            }
            return findRouteMatch(matchedRoutes, error);
        } else {
            Map<ErrorRoute, RouteMatch<R>> producesAllMatchedRoutes = new LinkedHashMap<>();
            Map<ErrorRoute, RouteMatch<R>> producesSpecificMatchedRoutes = new LinkedHashMap<>();
            for (ErrorRoute errorRoute : errorRoutes) {
                @SuppressWarnings("unchecked")
                final RouteMatch<R> match = (RouteMatch<R>) errorRoute
                        .match(originatingClass, error).orElse(null);
                if (match != null) {
                    final List<MediaType> produces = match.getProduces();
                    if (CollectionUtils.isEmpty(produces) || produces.contains(MediaType.ALL_TYPE)) {
                        producesAllMatchedRoutes.put(errorRoute, match);
                    } else {
                        producesSpecificMatchedRoutes.put(errorRoute, match);
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

            for (StatusRoute statusRoute : statusRoutes) {
                @SuppressWarnings("unchecked")
                final RouteMatch<R> match = (RouteMatch<R>) statusRoute
                        .match(originatingClass, status).orElse(null);
                if (match != null && match.doesProduce(accept)) {
                    return Optional.of(match);
                }
            }
        } else {
            RouteMatch<R> firstMatch = null;
            for (StatusRoute errorRoute : statusRoutes) {
                @SuppressWarnings("unchecked")
                final RouteMatch<R> match = (RouteMatch<R>) errorRoute
                        .match(originatingClass, status).orElse(null);
                if (match != null) {
                    final List<MediaType> produces = match.getProduces();
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
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            if (errorRoute.originatingType() == null) {
                Optional<RouteMatch<R>> match = errorRoute.match(error);
                match.ifPresent(m -> matchedRoutes.put(errorRoute, m));
            }
        }

        return findRouteMatch(matchedRoutes, error);
    }

    @NonNull
    @Override
    public List<HttpFilter> findFilters(@NonNull HttpRequest<?> request) {
        if (preconditionFilterRoutes.isEmpty()) {
            return alwaysMatchesHttpFilters.get();
        }
        List<HttpFilter> httpFilters = new ArrayList<>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
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
        httpFilters.sort(OrderUtil.COMPARATOR);
        return Collections.unmodifiableList(httpFilters);
    }

    @SuppressWarnings("unchecked")
    @NonNull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@NonNull CharSequence uri, @Nullable HttpRequest<?> context) {
        List matchedRoutes = new ArrayList<>(5);
        final String uriStr = uri.toString();
        for (List<UriRoute> routes : routesByMethod.values()) {
            for (UriRoute route : routes) {
                final UriRouteMatch match = route.match(uriStr).orElse(null);
                if (match != null && match.test(context)) {
                    matchedRoutes.add(match);
                }
            }
        }
        return matchedRoutes.stream();
    }

    private <T, R> List<UriRouteMatch<T, R>> find(String httpMethodName, CharSequence uri, @Nullable Predicate<UriRouteMatch> predicate) {
        List<UriRoute> routes = routesByMethod.getOrDefault(httpMethodName, Collections.emptyList());
        if (CollectionUtils.isNotEmpty(routes)) {
            final String uriStr = uri.toString();
            List<UriRouteMatch<T, R>> routeMatches = new LinkedList<>();
            for (UriRoute route : routes) {
                Optional<UriRouteMatch> match = route.match(uriStr);
                if (predicate != null) {
                    match = match.filter(predicate);
                }
                match.ifPresent(routeMatches::add);
            }
            return routeMatches;
        } else {
            //noinspection unchecked
            return Collections.emptyList();
        }
    }

    private UriRoute[] finalizeRoutes(List<UriRoute> routes) {
        Collections.sort(routes);
        return routes.toArray(new UriRoute[0]);
    }

    private <T> Optional<RouteMatch<T>> findRouteMatch(Map<ErrorRoute, RouteMatch<T>> matchedRoutes, Throwable error) {
        if (matchedRoutes.size() == 1) {
            return matchedRoutes.values().stream().findFirst();
        } else if (matchedRoutes.size() > 1) {
            int minCount = Integer.MAX_VALUE;

            Supplier<List<Class>> hierarchySupplier = () -> ClassUtils.resolveHierarchy(error.getClass());
            Optional<RouteMatch<T>> match = Optional.empty();
            Class errorClass = error.getClass();

            for (Map.Entry<ErrorRoute, RouteMatch<T>> entry: matchedRoutes.entrySet()) {
                Class exceptionType = entry.getKey().exceptionType();
                if (exceptionType.equals(errorClass)) {
                    match = Optional.of(entry.getValue());
                    break;
                } else {
                    List<Class> hierarchy = hierarchySupplier.get();
                    //measures the distance in the hierarchy from the error and the route error type
                    int index = hierarchy.indexOf(exceptionType);
                    //the class closest in the hierarchy should be chosen
                    if (index > -1 && index < minCount) {
                        minCount = index;
                        match = Optional.of(entry.getValue());
                    }
                }
            }

            return match;
        }
        return Optional.empty();
    }

    @Override
    public List<FilterEntry<HttpFilter>> resolveFilterEntries(RouteMatch<?> routeMatch) {
        if (preconditionFilterRoutes.isEmpty()) {
            return (List) alwaysMatchesFilterRoutes;
        }
        List<FilterEntry<HttpFilter>> filterEntries = new ArrayList<>(alwaysMatchesFilterRoutes.size() + preconditionFilterRoutes.size());
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
    public List<HttpFilter> resolveFilters(HttpRequest<?> request, List<FilterEntry<HttpFilter>> filterEntries) {
        List<HttpFilter> httpFilters = new ArrayList<>(filterEntries.size());
        for (FilterEntry<HttpFilter> entry : filterEntries) {
            if (entry.hasMethods() && !entry.getFilterMethods().contains(request.getMethod())) {
                continue;
            }
            if (entry.hasPatterns()) {
                String path = request.getPath();
                String[] patterns = entry.getPatterns();
                boolean matches = true;
                for (String pattern : patterns) {
                    if (!matches) {
                        break;
                    }
                    matches = Filter.MATCH_ALL_PATTERN.equals(pattern) || PathMatcher.ANT.matches(pattern, path);
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
                matches = context.getAnnotationMetadata().hasStereotype(filterAnnotation);
            }
        }
        return matches;
    }
}
