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

import io.micronaut.context.env.PropertyPlaceholderResolver;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.RouteArguments;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteLocator;
import io.micronaut.web.router.RouteTable;
import io.micronaut.websocket.route.WebSocketRouteEndpoint;
import io.micronaut.websocket.route.WebSocketRouteSpec;
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
abstract sealed class AbstractHttpRouteBuilder implements HttpRouteBuilder permits DefaultHttpRouteBuilder, DefaultHttpRouteGroup, DefaultLocatedHttpRouteBuilder {

    private static final MediaType[] FORM_MEDIA_TYPES = {MediaType.APPLICATION_FORM_URLENCODED_TYPE, MediaType.MULTIPART_FORM_DATA_TYPE};
    private static final List<MediaType> DEFAULT_CONSUMES = List.of(MediaType.APPLICATION_JSON_TYPE);

    final RouteAssembly assembly;
    /**
     * The filters of the group the routes are declared in, or {@code null} outside a group.
     */
    final RouteAssembly.@Nullable RouteFilters groupFilters;
    /**
     * The other settings of the group the routes are declared in, or {@code null} outside a group.
     */
    final RouteAssembly.@Nullable RouteGroup groupSettings;
    /**
     * The prefix of the URI templates of the routes, or {@code null}.
     */
    private final @Nullable RoutePrefix prefix;
    /**
     * Resolves the placeholders of the ports given as strings, or {@code null} without an environment.
     */
    private final @Nullable PropertyPlaceholderResolver placeholderResolver;
    /**
     * Whether the routes of the builder were read: see {@link DefaultHttpRouteBuilder#close()}.
     */
    private boolean closed;

    /**
     * @param assembly     The assembly the routes are added to
     * @param groupFilters  The filters of the group the routes are declared in, or {@code null}
     * @param groupSettings The other settings of the group the routes are declared in, or {@code null}
     * @param prefix        The prefix of the URI templates of the routes, or {@code null}
     * @param placeholderResolver Resolves the placeholders of the ports given as strings, or {@code null}
     */
    AbstractHttpRouteBuilder(RouteAssembly assembly,
                             RouteAssembly.@Nullable RouteFilters groupFilters,
                             RouteAssembly.@Nullable RouteGroup groupSettings,
                             @Nullable RoutePrefix prefix,
                             @Nullable PropertyPlaceholderResolver placeholderResolver) {
        this.assembly = assembly;
        this.groupFilters = groupFilters;
        this.groupSettings = groupSettings;
        this.prefix = prefix;
        this.placeholderResolver = placeholderResolver;
    }

    /**
     * @param port A port given as a string, e.g. {@code ${my.admin.port}}
     * @return The port, see {@link RouteArguments#port(String, PropertyPlaceholderResolver)}
     */
    final int resolvePort(String port) {
        return RouteArguments.port(port, placeholderResolver);
    }

    /**
     * @return The media types and the executor of the group the routes are declared in, which
     * they inherit, or {@code null} outside a group
     */
    @Nullable RouteGroupDefaults groupDefaults() {
        return null;
    }

    /**
     * @param routes The routes of a handler
     * @return Their spec
     */
    private HttpRouteSpec spec(HandlerUriRoute... routes) {
        return spec(0, routes);
    }

    /**
     * @param own    The settings the routes have of their own, which they do not inherit from
     *               their group, e.g. {@link RouteGroupDefaults#CONSUMES} for a form handler
     * @param routes The routes of a handler
     * @return Their spec
     */
    final HttpRouteSpec spec(int own, HandlerUriRoute... routes) {
        List<HandlerUriRoute> handlerRoutes = List.of(routes);
        RouteGroupDefaults defaults = groupDefaults();
        return new DefaultHttpRouteSpec(handlerRoutes, this::resolvePort, defaults == null ? null : defaults.add(handlerRoutes, own));
    }

    @Override
    public final HttpRouteSpec handle(HttpMethod method, String uri, RequestHandler handler) {
        return spec(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final <B> HttpRouteSpec handle(HttpMethod method, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        // the body argument is annotated @Body
        return spec(route(method, uri, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncRequestHandler handler) {
        return spec(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(HttpMethod method, String uri, AsyncBodyRequestHandler handler) {
        return spec(route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleForm(HttpMethod method, String uri, FormRequestHandler handler) {
        return spec(RouteGroupDefaults.CONSUMES, route(method, uri, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
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
    public final HttpRouteSpec handleAsync(Set<HttpMethod> methods, String uri, AsyncBodyRequestHandler handler) {
        return forEach(methods, uri, method -> route(method, uri, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handle(RouteDeclaration route, RequestHandler handler) {
        return spec(declare(route, HandlerMethod.of(handler), null));
    }

    @Override
    public final <B> HttpRouteSpec handle(RouteDeclaration route, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return spec(declare(route, HandlerMethod.of(bodyType, handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(RouteDeclaration route, AsyncRequestHandler handler) {
        return spec(declare(route, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleAsync(RouteDeclaration route, AsyncBodyRequestHandler handler) {
        return spec(declare(route, HandlerMethod.of(handler), null));
    }

    @Override
    public final HttpRouteSpec handleForm(RouteDeclaration route, FormRequestHandler handler) {
        return spec(RouteGroupDefaults.CONSUMES, declare(route, HandlerMethod.of(handler), FORM_MEDIA_TYPES));
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
        Objects.requireNonNull(status, "status");
        return statusRoute(status, HandlerMethod.of(handler));
    }

    @Override
    public final StatusRouteSpec statusAsync(HttpStatus status, AsyncStatusRouteHandler handler) {
        Objects.requireNonNull(status, "status");
        return statusRoute(status, HandlerMethod.of(handler));
    }

    @Override
    public final HttpRouteSpec handle(String httpMethodName, String uri, RequestHandler handler) {
        return spec(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public final <B> HttpRouteSpec handle(String httpMethodName, String uri, Argument<B> bodyType, BodyRequestHandler<B> handler) {
        return spec(route(httpMethodName, uri, HandlerMethod.of(bodyType, handler)));
    }

    @Override
    public final HttpRouteSpec handleAsync(String httpMethodName, String uri, AsyncRequestHandler handler) {
        return spec(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public final HttpRouteSpec handleAsync(String httpMethodName, String uri, AsyncBodyRequestHandler handler) {
        return spec(route(httpMethodName, uri, HandlerMethod.of(handler)));
    }

    @Override
    public final HttpRouteSpec handleForm(String httpMethodName, String uri, FormRequestHandler handler) {
        return spec(RouteGroupDefaults.CONSUMES, route(httpMethodName, uri, HandlerMethod.of(handler)).consumes(FORM_MEDIA_TYPES));
    }

    @Override
    public final HttpRouteSpec webSocket(String uri, Consumer<WebSocketRouteSpec> endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        String template = uri(uri);
        WebSocketRouteEndpoint webSocket = WebSocketRouteEndpoint.of(template, endpoint);
        // the route of the upgrade request: the server finds the endpoint in its attribute
        HandlerUriRoute route = grouped(assembly.addRoute(HttpMethod.GET.name(), HttpMethod.GET, template, DEFAULT_CONSUMES,
            handle(HandlerMethod.webSocket(webSocket, WebSocketRouteEndpoint.ROUTE_METADATA))));
        route.attribute(WebSocketRouteEndpoint.ROUTE_ATTRIBUTE, webSocket);
        // the upgrade request has no body and the connection no response body: the media types
        // of a group do not apply, its executor runs the handlers of the connections
        return spec(RouteGroupDefaults.CONSUMES | RouteGroupDefaults.PRODUCES, route);
    }

    @Override
    public final <T> void locate(String prefixUri, LocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables) {
        locate(prefixUri, new RouteLocator(locator, tables));
    }

    @Override
    public final <T> void locateAsync(String prefixUri, AsyncLocatorHandler<? extends T> locator, Function<? super T, RouteTable> tables) {
        locate(prefixUri, new RouteLocator(locator, tables));
    }

    private void locate(String prefixUri, RouteLocator locator) {
        Objects.requireNonNull(prefixUri, "prefixUri");
        checkOpen();
        MethodExecutionHandle<Object, Object> target = handle(HandlerMethod.of(locator));
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
        DefaultHttpRouteGroup group = new DefaultHttpRouteGroup(assembly, assembly.groupFilters(groupFilters),
            assembly.routeGroup(groupSettings), new RouteGroupDefaults(groupDefaults()), groupPrefix, placeholderResolver);
        try {
            routes.accept(group);
        } finally {
            // the filters of the group are the ones declared in its lambda
            group.close();
        }
    }

    /**
     * Close the builder: its routes were read. A later declaration would be dropped, so it fails.
     */
    final void closeBuilder() {
        closed = true;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("The route builder is closed: declare the routes inside HttpRoutes.routes(...), "
                + "or inside the callback that builds the route table, not after it returned");
        }
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
        if (filters != null) {
            route.inGroup(filters);
        }
        RouteAssembly.RouteGroup settings = groupSettings;
        if (settings != null) {
            route.inGroup(settings);
        }
        return route;
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
        // global on the builder, local to the routes of the group in a group
        RouteAssembly.RouteGroup settings = groupSettings;
        RouteAssembly.DefaultErrorRoute route = settings == null
            ? assembly.addErrorRoute(null, type, handle(handler))
            : settings.addErrorRoute(type, handle(handler));
        return new DefaultErrorRouteSpec(route, handler);
    }

    private StatusRouteSpec statusRoute(HttpStatus status, HandlerMethod<?> handler) {
        checkOpen();
        // global on the builder, local to the routes of the group in a group
        RouteAssembly.RouteGroup settings = groupSettings;
        RouteAssembly.DefaultStatusRoute route = settings == null
            ? assembly.addStatusRoute(null, status, handle(handler))
            : settings.addStatusRoute(status, handle(handler));
        return new DefaultStatusRouteSpec(route, handler);
    }

    private HandlerUriRoute route(String httpMethodName, String uri, HandlerMethod<?> handler) {
        RouteArguments.httpMethodName(httpMethodName);
        HttpMethod method = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        String name = method == HttpMethod.CUSTOM ? httpMethodName : method.name();
        return grouped(assembly.addRoute(name, method, uri(uri), DEFAULT_CONSUMES, handle(handler)));
    }

    private HandlerUriRoute route(HttpMethod method, String uri, HandlerMethod<?> handler, MediaType @Nullable [] consumes) {
        standardMethod(method);
        RouteAssembly.DefaultUriRoute route = assembly.addRoute(method.name(), method, uri(uri), DEFAULT_CONSUMES, handle(handler));
        return grouped(consumes == null ? route : route.consumes(consumes));
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handle(HandlerMethod<?> method) {
        return (MethodExecutionHandle<Object, Object>) method;
    }

    private HttpRouteSpec forEach(Set<HttpMethod> methods, String uri, Function<HttpMethod, HandlerUriRoute> route) {
        Objects.requireNonNull(methods, "methods");
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No HTTP method for route: " + uri);
        }
        for (HttpMethod method : methods) {
            // before any route is added
            standardMethod(Objects.requireNonNull(method, "methods must not contain null"));
        }
        List<HandlerUriRoute> routes = new ArrayList<>(methods.size());
        for (HttpMethod method : methods) {
            routes.add(route.apply(method));
        }
        return spec(routes.toArray(new HandlerUriRoute[0]));
    }

    /**
     * @param method The HTTP method of a route
     * @throws NullPointerException     if it is {@code null}
     * @throws IllegalArgumentException if it is {@link HttpMethod#CUSTOM}, which has no name
     */
    private static void standardMethod(HttpMethod method) {
        Objects.requireNonNull(method, "method");
        RouteArguments.standardMethod(method, "handle(\"PROPFIND\", uri, handler)");
    }

    /**
     * The media types given to a route: a copy, without {@code null}.
     *
     * @param mediaTypes The media types
     * @return A copy
     */
    static MediaType[] mediaTypes(MediaType[] mediaTypes) {
        Objects.requireNonNull(mediaTypes, "mediaTypes");
        MediaType[] copy = mediaTypes.clone();
        for (MediaType mediaType : copy) {
            Objects.requireNonNull(mediaType, "mediaTypes must not contain null");
        }
        return copy;
    }
}
