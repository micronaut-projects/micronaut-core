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

    private UriMatchTemplate currentNestedTemplate = null;

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
        Class<?> type = target.getClass();
        if(beanContext.containsBean(type)) {

        }
        else {
            throw new RuntimeException("Target of route must be a bean within ");
        }
        return null;
    }

    @Override
    public Route GET(String uri, Class<?> type, String method) {
        Object instance = beanContext.findBean(type)
                .orElseThrow(() -> new RoutingException("No bean found for route "));
        HttpMethod httpMethod = HttpMethod.GET;

        DefaultRoute route;
        if (currentNestedTemplate != null) {
            route = new DefaultRoute(HttpMethod.GET, currentNestedTemplate.nest(uri) );
        }
        else {
            route = new DefaultRoute(httpMethod, uri);
        }
        return route;
    }

    @Override
    public Route POST(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route POST(String uri, Class type, String method) {
        return null;
    }

    @Override
    public Route PUT(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route PUT(String uri, Class type, String method) {
        return null;
    }

    @Override
    public Route PATCH(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route PATCH(String uri, Class type, String method) {
        return null;
    }

    @Override
    public Route DELETE(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route DELETE(String uri, Class type, String method) {
        return null;
    }

    @Override
    public Route OPTIONS(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route OPTIONS(String uri, Class type, String method) {
        return null;
    }

    @Override
    public Route HEAD(String uri, Object target, String method) {
        return null;
    }

    @Override
    public Route HEAD(String uri, Class type, String method) {
        return null;
    }

    /**
     * @author Graeme Rocher
     * @since 1.0
     */
    class DefaultRoute implements Route {

        private final HttpMethod httpMethod;
        private final MediaType mediaType;
        private final UriMatchTemplate uriMatchTemplate;

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
            return new DefaultRoute(httpMethod, uriMatchTemplate, mediaType);
        }

        @Override
        public Route nest(Runnable nested) {
            UriMatchTemplate previous = DefaultRouteBuilder.this.currentNestedTemplate;
            DefaultRouteBuilder.this.currentNestedTemplate = uriMatchTemplate;
            try {
                nested.run();
            } finally {
                DefaultRouteBuilder.this.currentNestedTemplate = previous;
            }
            return this;
        }

        @Override
        public Optional<UriMatchInfo> match(String uri) {
            return uriMatchTemplate.match(uri);
        }
    }

}
