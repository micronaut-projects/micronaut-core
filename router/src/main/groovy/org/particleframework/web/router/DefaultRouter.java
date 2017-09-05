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

import org.particleframework.http.HttpMethod;

import java.util.*;
import java.util.stream.Stream;

/**
 * <p>The default {@link Router} implementation. This implementation does not perform any additional caching of route discovery</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultRouter implements Router {

    private final Route[][] routesByMethod = new Route[HttpMethod.values().length][];

    /**
     * Construct a new router for the given route builders
     *
     * @param builders The builders
     */
    public DefaultRouter(RouteBuilder... builders) {
        List<Route> getRoutes = new ArrayList<>();
        List<Route> putRoutes = new ArrayList<>();
        List<Route> postRoutes = new ArrayList<>();
        List<Route> patchRoutes = new ArrayList<>();
        List<Route> deleteRoutes = new ArrayList<>();
        List<Route> optionsRoutes = new ArrayList<>();
        List<Route> headRoutes = new ArrayList<>();
        List<Route> connectRoutes = new ArrayList<>();

        for (RouteBuilder builder : builders) {
            List<Route> constructedRoutes = builder.getConstructedRoutes();
            for (Route route : constructedRoutes) {
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
                }
            }
        }

        for (HttpMethod method : HttpMethod.values()) {
            switch (method) {
                case GET:
                    routesByMethod[method.ordinal()] = getRoutes.toArray(new Route[getRoutes.size()]);
                    break;
                case PUT:
                    routesByMethod[method.ordinal()] = putRoutes.toArray(new Route[putRoutes.size()]);
                    break;
                case POST:
                    routesByMethod[method.ordinal()] = postRoutes.toArray(new Route[postRoutes.size()]);
                    break;
                case PATCH:
                    routesByMethod[method.ordinal()] = patchRoutes.toArray(new Route[patchRoutes.size()]);
                    break;
                case DELETE:
                    routesByMethod[method.ordinal()] = deleteRoutes.toArray(new Route[deleteRoutes.size()]);
                    break;
                case OPTIONS:
                    routesByMethod[method.ordinal()] = optionsRoutes.toArray(new Route[optionsRoutes.size()]);
                    break;
                case HEAD:
                    routesByMethod[method.ordinal()] = headRoutes.toArray(new Route[headRoutes.size()]);
                    break;
                case CONNECT:
                    routesByMethod[method.ordinal()] = connectRoutes.toArray(new Route[connectRoutes.size()]);
                    break;
            }
        }
    }

    @Override
    public Stream<RouteMatch> find(HttpMethod httpMethod, CharSequence uri) {
        Route[] routes = routesByMethod[httpMethod.ordinal()];
        return Arrays.stream(routes)
                .map((route -> route.match(uri.toString())))
                .filter((Optional::isPresent))
                .map((Optional::get));
    }

    @Override
    public Optional<RouteMatch> route(HttpMethod httpMethod, CharSequence uri) {
        return findFirst(httpMethod, uri);
    }

    @Override
    public Optional<RouteMatch> GET(CharSequence uri) {
        return findFirst(HttpMethod.GET, uri);
    }

    @Override
    public Optional<RouteMatch> POST(CharSequence uri) {
        return findFirst(HttpMethod.POST, uri);
    }

    @Override
    public Optional<RouteMatch> PUT(CharSequence uri) {
        return findFirst(HttpMethod.PUT, uri);
    }

    @Override
    public Optional<RouteMatch> PATCH(CharSequence uri) {
        return findFirst(HttpMethod.PATCH, uri);
    }

    @Override
    public Optional<RouteMatch> DELETE(CharSequence uri) {
        return findFirst(HttpMethod.DELETE, uri);
    }

    @Override
    public Optional<RouteMatch> OPTIONS(CharSequence uri) {
        return findFirst(HttpMethod.OPTIONS, uri);
    }

    @Override
    public Optional<RouteMatch> HEAD(CharSequence uri) {
        return findFirst(HttpMethod.HEAD, uri);
    }

    @Override
    public Stream<RouteMatch> findAny(CharSequence uri) {
        return Arrays.stream(routesByMethod)
                .filter(Objects::nonNull)
                .flatMap(Arrays::stream)
                .map(route -> route.match(uri.toString()))
                .filter(Optional::isPresent)
                .map(Optional::get);
    }

    protected Optional<RouteMatch> findFirst(HttpMethod method, CharSequence uri) {
        Route[] routes = routesByMethod[method.ordinal()];
        return Arrays.stream(routes)
                .map((route -> route.match(uri.toString())))
                .filter((Optional::isPresent))
                .map((Optional::get))
                .findFirst();
    }
}
