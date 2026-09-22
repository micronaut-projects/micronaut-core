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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.DefaultRouteBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The {@link HttpRouteBuilder} of handler functions, adding routes to a {@link DefaultRouteBuilder}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultHandlerRouteBuilder implements HttpRouteBuilder {

    private final DefaultRouteBuilder builder;

    public DefaultHandlerRouteBuilder(DefaultRouteBuilder builder) {
        this.builder = builder;
    }

    @Override
    public HttpRouteSpec handle(HttpMethod method, String uri, RequestHandler handler) {
        return new Routes(builder.handle(method, uri, handler));
    }

    @Override
    public <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(method, uri, bodyType, handler));
    }

    @Override
    public HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(method, uri, handler));
    }

    @Override
    public <B> HttpRouteSpec handleAsync(HttpMethod method, String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new Routes(builder.handleAsync(method, uri, bodyType, handler));
    }

    @Override
    public <B> HttpRouteSpec handleAsync(RouteDeclaration route, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new Routes(builder.handleAsync(route, bodyType, handler));
    }

    @Override
    public HttpRouteSpec handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return new Routes(builder.handleForm(method, uri, handler));
    }

    @Override
    public HttpRouteSpec handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(method, uri, handler));
    }

    @Override
    public HttpRouteSpec handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(method, uri, handler));
    }

    @Override
    public HttpRouteSpec handle(Set<HttpMethod> methods, String uri, RequestHandler handler) {
        return forEach(methods, uri, method -> builder.handle(method, uri, handler));
    }

    @Override
    public HttpRouteSpec handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler) {
        return forEach(methods, uri, method -> builder.handleAsync(method, uri, handler));
    }

    @Override
    public HttpRouteSpec handle(RouteDeclaration route, RequestHandler handler) {
        return new Routes(builder.handle(route, handler));
    }

    @Override
    public <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(route, bodyType, handler));
    }

    @Override
    public HttpRouteSpec handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(route, handler));
    }

    @Override
    public HttpRouteSpec handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return new Routes(builder.handleForm(route, handler));
    }

    @Override
    public HttpRouteSpec handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(route, handler));
    }

    @Override
    public HttpRouteSpec handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(route, handler));
    }

    @Override
    public <E extends Throwable> ErrorRouteSpec error(Class<E> type, ErrorRouteHandler<E> handler) {
        io.micronaut.web.router.ErrorRoute route = builder.error(type, handler);
        return new ErrorRouteSpec() {
            @Override
            public ErrorRouteSpec produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    @Override
    public StatusRouteSpec status(HttpStatus status, StatusRouteHandler handler) {
        io.micronaut.web.router.StatusRoute route = builder.status(status, handler);
        return new StatusRouteSpec() {
            @Override
            public StatusRouteSpec produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    private static HttpRouteSpec forEach(Set<HttpMethod> methods, String uri, Function<HttpMethod, HandlerUriRoute> route) {
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No HTTP method for route: " + uri);
        }
        List<HandlerUriRoute> routes = new ArrayList<>(methods.size());
        for (HttpMethod method : methods) {
            routes.add(route.apply(method));
        }
        return new Routes(routes.toArray(new HandlerUriRoute[0]));
    }

    /**
     * The routes of a handler: one, or one per HTTP method, configured together.
     */
    private static final class Routes implements HttpRouteSpec {

        private final HandlerUriRoute[] routes;

        Routes(HandlerUriRoute... routes) {
            this.routes = routes;
        }

        @Override
        public HttpRouteSpec consumes(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.consumes(mediaTypes);
            }
            return this;
        }

        @Override
        public HttpRouteSpec consumesAll() {
            for (HandlerUriRoute route : routes) {
                route.consumesAll();
            }
            return this;
        }

        @Override
        public HttpRouteSpec produces(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.produces(mediaTypes);
            }
            return this;
        }

        @Override
        public HttpRouteSpec executeOn(String executorName) {
            for (HandlerUriRoute route : routes) {
                route.executeOn(executorName);
            }
            return this;
        }

        @Override
        public HttpRouteSpec nonBlocking() {
            for (HandlerUriRoute route : routes) {
                route.nonBlocking();
            }
            return this;
        }

        @Override
        public HttpRouteSpec before(RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec before(String executorName, RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(executorName, filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec beforeAsync(AsyncRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.beforeAsync(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec after(RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec after(String executorName, RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(executorName, filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec afterAsync(AsyncRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.afterAsync(filter);
            }
            return this;
        }
    }
}
