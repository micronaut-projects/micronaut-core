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
 * The {@link RouteBuilder} of handler functions, adding routes to a {@link DefaultRouteBuilder}.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultHandlerRouteBuilder implements RouteBuilder {

    private final DefaultRouteBuilder builder;

    public DefaultHandlerRouteBuilder(DefaultRouteBuilder builder) {
        this.builder = builder;
    }

    @Override
    public UriRoute handle(HttpMethod method, String uri, RequestHandler handler) {
        return new Routes(builder.handle(method, uri, handler));
    }

    @Override
    public <B> UriRoute handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(method, uri, bodyType, handler));
    }

    @Override
    public UriRoute handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(method, uri, handler));
    }

    @Override
    public UriRoute handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return new Routes(builder.handleForm(method, uri, handler));
    }

    @Override
    public UriRoute handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(method, uri, handler));
    }

    @Override
    public UriRoute handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(method, uri, handler));
    }

    @Override
    public UriRoute handle(Set<HttpMethod> methods, String uri, RequestHandler handler) {
        return forEach(methods, uri, method -> builder.handle(method, uri, handler));
    }

    @Override
    public UriRoute handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler) {
        return forEach(methods, uri, method -> builder.handleAsync(method, uri, handler));
    }

    @Override
    public UriRoute handle(RouteDeclaration route, RequestHandler handler) {
        return new Routes(builder.handle(route, handler));
    }

    @Override
    public <B> UriRoute handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(builder.handle(route, bodyType, handler));
    }

    @Override
    public UriRoute handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return new Routes(builder.handleAsync(route, handler));
    }

    @Override
    public UriRoute handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return new Routes(builder.handleForm(route, handler));
    }

    @Override
    public UriRoute handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler) {
        return new Routes(builder.handleFormAsync(route, handler));
    }

    @Override
    public UriRoute handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler) {
        return new Routes(builder.handleFormStream(route, handler));
    }

    @Override
    public <E extends Throwable> ErrorRoute error(Class<E> type, ErrorRouteHandler<E> handler) {
        io.micronaut.web.router.ErrorRoute route = builder.error(type, handler);
        return new ErrorRoute() {
            @Override
            public ErrorRoute produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    @Override
    public StatusRoute status(HttpStatus status, StatusRouteHandler handler) {
        io.micronaut.web.router.StatusRoute route = builder.status(status, handler);
        return new StatusRoute() {
            @Override
            public StatusRoute produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    private static UriRoute forEach(Set<HttpMethod> methods, String uri, Function<HttpMethod, HandlerUriRoute> route) {
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
    private static final class Routes implements UriRoute {

        private final HandlerUriRoute[] routes;

        Routes(HandlerUriRoute... routes) {
            this.routes = routes;
        }

        @Override
        public UriRoute consumes(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.consumes(mediaTypes);
            }
            return this;
        }

        @Override
        public UriRoute consumesAll() {
            for (HandlerUriRoute route : routes) {
                route.consumesAll();
            }
            return this;
        }

        @Override
        public UriRoute produces(MediaType... mediaTypes) {
            for (HandlerUriRoute route : routes) {
                route.produces(mediaTypes);
            }
            return this;
        }

        @Override
        public UriRoute executeOn(String executorName) {
            for (HandlerUriRoute route : routes) {
                route.executeOn(executorName);
            }
            return this;
        }

        @Override
        public UriRoute nonBlocking() {
            for (HandlerUriRoute route : routes) {
                route.nonBlocking();
            }
            return this;
        }

        @Override
        public UriRoute before(RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(filter);
            }
            return this;
        }

        @Override
        public UriRoute before(String executorName, RouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(executorName, filter);
            }
            return this;
        }

        @Override
        public UriRoute beforeAsync(AsyncRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.beforeAsync(filter);
            }
            return this;
        }

        @Override
        public UriRoute after(RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(filter);
            }
            return this;
        }

        @Override
        public UriRoute after(String executorName, RouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(executorName, filter);
            }
            return this;
        }

        @Override
        public UriRoute afterAsync(AsyncRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.afterAsync(filter);
            }
            return this;
        }
    }
}
