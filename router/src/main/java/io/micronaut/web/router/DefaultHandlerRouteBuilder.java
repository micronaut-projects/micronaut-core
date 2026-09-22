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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.web.router.builder.RouteBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The {@link RouteBuilder} of handler functions, adding routes to a {@link DefaultRouteBuilder}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class DefaultHandlerRouteBuilder implements RouteBuilder {

    private final DefaultRouteBuilder builder;

    DefaultHandlerRouteBuilder(DefaultRouteBuilder builder) {
        this.builder = builder;
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handle(HttpMethod method, String uri, RequestHandler handler) {
        return new Routes(builder.handle(method, uri, handler));
    }

    @Override
    public <B> io.micronaut.web.router.builder.UriRoute handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(method, uri, bodyType, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return new Routes(builder.handleForm(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handle(Set<HttpMethod> methods, String uri, RequestHandler handler) {
        return forEach(methods, uri, method -> builder.handle(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler) {
        return forEach(methods, uri, method -> builder.handleAsync(method, uri, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handle(RouteDeclaration route, RequestHandler handler) {
        return new Routes(builder.handle(route, handler));
    }

    @Override
    public <B> io.micronaut.web.router.builder.UriRoute handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(route, bodyType, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(route, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return new Routes(builder.handleForm(route, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(route, handler));
    }

    @Override
    public io.micronaut.web.router.builder.UriRoute handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(route, handler));
    }

    @Override
    public <E extends Throwable> io.micronaut.web.router.builder.ErrorRoute error(Class<E> type, ErrorRouteHandler<E> handler) {
        ErrorRoute route = builder.error(type, handler);
        return new io.micronaut.web.router.builder.ErrorRoute() {
            @Override
            public io.micronaut.web.router.builder.ErrorRoute produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    @Override
    public io.micronaut.web.router.builder.StatusRoute status(HttpStatus status, StatusRouteHandler handler) {
        StatusRoute route = builder.status(status, handler);
        return new io.micronaut.web.router.builder.StatusRoute() {
            @Override
            public io.micronaut.web.router.builder.StatusRoute produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    private static io.micronaut.web.router.builder.UriRoute forEach(Set<HttpMethod> methods, String uri, Function<HttpMethod, HandlerUriRoute> route) {
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
    private static final class Routes implements io.micronaut.web.router.builder.UriRoute {

        private final HandlerUriRoute[] routes;

        Routes(HandlerUriRoute... routes) {
            this.routes = routes;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute consumes(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.consumes(mediaTypes);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute consumesAll() {
            for (HandlerUriRoute route : routes) {
                route.consumesAll();
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute produces(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.produces(mediaTypes);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute executeOn(String executorName) {
            for (HandlerUriRoute route : routes) {
                route.executeOn(executorName);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute nonBlocking() {
            for (HandlerUriRoute route : routes) {
                route.nonBlocking();
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute before(RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(filter);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute before(String executorName, RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(executorName, filter);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute beforeAsync(AsyncRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.beforeAsync(filter);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute after(RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(filter);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute after(String executorName, RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(executorName, filter);
            }
            return this;
        }

        @Override
        public io.micronaut.web.router.builder.UriRoute afterAsync(AsyncRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.afterAsync(filter);
            }
            return this;
        }
    }
}
