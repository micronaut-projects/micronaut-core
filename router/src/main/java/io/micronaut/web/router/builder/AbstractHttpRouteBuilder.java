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
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.web.router.AnyMethodRoutes;
import io.micronaut.web.router.RouteArguments;
import io.micronaut.web.router.RouteAssembly;
import io.micronaut.web.router.RouteLocator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Adds the routes to handler functions to a {@link RouteAssembly}: the routes of the builder of
 * the {@link HttpRoutes} beans, see {@link DefaultHttpRouteBuilder}, of the {@link LocatedRoutes}
 * of located targets, see {@link DefaultLocatedHttpRouteBuilder}, and of a
 * group of routes, see {@link DefaultHttpRouteGroup}, which prefixes their URIs and applies its
 * filters to them.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract sealed class AbstractHttpRouteBuilder implements HttpRouteScope permits DefaultHttpRouteBuilder, AbstractHttpRouteGroup, DefaultLocatedHttpRouteBuilder {

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
     * The routes declared on the builder that were not ended with a terminal yet.
     */
    private final List<PendingRoute> pending = new ArrayList<>(0);
    /**
     * The bean that declares the routes, for the messages, or {@code null}: see
     * {@link #declaredBy(Class)}; a group has the one of its builder.
     */
    private @Nullable Class<?> declaringBean;

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
        checkPort();
        return RouteArguments.port(port, placeholderResolver);
    }

    /**
     * @return Whether the routes are the routes of located targets, see {@link LocatedRoutes}
     */
    boolean located() {
        return false;
    }

    /**
     * Fail if the routes cannot open a port: located routes are on the ports of their locator routes.
     *
     * @throws IllegalArgumentException if they cannot
     */
    final void checkPort() {
        if (located()) {
            throw new IllegalArgumentException("Located routes cannot expose ports: they are on the ports of their locator routes");
        }
    }

    /**
     * @return The media types and the executor of the group the routes are declared in, which
     * they inherit, or {@code null} outside a group
     */
    @Nullable RouteGroupDefaults groupDefaults() {
        return null;
    }

    /**
     * @param own    The settings the routes have of their own, which they do not inherit from
     *               their group, e.g. {@link RouteGroupDefaults#CONSUMES} for a form handler
     * @param routes The routes of a handler
     * @return Their routes, to give the settings of their spec
     */
    final HandlerRoutes routes(int own, List<RouteSettings> routes) {
        RouteGroupDefaults defaults = groupDefaults();
        return new HandlerRoutes(routes, defaults == null ? null : defaults.add(routes, own));
    }

    @Override
    public HttpRouteSpec route(HttpMethod method, String uri) {
        standardMethod(method);
        return pending(method.name(), uri, (template, handler) -> List.of(route(method, template, handler.get())));
    }

    @Override
    public HttpRouteSpec route(Set<HttpMethod> methods, String uri) {
        Objects.requireNonNull(methods, "methods");
        if (methods.isEmpty()) {
            throw new IllegalArgumentException("No HTTP method for route: " + uri);
        }
        for (HttpMethod method : methods) {
            // before the route is declared
            standardMethod(Objects.requireNonNull(method, "methods must not contain null"));
        }
        List<HttpMethod> declared = List.copyOf(methods);
        StringJoiner names = new StringJoiner(", ");
        for (HttpMethod method : declared) {
            names.add(method.name());
        }
        return pending(names.toString(), uri, (template, handler) -> {
            List<RouteSettings> routes = new ArrayList<>(declared.size());
            for (HttpMethod method : declared) {
                routes.add(route(method, template, handler.get()));
            }
            return routes;
        });
    }

    @Override
    public HttpRouteSpec route(String httpMethodName, String uri) {
        RouteArguments.httpMethodName(httpMethodName);
        HttpMethod method = HttpMethod.parse(httpMethodName);
        // a standard method by its canonical name, a custom one by the given name
        String name = method == HttpMethod.CUSTOM ? httpMethodName : method.name();
        return pending(name, uri, (template, handler) ->
            List.of(grouped(assembly.addRoute(name, method, template, DEFAULT_CONSUMES, handle(handler.get())).settings())));
    }

    @Override
    public HttpRouteSpec any(String uri) {
        return pending("any", uri, this::anyMethod);
    }

    /**
     * Declare a pending route, which its terminal adds.
     *
     * @param methods Describes the methods of the route, for the messages
     * @param uri     The URI template of the route, relative to the prefix of the builder
     * @param routes  Adds the routes of a handler, given the URI template under the prefix
     * @return The spec of the pending route
     */
    private HttpRouteSpec pending(String methods, String uri, BiFunction<String, Supplier<HandlerMethod<?>>, List<RouteSettings>> routes) {
        String template = uri(uri);
        Class<?> bean = declaringBean;
        String description = methods + " " + template + (bean == null ? "" : " declared by " + beanName(bean));
        PendingRoute route = new PendingRoute(this, handler -> routes.apply(template, handler), description);
        pending.add(route);
        return new DefaultHttpRouteSpec(route);
    }

    /**
     * Add the routes of a pending route that its terminal ended.
     *
     * @param route  The pending route
     * @param routes Adds its routes
     * @param init   Gives a route the settings of the terminal, or {@code null}
     * @param own    The settings of the terminal, which the routes do not inherit from their groups
     * @return The routes, to give the settings of the spec
     */
    final HandlerRoutes addRoutes(PendingRoute route, Supplier<List<RouteSettings>> routes, @Nullable Consumer<RouteSettings> init, int own) {
        checkOpen();
        pending.remove(route);
        List<RouteSettings> added = routes.get();
        if (init != null) {
            for (RouteSettings settings : added) {
                init.accept(settings);
            }
        }
        return routes(own, added);
    }

    /**
     * Drop a pending route whose terminal failed.
     *
     * @param route The pending route
     */
    final void dropPending(PendingRoute route) {
        pending.remove(route);
    }

    /**
     * The routes of any method: one per standard method and one for the custom methods, see
     * {@link AnyMethodRoutes}.
     *
     * @param template The URI template, under the prefix
     * @param handler  Creates the handler method of a route
     * @return The routes
     */
    private List<RouteSettings> anyMethod(String template, Supplier<HandlerMethod<?>> handler) {
        List<RouteSettings> routes = new ArrayList<>(HttpMethod.values().length);
        for (HttpMethod method : HttpMethod.values()) {
            if (method != HttpMethod.CUSTOM) {
                routes.add(route(method, template, handler.get()));
            }
        }
        routes.add(grouped(assembly.addRoute(AnyMethodRoutes.CUSTOM_METHODS, HttpMethod.CUSTOM, template, DEFAULT_CONSUMES,
            handle(handler.get())).settings()));
        for (RouteSettings route : routes) {
            route.anyMethod();
        }
        return routes;
    }

    /**
     * Name the bean that declares the routes of the builder, in the messages of the routes it
     * does not end with a terminal.
     *
     * @param bean The class of the bean, or {@code null}
     */
    final void declaredBy(@Nullable Class<?> bean) {
        this.declaringBean = bean;
    }

    /**
     * Fail if a route declared on the builder was not ended with a terminal.
     *
     * @throws IllegalStateException naming the routes that were not
     */
    final void checkEnded() {
        if (pending.isEmpty()) {
            return;
        }
        StringJoiner routes = new StringJoiner(", ");
        for (PendingRoute route : pending) {
            routes.add(route.description());
        }
        boolean one = pending.size() == 1;
        pending.clear();
        throw new IllegalStateException((one ? "The route " : "The routes ") + routes
            + (one ? " has no handler: end it" : " have no handler: end each") + " with handle, handleAsync or respond");
    }

    private static String beanName(Class<?> bean) {
        String name = bean.getName();
        // a nested class by its simple names: ItemRoutes, or Routes.Items
        return name.substring(name.lastIndexOf('.') + 1).replace('$', '.');
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
    public final <T> void locate(String prefixUri, LocatorHandler<? extends T> locator,
                                 Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
        locate(prefixUri, new RouteLocator(locator, routesOf, assembly.locatedTables(), locatorGroupSettings()));
    }

    @Override
    public final <T> void locateAsync(String prefixUri, AsyncLocatorHandler<? extends T> locator,
                                      Function<? super T, ? extends LocatedRoutes<?>> routesOf) {
        locate(prefixUri, new RouteLocator(locator, routesOf, assembly.locatedTables(), locatorGroupSettings()));
    }

    /**
     * The media types and the executor of the groups of a locator route, which the routes it
     * locates inherit: they are given to the settings when the outermost group is closed, like
     * to the routes of the groups.
     *
     * @return The settings, or {@code null} outside a group
     */
    private @Nullable RouteSettings locatorGroupSettings() {
        RouteGroupDefaults defaults = groupDefaults();
        if (defaults == null) {
            return null;
        }
        RouteSettings settings = new RouteSettings(port -> { }, null);
        defaults.add(List.of(settings), 0);
        return settings;
    }

    private void locate(String prefixUri, RouteLocator locator) {
        Objects.requireNonNull(prefixUri, "prefixUri");
        RoutePrefix.checkPath(prefixUri, "a locator route");
        checkOpen();
        MethodExecutionHandle<Object, Object> target = handle(HandlerMethod.of(locator));
        for (String template : RouteLocator.templates(uri(prefixUri))) {
            for (HttpMethod method : HttpMethod.values()) {
                if (method != HttpMethod.CUSTOM) {
                    // the routes of the target decide which media types they consume and produce, inheriting
                    // the ones of the groups; the located route carries the filters of the groups of the locator route
                    grouped(assembly.addRoute(method.name(), method, template, DEFAULT_CONSUMES, target).settings()).consumesAll();
                }
            }
            // a request with a custom method is located too, and matched against the located
            // routes of its method, or of any method
            grouped(assembly.addRoute(AnyMethodRoutes.CUSTOM_METHODS, HttpMethod.CUSTOM, template, DEFAULT_CONSUMES, target).settings()).consumesAll();
        }
    }

    /**
     * Declare a group of routes, see {@link HttpRouteBuilder#group(Consumer)}.
     *
     * @param factory Creates the group
     * @param routes  Declares the routes and the filters of the group
     * @param <G>     The type of the group
     */
    final <G extends AbstractHttpRouteGroup<?>> void declareGroup(GroupFactory<G> factory, Consumer<? super G> routes) {
        Objects.requireNonNull(routes, "routes");
        declareGroup(prefix, factory, routes);
    }

    /**
     * Declare a group of routes under a prefix, see {@link HttpRouteBuilder#path(String, Consumer)}.
     *
     * @param groupPrefix The prefix of the group, under the prefix of the builder
     * @param factory     Creates the group
     * @param routes      Declares the routes and the filters of the group
     * @param <G>         The type of the group
     */
    final <G extends AbstractHttpRouteGroup<?>> void declarePath(String groupPrefix, GroupFactory<G> factory, Consumer<? super G> routes) {
        Objects.requireNonNull(routes, "routes");
        declareGroup(RoutePrefix.of(groupPrefix, this.prefix), factory, routes);
    }

    private <G extends AbstractHttpRouteGroup<?>> void declareGroup(@Nullable RoutePrefix groupPrefix, GroupFactory<G> factory,
                                                                    Consumer<? super G> routes) {
        checkOpen();
        G group = factory.create(assembly, assembly.groupFilters(groupFilters), assembly.routeGroup(groupSettings),
            new RouteGroupDefaults(groupDefaults()), groupPrefix, placeholderResolver);
        group.declaredBy(declaringBean);
        try {
            routes.accept(group);
            // the routes of the group are ended in its lambda
            group.checkEnded();
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

    final void checkOpen() {
        if (closed) {
            throw new IllegalStateException("The route builder is closed: declare the routes inside "
                + (located() ? "LocatedRoutes" : "HttpRoutes") + ".routes(...), not after it returned");
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

    private RouteSettings grouped(RouteSettings route) {
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

    private ErrorRouteSpec errorRoute(Class<? extends Throwable> type, HandlerMethod<?> handler) {
        checkOpen();
        // global on the builder, local to the routes of the group in a group
        RouteAssembly.RouteGroup settings = groupSettings;
        if (settings == null) {
            checkGlobal("error");
        }
        RouteAssembly.DefaultErrorRoute route = settings == null
            ? assembly.addErrorRoute(null, type, handle(handler))
            : settings.addErrorRoute(type, handle(handler));
        return new DefaultErrorRouteSpec(route, handler);
    }

    private StatusRouteSpec statusRoute(HttpStatus status, HandlerMethod<?> handler) {
        checkOpen();
        // global on the builder, local to the routes of the group in a group
        RouteAssembly.RouteGroup settings = groupSettings;
        if (settings == null) {
            checkGlobal("status");
        }
        RouteAssembly.DefaultStatusRoute route = settings == null
            ? assembly.addStatusRoute(null, status, handle(handler))
            : settings.addStatusRoute(status, handle(handler));
        return new DefaultStatusRouteSpec(route, handler);
    }

    /**
     * Fail if the routes cannot declare a global error or status route: located routes declare
     * them in a group, local to the routes of the group.
     *
     * @param kind The kind of the route, for the message
     * @throws IllegalArgumentException if they cannot
     */
    private void checkGlobal(String kind) {
        if (located()) {
            throw new IllegalArgumentException("Located routes cannot declare global " + kind + " routes, which belong to the application routes: "
                + "declare them in a group of the located routes, local to its routes, e.g. routes.group(group -> group." + kind + "(...))");
        }
    }

    /**
     * @param method   The HTTP method
     * @param template The URI template, under the prefix
     * @param handler  The handler method
     * @return The route
     */
    private RouteSettings route(HttpMethod method, String template, HandlerMethod<?> handler) {
        return grouped(assembly.addRoute(method.name(), method, template, DEFAULT_CONSUMES, handle(handler)).settings());
    }

    @SuppressWarnings("unchecked")
    private static MethodExecutionHandle<Object, Object> handle(HandlerMethod<?> method) {
        return (MethodExecutionHandle<Object, Object>) method;
    }

    /**
     * @param method The HTTP method of a route
     * @throws NullPointerException     if it is {@code null}
     * @throws IllegalArgumentException if it is {@link HttpMethod#CUSTOM}, which has no name
     */
    private static void standardMethod(HttpMethod method) {
        Objects.requireNonNull(method, "method");
        RouteArguments.standardMethod(method, "route(\"PROPFIND\", uri)");
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

    /**
     * Creates a group of routes.
     *
     * @param <G> The type of the group
     */
    @FunctionalInterface
    interface GroupFactory<G extends AbstractHttpRouteGroup<?>> {

        /**
         * @param assembly            The assembly the routes are added to
         * @param filters             The filters of the group
         * @param settings            The other settings of the group
         * @param defaults            The media types and the executor of the group
         * @param prefix              The prefix of the URI templates of the routes, or {@code null}
         * @param placeholderResolver Resolves the placeholders of the ports given as strings, or {@code null}
         * @return The group
         */
        G create(RouteAssembly assembly, RouteAssembly.RouteFilters filters, RouteAssembly.RouteGroup settings,
                 RouteGroupDefaults defaults, @Nullable RoutePrefix prefix, @Nullable PropertyPlaceholderResolver placeholderResolver);
    }
}
