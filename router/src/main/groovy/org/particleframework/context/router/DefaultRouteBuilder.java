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
package org.particleframework.context.router;


import org.particleframework.context.BeanContext;
import org.particleframework.context.router.exceptions.RoutingException;
import org.particleframework.http.HttpMethod;
import org.particleframework.http.MediaType;
import org.particleframework.http.uri.UriMatchInfo;
import org.particleframework.http.uri.UriMatchTemplate;
import org.particleframework.http.uri.UriTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * A DefaultRouteBuilder implementation for building roots
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class DefaultRouteBuilder implements RouteBuilder {

    private final BeanContext beanContext;
    private final UriNamingStrategy uriNamingStrategy = new UriNamingStrategy() {};

    private DefaultRoute currentParentRoute = null;
    private List<DefaultRoute> builtRoutes = new ArrayList<>();

    public DefaultRouteBuilder(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public UriNamingStrategy getUriNamingStrategy() {
        return uriNamingStrategy;
    }


    @Override
    public ResourceRoute resources(Class cls) {
        return null;
    }

    public List<Route> getBuiltRoutes() {
        return Collections.unmodifiableList(builtRoutes);
    }

    @Override
    public ResourceRoute single(Class cls) {
        return null;
    }

    @Override
    public Route status(int code, Class type, String method) {
        return null;
    }

    @Override
    public Route error(Class<? extends Throwable> error, Class type, String method) {
        return null;
    }

    @Override
    public Route GET(String uri, Object target, String method) {
        return buildRoute(HttpMethod.GET, uri);
    }

    @Override
    public Route GET(String uri, Class<?> type, String method) {
//        Object instance = beanContext.findBean(type)
//                .orElseThrow(() -> new RoutingException("No bean found for route "));
        return buildRoute(HttpMethod.GET, uri);
    }

    protected Route buildRoute(HttpMethod httpMethod, String uri) {
        DefaultRoute route;
        if (currentParentRoute != null) {
            route = new DefaultRoute(HttpMethod.GET, currentParentRoute.uriMatchTemplate.nest(uri) );
            currentParentRoute.nestedRoutes.add(route);
        }
        else {
            route = new DefaultRoute(httpMethod, uri);
        }
        this.builtRoutes.add(route);
        return route;
    }

    @Override
    public Route POST(String uri, Object target, String method) {
        return buildRoute(HttpMethod.POST, uri);
    }

    @Override
    public Route POST(String uri, Class type, String method) {
        return buildRoute(HttpMethod.POST, uri);
    }

    @Override
    public Route PUT(String uri, Object target, String method) {
        return buildRoute(HttpMethod.PUT, uri);
    }

    @Override
    public Route PUT(String uri, Class type, String method) {
        return buildRoute(HttpMethod.PUT, uri);
    }

    @Override
    public Route PATCH(String uri, Object target, String method) {
        return buildRoute(HttpMethod.PATCH, uri);
    }

    @Override
    public Route PATCH(String uri, Class type, String method) {
        return buildRoute(HttpMethod.PATCH, uri);
    }

    @Override
    public Route DELETE(String uri, Object target, String method) {
        return buildRoute(HttpMethod.DELETE, uri);
    }

    @Override
    public Route DELETE(String uri, Class type, String method) {
        return buildRoute(HttpMethod.DELETE, uri);
    }

    @Override
    public Route OPTIONS(String uri, Object target, String method) {
        return buildRoute(HttpMethod.OPTIONS, uri);
    }

    @Override
    public Route OPTIONS(String uri, Class type, String method) {
        return buildRoute(HttpMethod.OPTIONS, uri);
    }

    @Override
    public Route HEAD(String uri, Object target, String method) {
        return buildRoute(HttpMethod.HEAD, uri);
    }

    @Override
    public Route HEAD(String uri, Class type, String method) {
        return buildRoute(HttpMethod.HEAD, uri);
    }

    /**
     * The default route impl
     */
    class DefaultRoute implements Route {

        private final HttpMethod httpMethod;
        private final MediaType mediaType;
        private final UriMatchTemplate uriMatchTemplate;
        private final List<DefaultRoute> nestedRoutes = new ArrayList<>();

        DefaultRoute(HttpMethod httpMethod, CharSequence uriTemplate) {
            this(httpMethod, uriTemplate, MediaType.JSON);
        }

        DefaultRoute(HttpMethod httpMethod, CharSequence uriTemplate, MediaType mediaType) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), mediaType);
        }

        DefaultRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate) {
            this(httpMethod, uriTemplate, MediaType.JSON);
        }

        DefaultRoute(HttpMethod httpMethod, UriMatchTemplate uriTemplate, MediaType mediaType) {
            this.httpMethod = httpMethod;
            this.uriMatchTemplate = uriTemplate;
            this.mediaType = mediaType;
        }

        @Override
        public UriTemplate getUriTemplate() {
            return uriMatchTemplate;
        }

        @Override
        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        @Override
        public MediaType getMediaType() {
            return mediaType;
        }

        @Override
        public Route accept(MediaType mediaType) {
            DefaultRouteBuilder.this.builtRoutes.remove(this);
            DefaultRoute newRoute = new DefaultRoute(httpMethod, uriMatchTemplate, mediaType);
            DefaultRouteBuilder.this.builtRoutes.add(newRoute);
            return newRoute;
        }

        @Override
        public Route nest(Runnable nested) {
            DefaultRoute previous = DefaultRouteBuilder.this.currentParentRoute;
            DefaultRouteBuilder.this.currentParentRoute = this;
            try {
                nested.run();
            } finally {
                DefaultRouteBuilder.this.currentParentRoute = previous;
            }
            return this;
        }

        @Override
        public Optional<UriMatchInfo> match(String uri) {
            return uriMatchTemplate.match(uri);
        }
    }

}
