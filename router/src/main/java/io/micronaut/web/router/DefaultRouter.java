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
import org.jspecify.annotations.Nullable;
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
import io.micronaut.web.router.filter.RouteMatchFilter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
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

    private static final String SNAPSHOT_ATTRIBUTE = "micronaut.router.route-source.snapshot";
    private static final Supplier<List<RouteSource>> NO_ROUTE_SOURCES = List::of;
    private static final Supplier<List<RouteMatchFilter>> NO_ROUTE_MATCH_FILTERS = List::of;

    /**
     * The routes of the application: the first tier.
     */
    private final UriRouteSet routes;
    /**
     * The route sources, in order: the tables they publish are the next tiers. Given by the
     * {@link RouteSourcesListener} when the router bean is created, before it is published, so
     * that a router that replaces this one and calls a public constructor has them too.
     */
    private Supplier<List<RouteSource>> routeSources;
    /**
     * The route match filters (e.g. versioning), applied to the candidates of every tier when
     * there are route sources.
     */
    private Supplier<List<RouteMatchFilter>> routeMatchFilters;
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
        this(builders, assembled, NO_ROUTE_SOURCES, NO_ROUTE_MATCH_FILTERS);
    }

    private DefaultRouter(Collection<RouteBuilder> builders,
                          List<AssembledRoutes> assembled,
                          Supplier<List<RouteSource>> routeSources,
                          Supplier<List<RouteMatchFilter>> routeMatchFilters) {
        this.routeSources = routeSources;
        this.routeMatchFilters = routeMatchFilters;
        Set<Integer> exposedPorts = new HashSet<>(5);
        UriRouteSet.Builder uriRoutes = new UriRouteSet.Builder();
        Set<StatusRouteInfo<Object, Object>> statusRoutes = new LinkedHashSet<>();
        Set<ErrorRouteInfo<Object, Object>> errorRoutes = new LinkedHashSet<>();
        alwaysMatchesFilterRoutes = new ArrayList<>(20);
        preconditionFilterRoutes = new ArrayList<>(20);
        preMatchingAlwaysMatchesFilterRoutes = new ArrayList<>(10);
        preMatchingPreconditionFilterRoutes = new ArrayList<>(10);
        List<RouteSet> routeSets = new ArrayList<>(builders.size() + assembled.size());
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
                uriRoutes.add(route);
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
                uriRoutes.add(uriRouteInfo);
            }
            exposedPorts.addAll(routeSet.exposedPorts());
        }

        if (CollectionUtils.isNotEmpty(exposedPorts)) {
            this.exposedPorts = exposedPorts;
        } else {
            this.exposedPorts = Collections.emptySet();
        }
        this.routes = uriRoutes.build();
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

    /**
     * Construct a new router for the given route builders and route sources.
     *
     * @param builders          The builders
     * @param routeSources      Supplies the route sources, in order
     * @param routeMatchFilters Supplies the route match filters, applied to every tier when there are route sources
     * @return The router
     */
    static DefaultRouter withRouteSources(Collection<RouteBuilder> builders,
                                          Supplier<List<RouteSource>> routeSources,
                                          Supplier<List<RouteMatchFilter>> routeMatchFilters) {
        return new DefaultRouter(builders, List.of(), routeSources, routeMatchFilters);
    }

    /**
     * Give the router the route sources, whose tables are the tiers after the routes of the
     * router. Called when the router bean is created, before it is published.
     *
     * @param routeSources      Supplies the route sources, in order
     * @param routeMatchFilters Supplies the route match filters, applied to every tier
     */
    void useRouteSources(Supplier<List<RouteSource>> routeSources, Supplier<List<RouteMatchFilter>> routeMatchFilters) {
        this.routeSources = routeSources;
        this.routeMatchFilters = routeMatchFilters;
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

    /**
     * The route sets of the tables of the route sources for a request: captured once and reused
     * for the rest of the request, so that every lookup of a request sees the same tables.
     *
     * @param request The request, or {@code null} to capture the tables for one lookup
     * @return The route sets of the tables that have routes, in the order of their sources
     */
    private List<UriRouteSet> tables(@Nullable HttpRequest<?> request) {
        if (request != null && request.getAttribute(SNAPSHOT_ATTRIBUTE).orElse(null) instanceof Snapshot snapshot) {
            return snapshot.tables;
        }
        List<RouteSource> sources = routeSources.get();
        List<UriRouteSet> tables = new ArrayList<>(sources.size());
        for (RouteSource source : sources) {
            RouteTable table = source.snapshot();
            if (table == null) {
                throw new IllegalStateException("Route source " + source + " returned no route table");
            }
            // sealed: the RouteTableFactory builds every table
            UriRouteSet tableRoutes = ((DefaultRouteTable) table).routes();
            if (!tableRoutes.isEmpty()) {
                tables.add(tableRoutes);
            }
        }
        if (request != null) {
            request.setAttribute(SNAPSHOT_ATTRIBUTE, new Snapshot(tables));
        }
        return tables;
    }

    /**
     * @return Whether there are route sources: then their tables are the tiers after the
     * application routes, and the route match filters apply to every tier
     */
    private boolean hasRouteSources() {
        return !routeSources.get().isEmpty();
    }

    /**
     * The route match filters for a request, combined with the given filter. They are applied to
     * the candidates of each tier before its ambiguity is resolved and before deciding whether it
     * has a match: a more specific route rejected by a filter does not hide a less specific one, a
     * tier whose routes are rejected falls through to the next tier, and the routes of a table are
     * filtered like the application routes. Applying a filter again, e.g. by a
     * {@link io.micronaut.web.router.filter.FilteredRouter} that decorates this router, is harmless.
     *
     * @param request The request, or {@code null}
     * @param filter  The filter to combine them with, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The combined filter, or {@code null} to accept every route
     */
    private <T, R> @Nullable Predicate<UriRouteMatch<T, R>> withRouteMatchFilters(@Nullable HttpRequest<?> request,
                                                                                @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (request == null) {
            return filter;
        }
        Predicate<UriRouteMatch<T, R>> predicate = filter;
        for (RouteMatchFilter routeMatchFilter : routeMatchFilters.get()) {
            Predicate<UriRouteMatch<T, R>> next = routeMatchFilter.filter(request);
            predicate = predicate == null ? next : predicate.and(next);
        }
        return predicate;
    }

    private static <T, R> List<UriRouteMatch<T, R>> filter(List<UriRouteMatch<T, R>> matches, @Nullable Predicate<UriRouteMatch<T, R>> predicate) {
        if (predicate == null || matches.isEmpty()) {
            return matches;
        }
        var filtered = new ArrayList<UriRouteMatch<T, R>>(matches.size());
        for (UriRouteMatch<T, R> match : matches) {
            if (predicate.test(match)) {
                filtered.add(match);
            }
        }
        return filtered;
    }

    /**
     * The matches of the application routes followed by the matches of the tables, each filtered
     * by the route match filters when there are route sources.
     *
     * @param request            The request, or {@code null}
     * @param applicationMatches The matches of the application routes
     * @param tableMatches       Finds the matches of a table
     * @param <T>                The target type
     * @param <R>                The result type
     * @return The matches of every tier
     */
    private <T, R> Stream<UriRouteMatch<T, R>> ofEveryTier(@Nullable HttpRequest<?> request,
                                                           List<UriRouteMatch<T, R>> applicationMatches,
                                                           Function<UriRouteSet, List<UriRouteMatch<T, R>>> tableMatches) {
        if (!hasRouteSources()) {
            return applicationMatches.stream();
        }
        Predicate<UriRouteMatch<T, R>> predicate = withRouteMatchFilters(request, null);
        Stream<UriRouteMatch<T, R>> matches = filter(applicationMatches, predicate).stream();
        for (UriRouteSet table : tables(request)) {
            matches = Stream.concat(matches, filter(tableMatches.apply(table), predicate).stream());
        }
        return matches;
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        String path = uri.toString();
        return ofEveryTier(request, routes.find(request, path, ports), table -> table.find(request, path, ports));
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        String path = request.getPath();
        return ofEveryTier(request, routes.find(request, path, ports), table -> table.find(request, path, ports));
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        String path = uri.toString();
        return ofEveryTier(context, routes.find(httpMethod, path), table -> table.find(httpMethod, path));
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        Stream<UriRouteInfo<?, ?>> uriRoutes = routes.uriRoutes();
        if (!hasRouteSources()) {
            return uriRoutes;
        }
        for (UriRouteSet table : tables(null)) {
            uriRoutes = Stream.concat(uriRoutes, table.uriRoutes().distinct());
        }
        return uriRoutes;
    }

    @Override
    public @Nullable <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        if (!hasRouteSources()) {
            return routes.findClosest(request, ports);
        }
        Predicate<UriRouteMatch<T, R>> filter = withRouteMatchFilters(request, null);
        UriRouteMatch<T, R> match = closest(routes, request, filter);
        if (match != null) {
            return match;
        }
        for (UriRouteSet table : tables(request)) {
            match = closest(table, request, filter);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    /**
     * The closest match of a tier.
     *
     * @param tier    The routes of the tier
     * @param request The request
     * @param filter  The filter of the candidates, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The match, or {@code null}
     * @throws DuplicateRouteException if several routes of the tier match equally closely
     */
    private <T, R> @Nullable UriRouteMatch<T, R> closest(UriRouteSet tier, HttpRequest<?> request, @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (filter == null) {
            return tier.findClosest(request, ports);
        }
        return UriRouteSet.closest(request.getPath(), tier.findAllClosest(request, filter, ports));
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        return findAllClosestOfTiers(request, null);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, Predicate<UriRouteMatch<T, R>> filter) {
        return findAllClosestOfTiers(request, filter);
    }

    /**
     * The closest matches of the first tier that has a match. The filter applies to the
     * candidates of a tier before its ambiguity is resolved, so a more specific route it rejects
     * does not hide a less specific one it accepts, and a tier whose routes it rejects falls
     * through to the next.
     *
     * @param request The request
     * @param filter  The filter of the candidates, or {@code null}
     * @param <T>     The target type
     * @param <R>     The result type
     * @return The closest matches
     */
    private <T, R> List<UriRouteMatch<T, R>> findAllClosestOfTiers(HttpRequest<?> request, @Nullable Predicate<UriRouteMatch<T, R>> filter) {
        if (!hasRouteSources()) {
            return routes.findAllClosest(request, filter, ports);
        }
        Predicate<UriRouteMatch<T, R>> predicate = withRouteMatchFilters(request, filter);
        List<UriRouteMatch<T, R>> matches = routes.findAllClosest(request, predicate, ports);
        if (!matches.isEmpty()) {
            return matches;
        }
        for (UriRouteSet table : tables(request)) {
            List<UriRouteMatch<T, R>> tableMatches = table.findAllClosest(request, predicate, ports);
            if (!tableMatches.isEmpty()) {
                return tableMatches;
            }
        }
        return matches;
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

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        String path = uri.toString();
        Optional<UriRouteMatch<T, R>> match = routes.route(httpMethod, path);
        if (match.isPresent() || !hasRouteSources()) {
            return match;
        }
        for (UriRouteSet table : tables(null)) {
            match = table.route(httpMethod, path);
            if (match.isPresent()) {
                return match;
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

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(CharSequence uri, @Nullable HttpRequest<?> request) {
        String path = uri.toString();
        return ofEveryTier(request, routes.findAny(path, request, ports), table -> table.findAny(path, request, ports));
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> matches = routes.findAny(request, ports);
        if (!hasRouteSources()) {
            return matches;
        }
        Predicate<UriRouteMatch<T, R>> predicate = withRouteMatchFilters(request, null);
        matches = filter(matches, predicate);
        List<UriRouteMatch<T, R>> all = null;
        for (UriRouteSet table : tables(request)) {
            List<UriRouteMatch<T, R>> tableMatches = filter(table.findAny(request, ports), predicate);
            if (!tableMatches.isEmpty()) {
                if (all == null) {
                    all = new ArrayList<>(matches);
                }
                all.addAll(tableMatches);
            }
        }
        return all == null ? matches : all;
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
     * The tables of one request.
     *
     * @param tables The route sets of the tables
     */
    private record Snapshot(List<UriRouteSet> tables) {
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
