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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.web.router.RouteTable;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * The {@link HttpRouteBuilder}: adds the routes to handler functions to a {@link RouteAssembly},
 * like the legacy route builder adds its routes, without depending on it.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultHttpRouteBuilder implements HttpRouteBuilder {

    private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};
    private static final List<MediaType> DEFAULT_CONSUMES = List.of(MediaType.APPLICATION_JSON_TYPE);

    private final RouteAssembly assembly;

    /**
     * @param assembly The assembly the routes are added to
     */
    public DefaultHttpRouteBuilder(RouteAssembly assembly) {
        this.assembly = assembly;
    }

    @Override
    public HttpRouteSpec handle(HttpMethod method, String uri, RequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        // the body argument is annotated @Body
        return new Routes(route(method, uri, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public <B> HttpRouteSpec handleAsync(HttpMethod method, String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new Routes(route(method, uri, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public HttpRouteSpec handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormAsync(HttpMethod method, String uri, AsyncFormRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormStream(HttpMethod method, String uri, StreamingFormRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handle(Set<HttpMethod> methods, String uri, RequestHandler handler) {
        return forEach(methods, uri, method -> route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public HttpRouteSpec handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler) {
        return forEach(methods, uri, method -> route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public HttpRouteSpec handle(RouteDeclaration route, RequestHandler handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(handler)), null));
    }

    @Override
    public <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(bodyType, handler)), null));
    }

    @Override
    public HttpRouteSpec handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(handler)), null));
    }

    @Override
    public <B> HttpRouteSpec handleAsync(RouteDeclaration route, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(bodyType, handler)), null));
    }

    @Override
    public HttpRouteSpec handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(handler)), FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormAsync(RouteDeclaration route, AsyncFormRequestHandler handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(handler)), FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormStream(RouteDeclaration route, StreamingFormRequestHandler handler) {
        return new Routes(assembly.declare(route, handle(HandlerMethod.of(handler)), FORM_MEDIA_TYPES));
    }

    @Override
    public <E extends Throwable> ErrorRouteSpec error(Class<E> type, ErrorRouteHandler<E> handler) {
        RouteAssembly.DefaultErrorRoute route = assembly.addErrorRoute(null, type, handle(HandlerMethod.of(type, handler)));
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
        RouteAssembly.DefaultStatusRoute route = assembly.addStatusRoute(null, status, handle(HandlerMethod.of(handler)));
        return new StatusRouteSpec() {
            @Override
            public StatusRouteSpec produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    @Override
    public HttpRouteSpec handle(String httpMethodName, String uri, RequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public <B> HttpRouteSpec handle(String httpMethodName, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(bodyType, handler)));
    }

    @Override
    public HttpRouteSpec handleAsync(String httpMethodName, String uri, AsyncRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public <B> HttpRouteSpec handleAsync(String httpMethodName, String uri, Argument<B> bodyType, AsyncBodyRequestHandler<B> handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(bodyType, handler)));
    }

    @Override
    public HttpRouteSpec handleForm(String httpMethodName, String uri, FormRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)).consumes(FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormAsync(String httpMethodName, String uri, AsyncFormRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)).consumes(FORM_MEDIA_TYPES));
    }

    @Override
    public HttpRouteSpec handleFormStream(String httpMethodName, String uri, StreamingFormRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)).consumes(FORM_MEDIA_TYPES));
    }

    @Override
    public void locate(String prefixUri, LocatorHandler locator, Function<Object, RouteTable> tables) {
        Objects.requireNonNull(prefixUri, "prefixUri");
        MethodExecutionHandle<Object, Object> target = handle(HandlerMethod.of(new RouteLocator(locator, tables)));
        for (String template : RouteLocator.templates(prefixUri)) {
            for (HttpMethod method : HttpMethod.values()) {
                if (method != HttpMethod.CUSTOM) {
                    // the routes of the target decide which media types they consume and produce
                    assembly.addRoute(method.name(), method, template, DEFAULT_CONSUMES, target).consumesAll();
                }
            }
        }
    }

    private HandlerUriRoute route(String httpMethodName, String uri, HandlerMethod<?> handler) {
        Objects.requireNonNull(httpMethodName, "httpMethodName");
        HttpMethod method = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        String name = method == HttpMethod.CUSTOM ? httpMethodName : method.name();
        return assembly.addRoute(name, method, uri, DEFAULT_CONSUMES, handle(handler));
    }

    private HandlerUriRoute route(HttpMethod method, String uri, HandlerMethod<?> handler, MediaType @Nullable [] consumes) {
        RouteAssembly.DefaultUriRoute route = assembly.addRoute(method.name(), method, uri, DEFAULT_CONSUMES, handle(handler));
        return consumes == null ? route : route.consumes(consumes);
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handle(HandlerMethod<?> method) {
        return (MethodExecutionHandle<Object, Object>) method;
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
        public HttpRouteSpec annotationMetadata(AnnotationMetadata annotationMetadata) {
            for (HandlerUriRoute route : routes) {
                route.annotationMetadata(annotationMetadata);
            }
            return this;
        }

        @Override
        public HttpRouteSpec implementing(ExecutableMethod<?, ?> method) {
            for (HandlerUriRoute route : routes) {
                route.implementing(method);
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
