/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.filter.HttpFilter;
import io.micronaut.http.uri.UriMatchTemplate;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.net.URI;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>The default {@link Router} implementation. This implementation does not perform any additional caching of
 * route discovery.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRouter implements Router {

    private final Map<String, List<UriRoute>> routesByMethod = new HashMap<>();
    private final Set<StatusRoute> statusRoutes = new HashSet<>();
    private final Collection<FilterRoute> filterRoutes = new ArrayList<>();
    private final Set<ErrorRoute> errorRoutes = new HashSet<>();

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    @Inject
    public DefaultRouter(Collection<RouteBuilder> builders) {
        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                String key = route.getHttpMethodName();
                routesByMethod.computeIfAbsent(key, x -> new ArrayList<>()).add(route);
            }

            this.statusRoutes.addAll(builder.getStatusRoutes());
            this.errorRoutes.addAll(builder.getErrorRoutes());
            this.filterRoutes.addAll(builder.getFilterRoutes());
        }

        routesByMethod.values().forEach(this::finalizeRoutes);
    }

    /**
     * Construct a new router for the given route builders.
     *
     * @param builders The builders
     */
    public DefaultRouter(RouteBuilder... builders) {
        this(Arrays.asList(builders));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri) {
        return find(httpMethod.name(), uri);
    }

    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpRequest request, @Nonnull CharSequence uri) {
        return find(request.getMethodName(), uri);
    }

    @Nonnull
    @Deprecated
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull URI uri) {
        return find(httpMethod.name(), uri.toString());
    }

    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpRequest<?> request) {
        boolean permitsBody = HttpMethod.permitsRequestBody(request.getMethod());
        return this.<T, R>find(request, request.getPath())
                .filter((match) -> match.test(request) && (!permitsBody || match.accept(request.getContentType().orElse(null))));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> find(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri, @Nullable HttpRequest<?> context) {
        return find(httpMethod, uri);
    }

    @Override
    public Stream<UriRoute> uriRoutes() {
        return routesByMethod.values().stream().flatMap(List::stream);
    }

    @Nonnull
    @Override
    public <T, R> List<UriRouteMatch<T, R>> findAllClosest(@Nonnull HttpRequest<?> request) {
        List<UriRouteMatch<T, R>> uriRoutes = this.<T, R>find(request).collect(Collectors.toList());

        if (uriRoutes.size() > 1 && HttpMethod.permitsRequestBody(request.getMethod())) {

            List<UriRouteMatch<T, R>> explicitAcceptRoutes = new ArrayList<>(uriRoutes.size());
            List<UriRouteMatch<T, R>> acceptRoutes = new ArrayList<>(uriRoutes.size());

            Optional<MediaType> contentType = request.getContentType();

            for (UriRouteMatch<T, R> match: uriRoutes) {
                if (match.explicitAccept(contentType.orElse(MediaType.ALL_TYPE))) {
                    explicitAcceptRoutes.add(match);
                }
                if (explicitAcceptRoutes.isEmpty() && match.accept(contentType.orElse(null))) {
                    acceptRoutes.add(match);
                }
            }

            uriRoutes = explicitAcceptRoutes.isEmpty() ? acceptRoutes : explicitAcceptRoutes;
        }

        /**
         * Any changes to the logic below may also need changes to {@link io.micronaut.http.uri.UriTemplate#compareTo(UriTemplate)}
         */
        if (uriRoutes.size() > 1) {
            long variableCount = 0;
            long rawLength = 0;

            List<UriRouteMatch<T, R>> closestMatches = new ArrayList<>(uriRoutes.size());

            for (int i = 0; i < uriRoutes.size(); i++) {
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

    @Nonnull
    @Override
    public <T, R> Optional<UriRouteMatch<T, R>> route(@Nonnull HttpMethod httpMethod, @Nonnull CharSequence uri) {
        List<UriRoute> routes = routesByMethod.getOrDefault(httpMethod.name(), Collections.emptyList());
        Optional<UriRouteMatch> result = routes.stream()
            .map((route -> route.match(uri.toString())))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();

        UriRouteMatch match = result.orElse(null);
        return Optional.ofNullable(match);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@Nonnull HttpStatus status) {
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
    public <R> Optional<RouteMatch<R>> route(@Nonnull Class originatingClass, @Nonnull HttpStatus status) {
        for (StatusRoute statusRoute : statusRoutes) {
            Optional<RouteMatch<R>> match = statusRoute.match(originatingClass, status);
            if (match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@Nonnull Class originatingClass, @Nonnull Throwable error) {
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            Optional<RouteMatch<R>> match = errorRoute.match(originatingClass, error);
            match.ifPresent((m) -> {
                matchedRoutes.put(errorRoute, m);
            });
        }
        return findRouteMatch(matchedRoutes, error);
    }

    @Override
    public <R> Optional<RouteMatch<R>> route(@Nonnull Throwable error) {
        Map<ErrorRoute, RouteMatch<R>> matchedRoutes = new LinkedHashMap<>();
        for (ErrorRoute errorRoute : errorRoutes) {
            if (errorRoute.originatingType() == null) {
                Optional<RouteMatch<R>> match = errorRoute.match(error);
                match.ifPresent((m) -> matchedRoutes.put(errorRoute, m));
            }
        }

        return findRouteMatch(matchedRoutes, error);
    }

    @Nonnull
    @Override
    public List<HttpFilter> findFilters(@Nonnull HttpRequest<?> request) {
        List<HttpFilter> httpFilters = new ArrayList<>();
        HttpMethod method = request.getMethod();
        URI uri = request.getUri();
        for (FilterRoute filterRoute : filterRoutes) {
            Optional<HttpFilter> match = filterRoute.match(method, uri);
            match.ifPresent(httpFilters::add);
        }
        if (!httpFilters.isEmpty()) {
            OrderUtil.sort(httpFilters);
            return Collections.unmodifiableList(httpFilters);
        } else {
            return Collections.emptyList();
        }
    }

    @SuppressWarnings("unchecked")
    @Deprecated
    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@Nonnull CharSequence uri) {
        return findAny(uri, null);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <T, R> Stream<UriRouteMatch<T, R>> findAny(@Nonnull CharSequence uri, @Nullable HttpRequest<?> context) {
        return uriRoutes()
                .filter(Objects::nonNull)
                .map(route -> route.match(uri.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    private <T, R> Stream<UriRouteMatch<T, R>> find(String httpMethodName, CharSequence uri) {
        List<UriRoute> routes = routesByMethod.getOrDefault(httpMethodName, Collections.emptyList());
        return routes.stream()
                .map((route -> route.match(uri.toString())))
                .filter(Optional::isPresent)
                .map(Optional::get);
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
}
