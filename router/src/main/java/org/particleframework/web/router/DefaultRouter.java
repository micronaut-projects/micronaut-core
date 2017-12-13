/*
 * Copyright 2017 original authors
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
package org.particleframework.web.router;

import org.particleframework.core.order.OrderUtil;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpStatus;
import org.particleframework.http.filter.HttpFilter;

import javax.inject.Singleton;
import java.net.URI;
import java.util.*;
import java.util.stream.Stream;

/**
 * <p>The default {@link Router} implementation. This implementation does not perform any additional caching of route discovery</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
public class DefaultRouter implements Router {

    private final UriRoute[][] routesByMethod = new UriRoute[HttpMethod.values().length][];
    private final StatusRoute[] routesByStatus = new StatusRoute[HttpStatus.values().length];
    private final Collection<FilterRoute> filterRoutes = new ArrayList<>();
    private final SortedSet<ErrorRoute> errorRoutes = new TreeSet<>();
    /**
     * Construct a new router for the given route builders
     *
     * @param builders The builders
     */
    public DefaultRouter(RouteBuilder... builders) {
        List<UriRoute> getRoutes = new ArrayList<>();
        List<UriRoute> putRoutes = new ArrayList<>();
        List<UriRoute> postRoutes = new ArrayList<>();
        List<UriRoute> patchRoutes = new ArrayList<>();
        List<UriRoute> deleteRoutes = new ArrayList<>();
        List<UriRoute> optionsRoutes = new ArrayList<>();
        List<UriRoute> headRoutes = new ArrayList<>();
        List<UriRoute> connectRoutes = new ArrayList<>();
        List<UriRoute> traceRoutes = new ArrayList<>();


        for (RouteBuilder builder : builders) {
            List<UriRoute> constructedRoutes = builder.getUriRoutes();
            for (UriRoute route : constructedRoutes) {
                switch (route.getHttpMethod()) {
                    case GET:
                        getRoutes.add(route);
                        break;
                    case PUT:
                        putRoutes.add(route);
                        break;
                    case POST:
                        postRoutes.add(route);
                        break;
                    case PATCH:
                        patchRoutes.add(route);
                        break;
                    case DELETE:
                        deleteRoutes.add(route);
                        break;
                    case OPTIONS:
                        optionsRoutes.add(route);
                        break;
                    case HEAD:
                        headRoutes.add(route);
                        break;
                    case CONNECT:
                        connectRoutes.add(route);
                        break;
                    case TRACE:
                        traceRoutes.add(route);
                        break;
                }
            }

            for (StatusRoute statusRoute : builder.getStatusRoutes()) {
                HttpStatus status = statusRoute.status();
                this.routesByStatus[status.ordinal()] = statusRoute;
            }

            this.errorRoutes.addAll(builder.getErrorRoutes());
            this.filterRoutes.addAll(builder.getFilterRoutes());
        }

        for (HttpMethod method : HttpMethod.values()) {
            switch (method) {
                case GET:
                    routesByMethod[method.ordinal()] = getRoutes.toArray(new UriRoute[getRoutes.size()]);
                    break;
                case PUT:
                    routesByMethod[method.ordinal()] = putRoutes.toArray(new UriRoute[putRoutes.size()]);
                    break;
                case POST:
                    routesByMethod[method.ordinal()] = postRoutes.toArray(new UriRoute[postRoutes.size()]);
                    break;
                case PATCH:
                    routesByMethod[method.ordinal()] = patchRoutes.toArray(new UriRoute[patchRoutes.size()]);
                    break;
                case DELETE:
                    routesByMethod[method.ordinal()] = deleteRoutes.toArray(new UriRoute[deleteRoutes.size()]);
                    break;
                case OPTIONS:
                    routesByMethod[method.ordinal()] = optionsRoutes.toArray(new UriRoute[optionsRoutes.size()]);
                    break;
                case HEAD:
                    routesByMethod[method.ordinal()] = headRoutes.toArray(new UriRoute[headRoutes.size()]);
                    break;
                case CONNECT:
                    routesByMethod[method.ordinal()] = connectRoutes.toArray(new UriRoute[connectRoutes.size()]);
                    break;
                case TRACE:
                    routesByMethod[method.ordinal()] = traceRoutes.toArray(new UriRoute[traceRoutes.size()]);
                    break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Stream<UriRouteMatch<T>> find(HttpMethod httpMethod, CharSequence uri) {
        UriRoute[] routes = routesByMethod[httpMethod.ordinal()];
        return Arrays.stream(routes)
                .map((route -> route.match(uri.toString())))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    @Override
    public Stream<UriRoute> uriRoutes() {
        return Arrays.stream(routesByMethod)
                .flatMap(Arrays::stream);
    }

    @Override
    public <T> Optional<UriRouteMatch<T>> route(HttpMethod httpMethod, CharSequence uri) {
        UriRoute[] routes = routesByMethod[httpMethod.ordinal()];
        Optional<UriRouteMatch> result = Arrays.stream(routes)
                .map((route -> route.match(uri.toString())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
        UriRouteMatch match = result.orElse(null);
        return match != null ? Optional.of(match) : Optional.empty();
    }

    @Override
    public <T> Optional<RouteMatch<T>> route(HttpStatus status) {
        StatusRoute routesByStatus = this.routesByStatus[status.ordinal()];
        if(routesByStatus == null) {
            return Optional.empty();
        }
        return routesByStatus.match(status);
    }

    @Override
    public <T> Optional<RouteMatch<T>> route(Class originatingClass, Throwable error) {
        for (ErrorRoute errorRoute : errorRoutes) {
            Optional<RouteMatch<T>> match = errorRoute.match(originatingClass, error);
             if(match.isPresent()) {
                return match;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<HttpFilter> findFilters(HttpRequest<?> request) {
        List<HttpFilter> httpFilters = new ArrayList<>();
        HttpMethod method = request.getMethod();
        URI uri = request.getUri();
        for (FilterRoute filterRoute : filterRoutes) {
            Optional<HttpFilter> match = filterRoute.match(method, uri);
            match.ifPresent(httpFilters::add);
        }
        if(!httpFilters.isEmpty()) {

            OrderUtil.sort(httpFilters);
            return Collections.unmodifiableList(httpFilters);
        }
        else {
            return Collections.emptyList();
        }
    }


    @Override
    public <T> Optional<RouteMatch<T>> route(Throwable error) {
        for (ErrorRoute errorRoute : errorRoutes) {
            if(errorRoute.originatingType() == null) {
                Optional<RouteMatch<T>> match = errorRoute.match(error);
                if(match.isPresent()) {
                    return match;
                }
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> Stream<UriRouteMatch<T>> findAny(CharSequence uri) {
        return Arrays.stream(routesByMethod)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .map(route -> route.match(uri.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }


}
