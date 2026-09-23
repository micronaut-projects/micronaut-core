/*
 * Copyright 2017-2026 original authors
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
import io.micronaut.http.uri.RouteTemplate;
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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Adds the routes to handler functions to a {@link RouteAssembly}: the routes of the builder of
 * the {@link HttpRoutes} beans and of a route table, see {@link DefaultHttpRouteBuilder}, and of a
 * group of routes, see {@link DefaultHttpRouteGroup}, which prefixes their URIs and applies its
 * filters to them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract sealed class AbstractHttpRouteBuilder implements HttpRouteBuilder permits DefaultHttpRouteBuilder, DefaultHttpRouteGroup {

    private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};
    private static final List<MediaType> DEFAULT_CONSUMES = List.of(MediaType.APPLICATION_JSON_TYPE);

    final RouteAssembly assembly;
    /**
     * The filters of the group the routes are declared in, or {@code null} outside a group.
     */
    final RouteAssembly.@Nullable RouteFilters groupFilters;
    /**
     * The prefix of the URI templates of the routes, or {@code null}.
     */
    private final @Nullable RoutePrefix prefix;

    /**
     * @param assembly     The assembly the routes are added to
     * @param groupFilters The filters of the group the routes are declared in, or {@code null}
     * @param prefix       The prefix of the URI templates of the routes, or {@code null}
     */
    AbstractHttpRouteBuilder(RouteAssembly assembly, RouteAssembly.@Nullable RouteFilters groupFilters, @Nullable RoutePrefix prefix) {
        this.assembly = assembly;
        this.groupFilters = groupFilters;
        this.prefix = prefix;
    }

    @Override
    public final HttpRouteSpec handle(HttpMethod method, String uri, RequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        // the body argument is annotated @Body
        return new Routes(route(method, uri, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return new Routes(route(method, uri, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
    }

    @Override
    public final HttpRouteSpec handle(Set<HttpMethod> methods, String uri, RequestHandler handler) {
        return forEach(methods, uri, method -> route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(Set<HttpMethod> methods, String uri, AsyncRequestHandler handler) {
        return forEach(methods, uri, method -> route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handle(RouteDeclaration route, RequestHandler handler) {
        return new Routes(declare(route, HandlerMethod.of(handler), null));
    }

    @Override
    public final <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(declare(route, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return new Routes(declare(route, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return new Routes(declare(route, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
    }

    @Override
    public final <E extends Throwable> ErrorRouteSpec error(Class<E> type, ErrorRouteHandler<E> handler) {
        return errorRoute(type, HandlerMethod.of(type, handler));
    }

    @Override
    public final <E extends Throwable> ErrorRouteSpec errorAsync(Class<E> type, AsyncErrorRouteHandler<E> handler) {
        return errorRoute(type, HandlerMethod.of(type, handler));
    }

    @Override
    public final StatusRouteSpec status(HttpStatus status, StatusRouteHandler handler) {
        return statusRoute(status, HandlerMethod.of(handler));
    }

    @Override
    public final StatusRouteSpec statusAsync(HttpStatus status, AsyncStatusRouteHandler handler) {
        return statusRoute(status, HandlerMethod.of(handler));
    }

    @Override
    public final HttpRouteSpec handle(String httpMethodName, String uri, RequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public final <B> HttpRouteSpec handle(String httpMethodName, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(bodyType, handler)));
    }

    @Override
    public final HttpRouteSpec handleAsync(String httpMethodName, String uri, AsyncRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public final HttpRouteSpec handleForm(String httpMethodName, String uri, FormRequestHandler handler) {
        return new Routes(route(httpMethodName, uri, HandlerMethod.of(handler)).consumes(FORM_MEDIA_TYPES));
    }

    @Override
    public final void locate(String prefixUri, LocatorHandler locator, Function<Object, RouteTable> tables) {
        Objects.requireNonNull(prefixUri, "prefixUri");
        checkOpen();
        MethodExecutionHandle<Object, Object> target = handle(HandlerMethod.of(new RouteLocator(locator, tables)));
        for (String template : RouteLocator.templates(uri(prefixUri))) {
            for (HttpMethod method : HttpMethod.values()) {
                if (method != HttpMethod.CUSTOM) {
                    // the routes of the target decide which media types they consume and produce
                    // the located route carries the filters of the groups of the locator route
                    grouped(assembly.addRoute(method.name(), method, template, DEFAULT_CONSUMES, target).consumesAll());
                }
            }
        }
    }

    @Override
    public final void locate(RouteTemplate prefixTemplate, LocatorHandler locator, Function<Object, RouteTable> tables) {
        Objects.requireNonNull(prefixTemplate, "prefix");
        if (prefixTemplate.isMicronaut()) {
            locate(prefixTemplate.expression(), locator, tables);
            return;
        }
        checkOpen();
        RoutePrefix routePrefix = prefix;
        if (routePrefix != null) {
            throw new IllegalArgumentException("The locator prefix " + prefixTemplate.expression() + " of the route template engine '"
                + prefixTemplate.engineId() + "' cannot be declared in the route group with the prefix " + routePrefix
                + ": the prefix of a group is joined to Micronaut URI templates only. Declare it outside the group, or in a group without a prefix");
        }
        MethodExecutionHandle<Object, Object> target = handle(HandlerMethod.of(new RouteLocator(locator, tables)));
        for (RouteAssembly.DefaultUriRoute route : assembly.addLocatorRoutes(prefixTemplate, DEFAULT_CONSUMES, target)) {
            // the routes of the target decide which media types they consume and produce
            // the located route carries the filters of the groups of the locator route
            grouped(route.consumesAll());
        }
    }

    @Override
    public final ServerFilterSpec filter(String... patterns) {
        checkOpen();
        // global: the prefix and the filters of a group do not apply
        return new DefaultServerFilterSpec(assembly.addServerFilter(patterns));
    }

    @Override
    public final void group(Consumer<HttpRouteGroup> routes) {
        Objects.requireNonNull(routes, "routes");
        declareGroup(prefix, routes);
    }

    @Override
    public final void path(String prefix, Consumer<HttpRouteGroup> routes) {
        Objects.requireNonNull(routes, "routes");
        declareGroup(RoutePrefix.of(prefix, this.prefix), routes);
    }

    private void declareGroup(@Nullable RoutePrefix groupPrefix, Consumer<HttpRouteGroup> routes) {
        checkOpen();
        DefaultHttpRouteGroup group = new DefaultHttpRouteGroup(assembly, assembly.groupFilters(groupFilters), groupPrefix);
        try {
            routes.accept(group);
        } finally {
            // the filters of the group are the ones declared in its lambda
            group.close();
        }
    }

    private void checkOpen() {
        RouteAssembly.RouteFilters filters = groupFilters;
        if (filters != null && filters.isClosed()) {
            throw new IllegalStateException("The route group is closed: declare the routes of a group in its lambda");
        }
    }

    private String uri(String uri) {
        Objects.requireNonNull(uri, "uri");
        checkOpen();
        RoutePrefix routePrefix = prefix;
        return routePrefix == null ? uri : routePrefix.prefix(uri);
    }

    private HandlerUriRoute grouped(HandlerUriRoute route) {
        RouteAssembly.RouteFilters filters = groupFilters;
        return filters == null ? route : route.inGroup(filters);
    }

    private HandlerUriRoute declare(RouteDeclaration route, HandlerMethod<?> handler, MediaType @Nullable [] consumes) {
        Objects.requireNonNull(route, "route");
        checkOpen();
        if (prefix != null) {
            throw new IllegalArgumentException("The declared route " + route.httpMethodName() + " " + route.uriTemplate()
                + " cannot be bound in the route group with the prefix " + prefix
                + ": its index keys are computed for its own URI template. Bind it outside the group, or in a group without a prefix");
        }
        return grouped(assembly.declare(route, handle(handler), consumes));
    }

    private ErrorRouteSpec errorRoute(Class<? extends Throwable> type, HandlerMethod<?> handler) {
        checkOpen();
        // global, also when declared in a group
        RouteAssembly.DefaultErrorRoute route = assembly.addErrorRoute(null, type, handle(handler));
        return new ErrorRouteSpec() {
            @Override
            public ErrorRouteSpec produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    private StatusRouteSpec statusRoute(HttpStatus status, HandlerMethod<?> handler) {
        checkOpen();
        // global, also when declared in a group
        RouteAssembly.DefaultStatusRoute route = assembly.addStatusRoute(null, status, handle(handler));
        return new StatusRouteSpec() {
            @Override
            public StatusRouteSpec produces(MediaType... mediaTypes) {
                route.produces(mediaTypes);
                return this;
            }
        };
    }

    private HandlerUriRoute route(String httpMethodName, String uri, HandlerMethod<?> handler) {
        Objects.requireNonNull(httpMethodName, "httpMethodName");
        HttpMethod method = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        String name = method == HttpMethod.CUSTOM ? httpMethodName : method.name();
        return grouped(assembly.addRoute(name, method, uri(uri), DEFAULT_CONSUMES, handle(handler)));
    }

    private HandlerUriRoute route(HttpMethod method, String uri, HandlerMethod<?> handler, MediaType @Nullable [] consumes) {
        RouteAssembly.DefaultUriRoute route = assembly.addRoute(method.name(), method, uri(uri), DEFAULT_CONSUMES, handle(handler));
        return grouped(consumes == null ? route : route.consumes(consumes));
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
    private static final class Routes implements HttpRouteSpec, ContextFilterSpec<HttpRouteSpec> {

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
        public HttpRouteSpec before(ContextRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec before(String executorName, ContextRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.before(executorName, filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec beforeAsync(AsyncContextRouteRequestFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.beforeAsync(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec after(ContextRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec after(String executorName, ContextRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.after(executorName, filter);
            }
            return this;
        }

        @Override
        public HttpRouteSpec afterAsync(AsyncContextRouteResponseFilter filter) {
            for (HandlerUriRoute route : routes) {
                route.afterAsync(filter);
            }
            return this;
        }
    }
}
