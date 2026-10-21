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
import io.micronaut.web.router.exceptions.DuplicateRouteException;
import io.micronaut.web.router.exceptions.RoutingException;
import io.micronaut.web.router.spi.RouteMatchSelector;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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

    /**
     * The URI routes of the application.
     */
    private final UriRouteSet routes;
    private final StatusRouteInfo<Object, Object>[] statusRoutes;
    private final ErrorRouteInfo<Object, Object>[] errorRoutes;
    /**
     * The types that have local error routes, or {@code null} if unknown: no local error route
     * is looked up for another type, e.g. the class of a handler function.
     */
    private final @Nullable Set<Class<?>> localErrorRouteTypes;
    private final Set<Integer> exposedPorts;
    /**
     * The default ports: an unmodifiable snapshot, replaced as a whole.
     */
    private final AtomicReference<@Nullable Set<Integer>> ports = new AtomicReference<>();
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
     * Construct a new router for the given route builders. The routes of the
     * {@link io.micronaut.web.router.builder.HttpRoutes} beans reach the router as a route builder
     * bean too, so a router that replaces this one and calls this constructor has them.
     *
     * @param builders The builders
     */
    @Inject
    public DefaultRouter(Collection<RouteBuilder> builders) {
        this(builders, List.of());
    }

    /**
     * Construct a new router for the given route builders and the routes assembled without one.
     *
     * @param builders  The builders
     * @param assembled The routes assembled without a builder
     * @since 5.3.0
     */
    @Internal
    public DefaultRouter(Collection<RouteBuilder> builders, List<AssembledRoutes> assembled) {
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
                builder.getExposedPorts()));
        }
        for (AssembledRoutes assembledRoutes : assembled) {
            RouteAssembly assembly = assembledRoutes.routes();
            routeSets.add(new RouteSet(assembly.uriRoutes(), assembly.statusRoutes(), assembly.errorRoutes(), assembly.filterRoutes(),
                assembly.exposedPorts()));
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
        this.localErrorRouteTypes = localErrorRouteTypes(this.errorRoutes);
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
        this.ports.set(Collections.unmodifiableSet(new HashSet<>(ports)));
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request, CharSequence uri) {
        return routes.<T, R>find(request, uri.toString(), ports.get()).stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpRequest<?> request) {
        return routes.<T, R>find(request, request.getPath(), ports.get()).stream();
    }

    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(HttpMethod httpMethod, CharSequence uri, @Nullable HttpRequest<?> context) {
        return routes.<T, R>find(httpMethod, uri.toString()).stream();
    }

    @Override
    public Stream<UriRouteInfo<?, ?>> uriRoutes() {
        return routes.uriRoutes();
    }

    @Override
    public @Nullable <T, R> UriRouteMatch<T, R> findClosest(HttpRequest<?> request) throws DuplicateRouteException {
        return routes.findClosest(request, ports.get());
    }

    @Override
    public boolean hasBodyConditions() {
        return routes.hasBodyConditions();
    }

    @Override
    public <T, R> RouteResolution<T, R> resolve(HttpRequest<?> request,
                                                @Nullable Predicate<UriRouteMatch<T, R>> filter) throws DuplicateRouteException {
        return routes.resolve(request, ports.get(), filter);
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request) {
        return routes.findAllClosest(request, ports.get());
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(HttpRequest<?> request, Predicate<UriRouteMatch<T, R>> filter) {
        return routes.findAllClosest(request, filter, ports.get());
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
        // the matches may have a selector with its own order, see RouteMatchSelector#order()
        return resolveAmbiguity(request, uriRoutes, hasSelectorOrder(request, uriRoutes));
    }

    /**
     * Narrows the given route matches for a request down to the closest ones, as when the
     * request is routed: when the matches all have a route selector, it selects among them,
     * otherwise their ambiguity is resolved, see {@link #resolveAmbiguity(HttpRequest, List)}.
     *
     * @param request   The request
     * @param uriRoutes The route matches of the request, e.g. of the WebSocket routes of a path
     * @param <T>       The target type
     * @param <R>       The result type
     * @return The closest matches
     * @since 5.3.0
     */
    @Internal
    public static <T, R> List<UriRouteMatch<T, R>> selectClosest(HttpRequest<?> request, List<UriRouteMatch<T, R>> uriRoutes) {
        return UriRouteSet.selectClosest(request, uriRoutes);
    }

    /**
     * Narrows the given route matches for a request down to the closest ones.
     *
     * @param request     The request
     * @param uriRoutes   The route matches of the request
     * @param selectorOrders Whether a match may have a route selector with its own order
     * @param <T>         The target type
     * @param <R>         The result type
     * @return The closest matches
     */
    static <T, R> List<UriRouteMatch<T, R>> resolveAmbiguity(HttpRequest<?> request,
                                                             List<UriRouteMatch<T, R>> uriRoutes,
                                                             boolean selectorOrders) {
        // if there are multiple routes, try to resolve the ambiguity
        if (selectorOrders && uriRoutes.size() > 1) {
            // the routes of one selector, e.g. locator routes: its order instead of the Micronaut
            // policy, then the media types narrow the routes it orders equally
            Comparator<UriRouteInfo<?, ?>> selectorOrder = sameSelectorOrder(request, uriRoutes);
            if (selectorOrder != null) {
                List<UriRouteMatch<T, R>> ordered = mostSpecific(request, uriRoutes, selectorOrder);
                return ordered.size() > 1 ? byMediaTypes(request, ordered) : ordered;
            }
        }
        uriRoutes = byMediaTypes(request, uriRoutes);

        /*
         * Any changes to the logic below may also need changes to {@link io.micronaut.http.uri.UriTemplate#compareTo(UriTemplate)}
         */
        int routeCount = uriRoutes.size();
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
                    // the facts the template describes for this policy
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
            uriRoutes = closestMatches;
        }
        return uriRoutes;
    }

    /**
     * Narrows matches by the media types: the routes that explicitly produce the most preferred
     * accepted type, then, for a request with a body, the routes that explicitly consume its type.
     *
     * @param request   The request
     * @param uriRoutes The matches
     * @param <T>       The target type
     * @param <R>       The result type
     * @return The narrowed matches
     */
    private static <T, R> List<UriRouteMatch<T, R>> byMediaTypes(HttpRequest<?> request, List<UriRouteMatch<T, R>> uriRoutes) {
        final Collection<MediaType> acceptedProducedTypes = request.accept();
        if (CollectionUtils.isNotEmpty(acceptedProducedTypes)) {
            // take the highest priority accepted type, a type of the quality 0 is not acceptable
            final MediaType mediaType = preferredAcceptedType(acceptedProducedTypes);
            if (mediaType != null) {
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
        return uriRoutes;
    }

    private static @Nullable MediaType preferredAcceptedType(Collection<MediaType> acceptedTypes) {
        for (MediaType acceptedType : acceptedTypes) {
            if (!AcceptedMediaTypes.isRejected(acceptedType)) {
                return acceptedType;
            }
        }
        return null;
    }

    /**
     * @param request The request
     * @param matches The matches of a path
     * @return Whether a match has a route selector with its own order
     */
    static boolean hasSelectorOrder(HttpRequest<?> request, List<? extends UriRouteMatch<?, ?>> matches) {
        for (UriRouteMatch<?, ?> match : matches) {
            if (selectorOrder(request, match.getRouteInfo()) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param request The request
     * @param matches The matches of a path
     * @return The order of the route selector of all the matches, or {@code null} if they have
     * different selectors, none, or a selector without an order of its own
     */
    static @Nullable Comparator<UriRouteInfo<?, ?>> sameSelectorOrder(HttpRequest<?> request, List<? extends UriRouteMatch<?, ?>> matches) {
        RouteMatchSelector selector = null;
        Comparator<UriRouteInfo<?, ?>> order = null;
        for (UriRouteMatch<?, ?> match : matches) {
            UriRouteInfo<?, ?> route = RouteLocator.atLocationOf(request, match.getRouteInfo());
            RouteMatchSelector own = selector(route);
            if (own == null || selector != null && own != selector) {
                return null;
            }
            selector = own;
            order = selectorOrder(route);
        }
        return order;
    }

    /**
     * The matches whose routes are the most specific by the order of their selector, which
     * compares the routes with the settings they inherit at the location of the request.
     *
     * @param request The request
     * @param matches The matches, of the routes of one selector
     * @param order   The order of the selector
     * @return The most specific matches, in their order
     */
    static <T, R> List<UriRouteMatch<T, R>> mostSpecific(HttpRequest<?> request, List<UriRouteMatch<T, R>> matches, Comparator<UriRouteInfo<?, ?>> order) {
        int count = matches.size();
        var routes = new UriRouteInfo<?, ?>[count];
        UriRouteInfo<?, ?> best = null;
        for (int i = 0; i < count; i++) {
            UriRouteInfo<?, ?> route = RouteLocator.atLocationOf(request, matches.get(i).getRouteInfo());
            routes[i] = route;
            if (best == null || order.compare(route, best) < 0) {
                best = route;
            }
        }
        var result = new ArrayList<UriRouteMatch<T, R>>(count);
        for (int i = 0; i < count; i++) {
            if (order.compare(routes[i], Objects.requireNonNull(best)) == 0) {
                result.add(matches.get(i));
            }
        }
        return result;
    }

    /**
     * @param route A route
     * @return The route selector of the route, or {@code null}
     */
    static @Nullable RouteMatchSelector selector(UriRouteInfo<?, ?> route) {
        return route instanceof DefaultUrlRouteInfo<?, ?> info ? info.routeMatchSelector() : null;
    }

    /**
     * @param request The request
     * @param route   A route
     * @return The route selector of the route at the location of the request, see
     * {@link RouteLocator#atLocation}, or {@code null}
     */
    static @Nullable RouteMatchSelector selector(HttpRequest<?> request, UriRouteInfo<?, ?> route) {
        return selector(RouteLocator.atLocationOf(request, route));
    }

    /**
     * @param route A route
     * @return The order of the route selector of the route, read once when it was built, or {@code null}
     */
    static @Nullable Comparator<UriRouteInfo<?, ?>> selectorOrder(UriRouteInfo<?, ?> route) {
        return route instanceof DefaultUrlRouteInfo<?, ?> info ? info.routeMatchSelectorOrder() : null;
    }

    /**
     * @param request The request
     * @param route   A route
     * @return The order of the route selector of the route at the location of the request, or {@code null}
     */
    static @Nullable Comparator<UriRouteInfo<?, ?>> selectorOrder(HttpRequest<?> request, UriRouteInfo<?, ?> route) {
        return selectorOrder(RouteLocator.atLocationOf(request, route));
    }

    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(HttpMethod httpMethod, CharSequence uri) {
        return routes.route(httpMethod, uri.toString());
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
        if (originatingClass != null && localErrorRouteTypes != null && !localErrorRouteTypes.contains(originatingClass)) {
            // a local error route only matches the type it is local to
            return Optional.empty();
        }
        return findErrorRouteInternal(originatingClass, error, request);
    }

    /**
     * @param errorRoutes The error routes
     * @return The types the error routes are local to, or {@code null} if one of them is not a {@link DefaultErrorRouteInfo}
     */
    private static @Nullable Set<Class<?>> localErrorRouteTypes(ErrorRouteInfo<Object, Object>[] errorRoutes) {
        Set<Class<?>> types = new HashSet<>();
        for (ErrorRouteInfo<Object, Object> errorRoute : errorRoutes) {
            if (!(errorRoute instanceof DefaultErrorRouteInfo)) {
                return null;
            }
            Class<?> originatingType = errorRoute.originatingType();
            if (originatingType != null) {
                types.add(originatingType);
            }
        }
        return types;
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
     * The filters of the matched route: of its groups and its own, and for a route resolved by a
     * {@link DynamicRouteTarget}, first the filters the resolution added.
     */
    private static List<GenericHttpFilter> routeFilters(@Nullable RouteMatch<?> routeMatch) {
        if (routeMatch == null || !(routeMatch.getRouteInfo() instanceof DefaultUrlRouteInfo<?, ?> routeInfo)) {
            return List.of();
        }
        if (routeMatch instanceof DefaultUriRouteMatch<?, ?> uriRouteMatch
            && uriRouteMatch.matchInfo() instanceof DynamicRouteTarget.ResolvedMatchInfo resolved
            && !resolved.filters().isEmpty()) {
            List<GenericHttpFilter> filters = new ArrayList<>(resolved.filters().size() + routeInfo.routeFilters.size());
            filters.addAll(resolved.filters());
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
        return routes.<T, R>findAny(uri.toString(), request, ports.get()).stream();
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request) {
        return routes.findAny(request, null, ports.get());
    }

    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAny(HttpRequest<?> request, Predicate<UriRouteMatch<T, R>> filter) {
        return routes.findAny(request, filter, ports.get());
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
     * The routes of a route builder, or assembled without one.
     *
     * @param uriRoutes    The URI routes
     * @param statusRoutes The status routes
     * @param errorRoutes  The error routes
     * @param filterRoutes The filter routes
     * @param exposedPorts The exposed ports
     */
    private record RouteSet(List<UriRoute> uriRoutes,
                            List<StatusRoute> statusRoutes,
                            List<ErrorRoute> errorRoutes,
                            List<FilterRoute> filterRoutes,
                            Set<Integer> exposedPorts) {
    }
}
