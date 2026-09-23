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
package io.micronaut.web.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ImmediateExecutor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RouteCondition;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.filter.FilterPatternStyle;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.builder.AsyncContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.AsyncContextReplacingRouteRequestFilter;
import io.micronaut.web.router.builder.ContextReplacingRouteResponseFilter;
import io.micronaut.web.router.builder.ContextReplacingRouteRequestFilter;
import io.micronaut.web.router.builder.DeclaredUriRoute;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HandlerUriRoute;
import io.micronaut.web.router.builder.DefaultRouteAnnotations;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import io.micronaut.web.router.exceptions.RoutingException;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;


/**
 * Assembles the routes of a route builder into route infos: the machinery shared by the route
 * builders. The legacy {@link DefaultRouteBuilder} and the routes to handler functions of
 * {@link io.micronaut.web.router.builder.HttpRouteBuilder} both add their routes here, and the
 * router reads them from here.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class RouteAssembly {

    final ConversionService conversionService;
    final Charset defaultCharset;
    final List<UriRoute> uriRoutes = new ArrayList<>();
    final List<StatusRoute> statusRoutes = new ArrayList<>();
    final List<ErrorRoute> errorRoutes = new ArrayList<>();
    final Set<Integer> exposedPorts = new HashSet<>(5);
    @Nullable DefaultUriRoute currentParentRoute;
    private final @Nullable ExecutorSelector executorSelector;
    private final ThreadSelection threadSelection;
    private final MessageBodyHandlerRegistry messageBodyHandlerRegistry;
    private final UnaryOperator<String> routeUri;
    private final Consumer<DefaultUriRoute> routeCreated;
    private final List<DeclaredUriRoute> declaredRoutes = new ArrayList<>(0);
    private final List<DeclaredUriRoute> implicitHeadDeclaredRoutes = new ArrayList<>(0);
    /**
     * Whether the literal path the routes that are not nested are mounted under is known, see
     * {@link #mountPrefix}: only then can templates of engines other than the Micronaut one be
     * mounted, since {@link #routeUri} composes Micronaut templates only.
     */
    private final boolean mountKnown;
    /**
     * The literal path the routes that are not nested are mounted under, e.g. the context path,
     * or {@code null} for none.
     */
    private final @Nullable String mountPrefix;
    private final List<ServerFilters> serverFilters = new ArrayList<>(0);
    private final @Nullable String contextPath;

    /**
     * @param beanLocator       The locator of the application beans: the executor selector and the message body handlers
     * @param conversionService The conversion service
     * @param routeUri          The URI template of a route that is not nested in another route, e.g. under the context path
     * @param routeCreated      Called for every URI route when it is created, before any further configuration of it
     */
    public RouteAssembly(@Nullable Object beanLocator,
                         ConversionService conversionService,
                         UnaryOperator<String> routeUri,
                         Consumer<DefaultUriRoute> routeCreated) {
        this(beanLocator, conversionService, routeUri, routeCreated, null);
    }

    /**
     * @param beanLocator       The locator of the application beans: the executor selector and the message body handlers
     * @param conversionService The conversion service
     * @param routeUri          The URI template of a route that is not nested in another route, e.g. under the context path
     * @param routeCreated      Called for every URI route when it is created, before any further configuration of it
     * @param contextPath       The context path the patterns of the server filters are under, or {@code null}
     */
    public RouteAssembly(@Nullable Object beanLocator,
                         ConversionService conversionService,
                         UnaryOperator<String> routeUri,
                         Consumer<DefaultUriRoute> routeCreated,
                         @Nullable String contextPath) {
        this(beanLocator, conversionService, routeUri, routeCreated, false, null, contextPath);
    }

    /**
     * An assembly of routes under a context path. The context path is a literal mount: the
     * templates of every engine are mounted under it by their engine. The patterns of the server
     * filters are under it too.
     *
     * @param beanLocator       The locator of the application beans: the executor selector and the message body handlers
     * @param conversionService The conversion service
     * @param contextPath       The context path, e.g. the {@code micronaut.server.context-path} property, or {@code null}
     * @param routeCreated      Called for every URI route when it is created, before any further configuration of it
     */
    public RouteAssembly(@Nullable Object beanLocator,
                         ConversionService conversionService,
                         @Nullable String contextPath,
                         Consumer<DefaultUriRoute> routeCreated) {
        this(beanLocator, conversionService, uri -> underContextPath(contextPath, uri), routeCreated, true, mountPrefix(contextPath), contextPath);
    }

    @SuppressWarnings("ParameterNumber")
    private RouteAssembly(@Nullable Object beanLocator,
                          ConversionService conversionService,
                          UnaryOperator<String> routeUri,
                          Consumer<DefaultUriRoute> routeCreated,
                          boolean mountKnown,
                          @Nullable String mountPrefix,
                          @Nullable String contextPath) {
        this.contextPath = contextPath;
        this.conversionService = conversionService;
        this.routeUri = routeUri;
        this.routeCreated = routeCreated;
        this.mountKnown = mountKnown;
        this.mountPrefix = mountPrefix;
        if (beanLocator instanceof ApplicationContext applicationContext) {
            Environment environment = applicationContext.getEnvironment();
            defaultCharset = environment.get("micronaut.application.default-charset", Charset.class, StandardCharsets.UTF_8);
            this.executorSelector = applicationContext.findBean(ExecutorSelector.class).orElse(null);
            // like HttpServerConfiguration, which the router does not depend on
            this.threadSelection = environment.get("micronaut.server.thread-selection", ThreadSelection.class).orElse(ThreadSelection.MANUAL);
            this.messageBodyHandlerRegistry = applicationContext.findBean(MessageBodyHandlerRegistry.class).orElse(MessageBodyHandlerRegistry.EMPTY);
        } else {
            defaultCharset = StandardCharsets.UTF_8;
            this.executorSelector = null;
            this.threadSelection = ThreadSelection.MANUAL;
            this.messageBodyHandlerRegistry = MessageBodyHandlerRegistry.EMPTY;
        }
    }

    /**
     * @return The URI routes
     */
    public List<UriRoute> uriRoutes() {
        return Collections.unmodifiableList(uriRoutes);
    }

    /**
     * @return The status routes
     */
    public List<StatusRoute> statusRoutes() {
        return Collections.unmodifiableList(statusRoutes);
    }

    /**
     * @return The error routes
     */
    public List<ErrorRoute> errorRoutes() {
        return Collections.unmodifiableList(errorRoutes);
    }

    /**
     * The server filters declared with {@link #addServerFilter(String...)}, one filter route per
     * filter, as they are declared now.
     *
     * @return The filter routes
     */
    public List<FilterRoute> filterRoutes() {
        if (serverFilters.isEmpty()) {
            return List.of();
        }
        List<FilterRoute> routes = new ArrayList<>();
        for (ServerFilters filters : serverFilters) {
            filters.addFilterRoutes(routes);
        }
        return routes;
    }

    /**
     * Add a server filter, like a {@code @ServerFilter} bean.
     *
     * @param patterns The patterns of the paths it filters
     * @return The server filter, to add its filters to
     */
    public ServerFilters addServerFilter(String... patterns) {
        ServerFilters filters = new ServerFilters(patterns);
        serverFilters.add(filters);
        return filters;
    }

    /**
     * @return The ports the routes are exposed on, a read-only view
     */
    public Set<Integer> exposedPorts() {
        return Collections.unmodifiableSet(exposedPorts);
    }

    /**
     * Add a URI route: nested in the current parent route, or with the URI template of a route
     * that is not nested.
     *
     * @param httpMethodName   The name of the HTTP method, which differs from {@link HttpMethod#name()} for a custom method
     * @param httpMethod       The HTTP method
     * @param uri              The URI template
     * @param mediaTypes       The media types the route consumes
     * @param executableHandle The target of the route
     * @return The route
     */
    public DefaultUriRoute addRoute(String httpMethodName, HttpMethod httpMethod, String uri, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle) {
        DefaultUriRoute route;
        if (currentParentRoute != null) {
            UriMatchTemplate parentTemplate = currentParentRoute.uriMatchTemplate;
            if (parentTemplate == null) {
                // nesting across engines: rejected
                RouteTemplateEngines.defaults().nest(currentParentRoute.template, MicronautRouteTemplateEngine.INSTANCE.parse(RouteTemplate.micronaut(uri)));
                throw new IllegalStateException("Nesting across route template engines");
            }
            route = new DefaultUriRoute(
                httpMethod,
                parentTemplate.nest(uri),
                mediaTypes,
                executableHandle,
                httpMethodName,
                conversionService
            );
            currentParentRoute.nestedRoutes.add(route);
        } else {
            route = new DefaultUriRoute(httpMethod, routeUri.apply(uri), mediaTypes, executableHandle, httpMethodName, conversionService);
        }
        uriRoutes.add(route);
        routeCreated.accept(route);
        return route;
    }

    /**
     * Add a URI route with a template of any engine: nested in the current parent route by the
     * engine, or mounted under the context path by the engine.
     *
     * @param httpMethodName   The name of the HTTP method, which differs from {@link HttpMethod#name()} for a custom method
     * @param httpMethod       The HTTP method
     * @param template         The template
     * @param mediaTypes       The media types the route consumes
     * @param executableHandle The target of the route
     * @return The route
     * @throws IllegalArgumentException if the engine of the template is not registered, or the
     *                                  route would be nested in a route of another engine
     */
    public DefaultUriRoute addRoute(String httpMethodName, HttpMethod httpMethod, RouteTemplate template, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle) {
        if (template.isMicronaut() && (currentParentRoute == null || currentParentRoute.uriMatchTemplate != null)) {
            return addRoute(httpMethodName, httpMethod, template.expression(), mediaTypes, executableHandle);
        }
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        ParsedRouteTemplate parsed = engines.parse(template);
        DefaultUriRoute route;
        if (currentParentRoute != null) {
            route = new DefaultUriRoute(httpMethod, engines.nest(currentParentRoute.template, parsed), mediaTypes, executableHandle, httpMethodName, conversionService);
            currentParentRoute.nestedRoutes.add(route);
        } else {
            route = new DefaultUriRoute(httpMethod, mount(engines, parsed), mediaTypes, executableHandle, httpMethodName, conversionService);
        }
        uriRoutes.add(route);
        routeCreated.accept(route);
        return route;
    }

    /**
     * Add the routes of a locator whose prefix is a template of an engine other than the
     * Micronaut one, one per standard HTTP method, see {@link RouteLocator}. The prefix is nested
     * in the current parent route or mounted under the context path by its engine.
     *
     * @param prefix           The prefix
     * @param mediaTypes       The media types the routes consume
     * @param executableHandle The locator
     * @return The routes
     */
    public List<DefaultUriRoute> addLocatorRoutes(RouteTemplate prefix, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle) {
        RouteTemplateEngines engines = RouteTemplateEngines.defaults();
        ParsedRouteTemplate parsed = engines.parse(prefix);
        ParsedRouteTemplate composed = RouteLocator.prefixTemplate(currentParentRoute != null
            ? engines.nest(currentParentRoute.template, parsed)
            : mount(engines, parsed));
        List<DefaultUriRoute> routes = new ArrayList<>(HttpMethod.values().length);
        for (HttpMethod method : HttpMethod.values()) {
            if (method == HttpMethod.CUSTOM) {
                continue;
            }
            DefaultUriRoute route = new DefaultUriRoute(method, composed, mediaTypes, executableHandle, method.name(), conversionService);
            if (currentParentRoute != null) {
                currentParentRoute.nestedRoutes.add(route);
            }
            uriRoutes.add(route);
            routeCreated.accept(route);
            routes.add(route);
        }
        return routes;
    }

    private ParsedRouteTemplate mount(RouteTemplateEngines engines, ParsedRouteTemplate parsed) {
        if (!mountKnown) {
            throw new IllegalArgumentException("The route template " + parsed.template() + " of the engine '" + parsed.engineId()
                + "' is not supported here: this route builder composes Micronaut URI templates only");
        }
        return mountPrefix == null ? parsed : engines.mount(mountPrefix, parsed);
    }

    /**
     * @param contextPath A context path
     * @return The literal prefix of the context path: with a leading slash, without a trailing
     * one, and {@code null} for none
     */
    private static @Nullable String mountPrefix(@Nullable String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return null;
        }
        String prefix = contextPath.charAt(0) == '/' ? contextPath : '/' + contextPath;
        while (prefix.length() > 1 && prefix.charAt(prefix.length() - 1) == '/') {
            prefix = prefix.substring(0, prefix.length() - 1);
        }
        return prefix;
    }

    /**
     * A URI route that is not added: the caller builds its route info.
     *
     * @param httpMethod       The HTTP method
     * @param uri              The URI template
     * @param mediaTypes       The media types the route consumes
     * @param executableHandle The target of the route
     * @param httpMethodName   The name of the HTTP method
     * @return The route
     */
    public DefaultUriRoute newRoute(HttpMethod httpMethod, String uri, List<MediaType> mediaTypes, MethodExecutionHandle<Object, Object> executableHandle, String httpMethodName) {
        return new DefaultUriRoute(httpMethod, uri, mediaTypes, executableHandle, httpMethodName, conversionService);
    }

    /**
     * Add an error route.
     *
     * @param originatingClass The class the error route is local to, or {@code null} for a global error route
     * @param error            The type of the exception
     * @param executableHandle The target of the route
     * @return The route
     */
    public DefaultErrorRoute addErrorRoute(@Nullable Class<?> originatingClass, Class<? extends Throwable> error, MethodExecutionHandle<Object, Object> executableHandle) {
        DefaultErrorRoute errorRoute = originatingClass == null
            ? new DefaultErrorRoute(error, executableHandle, conversionService)
            : new DefaultErrorRoute(originatingClass, error, executableHandle, conversionService);
        errorRoutes.add(errorRoute);
        return errorRoute;
    }

    /**
     * Add a status route.
     *
     * @param originatingClass The class the status route is local to, or {@code null} for a global status route
     * @param status           The status
     * @param executableHandle The target of the route
     * @return The route
     */
    public DefaultStatusRoute addStatusRoute(@Nullable Class<?> originatingClass, HttpStatus status, MethodExecutionHandle<Object, Object> executableHandle) {
        DefaultStatusRoute statusRoute = originatingClass == null
            ? new DefaultStatusRoute(status, executableHandle, conversionService)
            : new DefaultStatusRoute(originatingClass, status, executableHandle, conversionService);
        statusRoutes.add(statusRoute);
        return statusRoute;
    }

    /**
     * Bind a target to a declared route. A declaration with index keys is built when the router
     * first uses it; without keys, nested, or under a context path, where the keys do not
     * describe the route, it is added as an ordinary URI route.
     *
     * @param declaration      The declared route
     * @param executableHandle The target of the route
     * @param consumes         The media types the route consumes, or {@code null} for the default
     * @return The route
     */
    public HandlerUriRoute declare(RouteDeclaration declaration, MethodExecutionHandle<Object, Object> executableHandle, MediaType @Nullable [] consumes) {
        HttpMethod httpMethod = declaration.httpMethod();
        String httpMethodName = declaration.httpMethodName();
        RouteTemplate template = declaration.template();
        DeclaredUriRoute route;
        if (template.isMicronaut()) {
            String uri = template.expression();
            if (!(declaration instanceof IndexedRouteDeclaration indexed) || currentParentRoute != null || !routeUri.apply(uri).equals(uri)) {
                // no index keys, or they do not describe the route: an ordinary route
                DefaultUriRoute ordinary = addRoute(httpMethodName, httpMethod, template, List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle);
                return consumes == null ? ordinary : ordinary.consumes(consumes);
            }
            route = new DeclaredUriRoute(
                indexed,
                // the segments and index facts, without building the matcher
                () -> MicronautRouteTemplateEngine.INSTANCE.parse(template),
                () -> new DefaultUriRoute(httpMethod, uri, List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle, httpMethodName, conversionService),
                exposedPorts::add
            );
        } else {
            RouteTemplateEngines engines = RouteTemplateEngines.defaults();
            // fails now, not when the route is first used, if the engine is missing
            engines.engine(template.engineId());
            if (!(declaration instanceof IndexedRouteDeclaration indexed) || currentParentRoute != null || !mountKnown || mountPrefix != null) {
                // no index keys, or they do not describe the route: an ordinary route
                DefaultUriRoute ordinary = addRoute(httpMethodName, httpMethod, template, List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle);
                return consumes == null ? ordinary : ordinary.consumes(consumes);
            }
            Supplier<ParsedRouteTemplate> parsed = SupplierUtil.memoized(() -> engines.parse(template));
            route = new DeclaredUriRoute(
                indexed,
                parsed,
                () -> new DefaultUriRoute(httpMethod, parsed.get(), List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle, httpMethodName, conversionService),
                exposedPorts::add
            );
        }
        if (consumes != null) {
            route.consumes(consumes);
        }
        declaredRoutes.add(route);
        return route;
    }

    /**
     * The routes that are built when the router first uses them: the targets bound to declared
     * routes, and their implicit {@code HEAD} routes. Their configuration is fixed when this
     * method is called.
     *
     * @return The routes
     */
    public List<LazyUriRouteInfo> lazyRouteInfos() {
        if (declaredRoutes.isEmpty()) {
            return List.of();
        }
        List<LazyUriRouteInfo> infos = new ArrayList<>(declaredRoutes.size() + implicitHeadDeclaredRoutes.size());
        for (DeclaredUriRoute route : declaredRoutes) {
            route.fix();
            infos.add(new LazyUriRouteInfo(route.declaration(), route.declaration().httpMethod(), false,
                effectiveOrder(route.order(), route.group()), route.parsedTemplate(), route::toRouteInfo));
        }
        for (DeclaredUriRoute route : implicitHeadDeclaredRoutes) {
            infos.add(new LazyUriRouteInfo(route.declaration(), HttpMethod.HEAD, true,
                effectiveOrder(route.order(), route.group()), route.parsedTemplate(), route::implicitHeadRouteInfo));
        }
        return infos;
    }

    /**
     * Add an implicit {@code HEAD} route for every {@code GET} route that has no {@code HEAD} route
     * for the same URI, like {@link AnnotatedMethodRouteBuilder} does for {@code @Get} methods.
     * Each {@code HEAD} route is a copy of the finished {@code GET} route.
     */
    public void addImplicitHeadRoutes() {
        // keyed by the engine and the template, never by the text alone: the same text can be a
        // different template for another engine
        List<DefaultUriRoute> getRoutes = new ArrayList<>();
        Set<Object> headTemplates = new HashSet<>();
        List<DefaultUriRoute> headRoutes = new ArrayList<>();
        for (UriRoute route : uriRoutes) {
            if (route instanceof DefaultUriRoute defaultUriRoute) {
                if (defaultUriRoute.httpMethod == HttpMethod.GET) {
                    getRoutes.add(defaultUriRoute);
                } else if (defaultUriRoute.httpMethod == HttpMethod.HEAD) {
                    headTemplates.add(defaultUriRoute.headKey());
                    headRoutes.add(defaultUriRoute);
                }
            }
        }
        for (DefaultUriRoute getRoute : getRoutes) {
            if (!headTemplates.contains(getRoute.headKey())
                && getRoute.targetMethod.booleanValue(Get.class, "headRoute").orElse(true)) {
                uriRoutes.add(getRoute.implicitHeadCopy());
            }
        }
        // declared routes, compared by their templates without building them
        Set<RouteTemplate> declaredHeads = new HashSet<>();
        for (DefaultUriRoute headRoute : headRoutes) {
            declaredHeads.add(headRoute.getRouteTemplate());
        }
        for (DeclaredUriRoute route : declaredRoutes) {
            if (route.declaration().httpMethod() == HttpMethod.HEAD) {
                declaredHeads.add(route.declaration().template());
            }
        }
        for (DeclaredUriRoute route : declaredRoutes) {
            if (route.declaration().httpMethod() == HttpMethod.GET && !declaredHeads.contains(route.declaration().template())
                && !implicitHeadDeclaredRoutes.contains(route)) {
                implicitHeadDeclaredRoutes.add(route);
            }
        }
    }

    /**
     * The order of a route: its own, or the one of its group, or {@code 0}.
     *
     * @param order The order of the route, or {@code null}
     * @param group The settings of the group of the route, or {@code null}
     * @return The order
     */
    private static int effectiveOrder(@Nullable Integer order, @Nullable RouteGroup group) {
        if (order != null) {
            return order;
        }
        Integer groupOrder = group == null ? null : group.order();
        return groupOrder == null ? 0 : groupOrder;
    }

    /**
     * A URI template under a context path.
     *
     * @param contextPath The context path, e.g. the {@code micronaut.server.context-path} property
     * @param uri         The URI template
     * @return The template under the context path
     */
    public static String underContextPath(@Nullable String contextPath, String uri) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return uri;
        }
        String prefix = contextPath.charAt(0) == '/' ? contextPath : '/' + contextPath;
        return UriTemplate.of(prefix).nest(uri).toString();
    }

    /**
     * The target of a route, for its description: the declaring type and the name of a method,
     * or the description of a handler function, whose type is a generated lambda class.
     *
     * @param targetMethod The target of the route
     * @return The description
     */
    static String target(MethodExecutionHandle<?, ?> targetMethod) {
        if (targetMethod instanceof HandlerMethod<?> handlerMethod) {
            return handlerMethod.toString();
        }
        return targetMethod.getDeclaringType().getSimpleName() + '#' + targetMethod.getName();
    }

    /**
     * The filters of a group of handler routes, see {@link io.micronaut.web.router.builder.HttpRouteGroup}.
     *
     * @param enclosing The filters of the enclosing group, or {@code null}
     * @return The filters of the group
     */
    public RouteFilters groupFilters(@Nullable RouteFilters enclosing) {
        return new RouteFilters(enclosing, executorName -> new ConfigurationException(
            "No executor configured for name: " + executorName + ", of a filter of a route group"));
    }

    /**
     * The settings of a group of handler routes other than its filters, see
     * {@link io.micronaut.web.router.builder.HttpRouteGroup}.
     *
     * @param enclosing The settings of the enclosing group, or {@code null}
     * @return The settings of the group
     */
    public RouteGroup routeGroup(@Nullable RouteGroup enclosing) {
        return new RouteGroup(enclosing);
    }

    /**
     * Abstract class for base {@link MethodBasedRouteInfo}.
     */
    abstract static class AbstractRoute implements Route {
        protected final List<Predicate<HttpRequest<?>>> conditions = new ArrayList<>();
        protected final MethodExecutionHandle<Object, Object> targetMethod;
        protected final ConversionService conversionService;
        protected List<MediaType> consumesMediaTypes;
        protected List<MediaType> producesMediaTypes = List.of();
        protected @Nullable String bodyArgumentName;
        protected @Nullable Argument<?> bodyArgument;

        /**
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         * @param mediaTypes The media types
         */
        AbstractRoute(MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService, List<MediaType> mediaTypes) {
            this.targetMethod = targetMethod;
            this.conversionService = conversionService;
            this.consumesMediaTypes = mediaTypes;
            for (Argument<?> argument : targetMethod.getArguments()) {
                if (argument.getAnnotationMetadata().hasAnnotation(Body.class)) {
                    this.bodyArgument = argument;
                }
            }
        }

        @Override
        public Route consumes(MediaType... mediaTypes) {
            if (mediaTypes != null) {
                this.consumesMediaTypes = List.of(mediaTypes);
            }
            return this;
        }

        @Override
        public List<MediaType> getConsumes() {
            return consumesMediaTypes;
        }

        @Override
        public Route consumesAll() {
            this.consumesMediaTypes = Collections.emptyList();
            return this;
        }

        @Override
        public Route where(Predicate<HttpRequest<?>> condition) {
            if (condition != null) {
                conditions.add(condition);
            }
            return this;
        }

        @Override
        public Route body(String argument) {
            this.bodyArgumentName = argument;
            return this;
        }

        @Override
        public Route body(Argument<?> argument) {
            this.bodyArgument = argument;
            return this;
        }

        @Override
        public Route produces(MediaType... mediaType) {
            if (mediaType != null) {
                this.producesMediaTypes = List.of(mediaType);
            }
            return this;
        }

        @Override
        public List<MediaType> getProduces() {
            return producesMediaTypes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof AbstractRoute that)) {
                return false;
            }
            return Objects.equals(consumesMediaTypes, that.consumesMediaTypes) &&
                    Objects.equals(producesMediaTypes, that.producesMediaTypes);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(consumesMediaTypes, producesMediaTypes);
        }
    }

    /**
     * Default Error Route.
     */
    @Internal
    public final class DefaultErrorRoute extends AbstractRoute implements ErrorRoute {

        private final Class<? extends Throwable> error;
        private final @Nullable Class<?> originatingClass;

         /**
          * @param error The throwable

         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            this(null, error, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param error The throwable
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultErrorRoute(@Nullable Class<?> originatingClass,
                                 Class<? extends Throwable> error,
                                 MethodExecutionHandle<Object, Object> targetMethod,
                                 ConversionService conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.error = error;
        }

        @Override
        public ErrorRouteInfo<Object, Object> toRouteInfo() {
            return new DefaultErrorRouteInfo<>(
                    originatingClass,
                    error,
                    targetMethod,
                    bodyArgumentName,
                    bodyArgument,
                    consumesMediaTypes,
                    producesMediaTypes,
                    conditions,
                    conversionService,
                    messageBodyHandlerRegistry);
        }

        @Override
        @Nullable
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public Class<? extends Throwable> exceptionType() {
            return error;
        }

        @Override
        public ErrorRoute consumes(MediaType... mediaType) {
            return (ErrorRoute) super.consumes(mediaType);
        }

        @Override
        public ErrorRoute produces(MediaType... mediaType) {
            return (ErrorRoute) super.produces(mediaType);
        }

        @Override
        public Route consumesAll() {
            super.consumesAll();
            return this;
        }

        @Override
        public ErrorRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public ErrorRoute where(Predicate<HttpRequest<?>> condition) {
            return (ErrorRoute) super.where(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            DefaultErrorRoute that = (DefaultErrorRoute) o;
            return error.equals(that.error) &&
                    Objects.equals(originatingClass, that.originatingClass);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(super.hashCode(), error, originatingClass);
        }

        @Override
        public String toString() {
            return ' ' + error.getSimpleName()
                    + " -> " + target(targetMethod);
        }
    }

    /**
     * Represents a route for an {@link io.micronaut.http.HttpStatus} code.
     */
    @Internal
    public final class DefaultStatusRoute extends AbstractRoute implements StatusRoute {

        private final int statusCode;
        @Nullable
        private final Class<?> originatingClass;

        /**
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(HttpStatus status, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            this(null, status, targetMethod, conversionService);
        }

        /**
         * @param originatingClass The originating class
         * @param status The HTTP Status
         * @param targetMethod The target method execution handle
         * @param conversionService The conversion service
         */
        public DefaultStatusRoute(@Nullable Class<?> originatingClass, HttpStatus status, MethodExecutionHandle<Object, Object> targetMethod, ConversionService conversionService) {
            super(targetMethod, conversionService, Collections.emptyList());
            this.originatingClass = originatingClass;
            this.statusCode = status.getCode();
        }

        @Override
        public StatusRouteInfo<Object, Object> toRouteInfo() {
            return new DefaultStatusRouteInfo<>(
                    originatingClass,
                    statusCode,
                    targetMethod,
                    bodyArgumentName,
                    bodyArgument,
                    consumesMediaTypes,
                    producesMediaTypes,
                    conditions,
                    conversionService,
                    messageBodyHandlerRegistry
            );
        }

        @Override
        @Nullable
        public Class<?> originatingType() {
            return originatingClass;
        }

        @Override
        public HttpStatus status() {
            return HttpStatus.valueOf(statusCode);
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public StatusRoute consumes(MediaType... mediaType) {
            return this;
        }

        @Override
        public Route consumesAll() {
            return this;
        }

        @Override
        public StatusRoute nest(Runnable nested) {
            return this;
        }

        @Override
        public StatusRoute where(Predicate<HttpRequest<?>> condition) {
            return (StatusRoute) super.where(condition);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DefaultStatusRoute that)) {
                return false;
            }
            if (!super.equals(o)) {
                return false;
            }
            return statusCode == that.statusCode &&
                    Objects.equals(originatingClass, that.originatingClass);
        }

        @Override
        public int hashCode() {
            return ObjectUtils.hash(super.hashCode(), statusCode, originatingClass);
        }
    }

    /**
     * A server filter declared in code, like a {@code @ServerFilter} bean: its request and
     * response filters are filter routes with its patterns, methods and order.
     */
    @Internal
    public final class ServerFilters {
        private final List<String> patterns;
        private final RouteFilters filters = new RouteFilters(null, executorName -> new ConfigurationException(
            "No executor configured for name: " + executorName + ", of a server filter"));
        private HttpMethod @Nullable [] methods;
        private int order;
        private FilterPatternStyle patternStyle = FilterPatternStyle.ANT;
        private boolean appendContextPath = true;
        private boolean preMatching;

        ServerFilters(String... patterns) {
            Objects.requireNonNull(patterns, "patterns");
            if (patterns.length == 0) {
                throw new IllegalArgumentException("A filter pattern is required");
            }
            for (String pattern : patterns) {
                if (pattern == null || pattern.isEmpty()) {
                    throw new IllegalArgumentException("A filter pattern must not be empty");
                }
            }
            this.patterns = List.of(patterns);
        }

        /**
         * @return The filters, to add the request and response filters to
         */
        public RouteFilters filters() {
            return filters;
        }

        /**
         * @param methods The methods of the requests to filter
         */
        public void methods(HttpMethod... methods) {
            HttpMethod[] copy = Objects.requireNonNull(methods, "methods").clone();
            for (HttpMethod method : copy) {
                Objects.requireNonNull(method, "methods must not contain null");
            }
            this.methods = copy;
        }

        /**
         * @param order The order among the server filters
         */
        public void order(int order) {
            this.order = order;
        }

        /**
         * @param patternStyle The style of the patterns
         */
        public void patternStyle(FilterPatternStyle patternStyle) {
            this.patternStyle = Objects.requireNonNull(patternStyle, "patternStyle");
        }

        /**
         * @param appendContextPath Whether the patterns are under the context path
         */
        public void appendContextPath(boolean appendContextPath) {
            this.appendContextPath = appendContextPath;
        }

        /**
         * Run the filters before the route is matched, like filter methods annotated
         * {@code @PreMatching}. The response filters also filter the responses of the routes,
         * like filter methods that are not.
         */
        public void preMatching() {
            this.preMatching = true;
        }

        private void addFilterRoutes(List<FilterRoute> routes) {
            String path = RouteAssembly.this.contextPath;
            List<String> resolved = patterns;
            if (appendContextPath && path != null) {
                resolved = patterns.stream()
                    .map(pattern -> ServerFilterRouteBuilder.prependContextPath(path, pattern))
                    .toList();
            }
            // like the filter methods of a filter bean: a filter route per filter, all with the same
            // order, which the stable sort of the server filters keeps in the order of the chain
            for (GenericHttpFilter filter : filters.chain()) {
                routes.add(filterRoute(filter, resolved, preMatching));
            }
            if (preMatching) {
                // like a bean with a @PreMatching @ResponseFilter method, which filters the response
                // a pre-matching request filter answered with, and a @ResponseFilter method, which
                // filters the response of the route: the chain runs one or the other, as it drops the
                // pre-matching filters when it matches the route
                for (GenericHttpFilter filter : filters.responseChain()) {
                    routes.add(filterRoute(filter, resolved, false));
                }
            }
        }

        private DefaultFilterRoute filterRoute(GenericHttpFilter filter, List<String> patterns, boolean isPreMatching) {
            GenericHttpFilter ordered = GenericHttpFilter.withOrder(filter, order);
            DefaultFilterRoute route = new DefaultFilterRoute(() -> ordered, AnnotationMetadata.EMPTY_METADATA, isPreMatching);
            route.patternStyle(patternStyle);
            for (String pattern : patterns) {
                route.pattern(pattern);
            }
            HttpMethod[] httpMethods = methods;
            if (httpMethods != null) {
                route.methods(httpMethods);
            }
            return route;
        }
    }

    /**
     * The filters of a handler route, or of a group of handler routes, with the filters of the
     * groups it is declared in.
     */
    @Internal
    public final class RouteFilters {
        private final Function<String, RuntimeException> noExecutor;
        private final List<GenericHttpFilter> requestFilters = new ArrayList<>(0);
        private final List<GenericHttpFilter> responseFilters = new ArrayList<>(0);
        private @Nullable RouteFilters group;
        private boolean closed;

        /**
         * @param group      The filters of the group the filters are declared in, or {@code null}
         * @param noExecutor The error when a filter runs on an executor that does not exist
         */
        RouteFilters(@Nullable RouteFilters group, Function<String, RuntimeException> noExecutor) {
            this.group = group;
            this.noExecutor = noExecutor;
        }

        /**
         * @param filter       The filter
         * @param executorName The name of the executor to run the filter on, or {@code null}
         */
        public void before(ContextReplacingRouteRequestFilter filter, @Nullable String executorName) {
            Objects.requireNonNull(filter, "filter");
            add(requestFilters, GenericHttpFilter.createRouteRequestFilter(filter::filter, executor(executorName)));
        }

        /**
         * @param filter The filter
         */
        public void beforeAsync(AsyncContextReplacingRouteRequestFilter filter) {
            Objects.requireNonNull(filter, "filter");
            add(requestFilters, GenericHttpFilter.createAsyncRouteRequestFilter(filter::filter));
        }

        /**
         * @param filter       The filter
         * @param executorName The name of the executor to run the filter on, or {@code null}
         */
        public void after(ContextReplacingRouteResponseFilter filter, @Nullable String executorName) {
            Objects.requireNonNull(filter, "filter");
            add(responseFilters, GenericHttpFilter.createRouteResponseFilter(filter::filter, executor(executorName)));
        }

        /**
         * @param filter The filter
         */
        public void afterAsync(AsyncContextReplacingRouteResponseFilter filter) {
            Objects.requireNonNull(filter, "filter");
            add(responseFilters, GenericHttpFilter.createAsyncRouteResponseFilter(filter::filter));
        }

        /**
         * Close the filters of a group: its lambda returned, and no filter can be added any more.
         */
        public void close() {
            closed = true;
        }

        /**
         * @return Whether the filters are closed
         */
        public boolean isClosed() {
            return closed;
        }

        /**
         * @param enclosing The filters of the group of the route
         */
        void inGroup(RouteFilters enclosing) {
            this.group = Objects.requireNonNull(enclosing, "group");
        }

        /**
         * @param other The filters to copy, e.g. of the {@code GET} route of an implicit {@code HEAD} route
         */
        void copy(RouteFilters other) {
            requestFilters.addAll(other.requestFilters);
            responseFilters.addAll(other.responseFilters);
            group = other.group;
        }

        /**
         * The filters in the order the filter chain runs them. Each level, the outer group, then
         * the inner groups, then the route, adds its response filters in reverse, as response
         * filters run from the last to the first, then its request filters as declared: the
         * response filters of a level filter the response a request filter of that level, or of
         * a level inside it, answered with instead of the route.
         *
         * @return The filters, with those of the enclosing groups first
         */
        List<GenericHttpFilter> chain() {
            RouteFilters enclosing = group;
            List<GenericHttpFilter> groupFilters = enclosing == null ? List.of() : enclosing.chain();
            if (requestFilters.isEmpty() && responseFilters.isEmpty()) {
                return groupFilters;
            }
            List<GenericHttpFilter> filters = new ArrayList<>(groupFilters.size() + requestFilters.size() + responseFilters.size());
            filters.addAll(groupFilters);
            // response filters first: the chain runs them on the way back from wherever the response
            // was produced, the route or a request filter that answered instead of it, in the order
            // they were declared
            filters.addAll(responseFilters.reversed());
            filters.addAll(requestFilters);
            return List.copyOf(filters);
        }

        /**
         * @return The response filters of this level, in the order the filter chain has them, see {@link #chain()}
         */
        List<GenericHttpFilter> responseChain() {
            return responseFilters.reversed();
        }

        private void add(List<GenericHttpFilter> filters, GenericHttpFilter filter) {
            if (closed) {
                throw new IllegalStateException("The route group is closed: declare the filters of a group in its lambda");
            }
            filters.add(filter);
        }

        /**
         * The named executor, looked up when a filter first runs on it.
         *
         * @param executorName The name of the executor, or {@code null}
         * @return The executor, or {@code null}
         */
        private @Nullable Supplier<Executor> executor(@Nullable String executorName) {
            if (executorName == null) {
                return null;
            }
            RouteArguments.executorName(executorName);
            return SupplierUtil.memoized(() -> {
                ExecutorSelector selector = RouteAssembly.this.executorSelector;
                if (selector == null) {
                    throw new IllegalStateException("No executor selector to find executor: " + executorName);
                }
                return selector.select(executorName).orElseThrow(() -> noExecutor.apply(executorName));
            });
        }
    }

    /**
     * The settings of a group of handler routes other than its filters, which the routes of the
     * group, and of the groups nested in it, inherit when they are built: a route overrides them.
     */
    @Internal
    public final class RouteGroup {
        private final @Nullable RouteGroup enclosing;
        private final List<Predicate<HttpRequest<?>>> predicates = new ArrayList<>(0);
        private final Map<String, Object> attributes = new LinkedHashMap<>(0);
        private final DefaultRouteAnnotations annotations;
        private final List<DefaultErrorRoute> errorRoutes = new ArrayList<>(0);
        private final List<DefaultStatusRoute> statusRoutes = new ArrayList<>(0);
        private final Supplier<ErrorRouteInfo<Object, Object>[]> errorRouteInfos = SupplierUtil.memoized(this::buildErrorRoutes);
        private final Supplier<StatusRouteInfo<Object, Object>[]> statusRouteInfos = SupplierUtil.memoized(this::buildStatusRoutes);
        private @Nullable Integer port;
        private @Nullable Integer order;
        private boolean closed;

        /**
         * @param enclosing The settings of the enclosing group, or {@code null}
         */
        RouteGroup(@Nullable RouteGroup enclosing) {
            this.enclosing = enclosing;
            this.annotations = new DefaultRouteAnnotations(enclosing == null ? null : enclosing.annotations);
        }

        /**
         * Add an error route local to the routes of the group.
         *
         * @param error            The type of the exception
         * @param executableHandle The target of the route
         * @return The route
         */
        public DefaultErrorRoute addErrorRoute(Class<? extends Throwable> error, MethodExecutionHandle<Object, Object> executableHandle) {
            checkOpen();
            DefaultErrorRoute route = new DefaultErrorRoute(error, executableHandle, conversionService);
            errorRoutes.add(route);
            return route;
        }

        /**
         * Add a status route local to the routes of the group.
         *
         * @param status           The status
         * @param executableHandle The target of the route
         * @return The route
         */
        public DefaultStatusRoute addStatusRoute(HttpStatus status, MethodExecutionHandle<Object, Object> executableHandle) {
            checkOpen();
            DefaultStatusRoute route = new DefaultStatusRoute(status, executableHandle, conversionService);
            statusRoutes.add(route);
            return route;
        }

        /**
         * @return The enclosing group, or {@code null}
         */
        public @Nullable RouteGroup enclosing() {
            return enclosing;
        }

        /**
         * @return The error routes of this group only, built once
         */
        public ErrorRouteInfo<Object, Object>[] errorRouteInfos() {
            return errorRouteInfos.get();
        }

        /**
         * @return The status routes of this group only, built once
         */
        public StatusRouteInfo<Object, Object>[] statusRouteInfos() {
            return statusRouteInfos.get();
        }

        /**
         * @return Whether the group, or an enclosing group, has error or status routes
         */
        boolean hasErrorOrStatusRoutes() {
            RouteGroup group = this;
            while (group != null) {
                if (!group.errorRoutes.isEmpty() || !group.statusRoutes.isEmpty()) {
                    return true;
                }
                group = group.enclosing;
            }
            return false;
        }

        /**
         * Build the error and status routes of the group and of the enclosing groups, which
         * rejects duplicates, when the first route of the group is built.
         */
        void buildErrorAndStatusRoutes() {
            RouteGroup group = this;
            while (group != null) {
                group.errorRouteInfos();
                group.statusRouteInfos();
                group = group.enclosing;
            }
        }

        @SuppressWarnings("unchecked")
        private ErrorRouteInfo<Object, Object>[] buildErrorRoutes() {
            List<ErrorRouteInfo<Object, Object>> infos = new ArrayList<>(errorRoutes.size());
            for (DefaultErrorRoute route : errorRoutes) {
                ErrorRouteInfo<Object, Object> info = route.toRouteInfo();
                if (infos.stream().anyMatch(other -> other.exceptionType() == info.exceptionType() && other.getProduces().equals(info.getProduces()))) {
                    throw new RoutingException("Attempted to register multiple error routes for error [" + route.exceptionType().getSimpleName() + "] in a route group: " + route);
                }
                infos.add(info);
            }
            return infos.toArray(ErrorRouteInfo[]::new);
        }

        @SuppressWarnings("unchecked")
        private StatusRouteInfo<Object, Object>[] buildStatusRoutes() {
            List<StatusRouteInfo<Object, Object>> infos = new ArrayList<>(statusRoutes.size());
            for (DefaultStatusRoute route : statusRoutes) {
                StatusRouteInfo<Object, Object> info = route.toRouteInfo();
                if (infos.stream().anyMatch(other -> other.statusCode() == info.statusCode() && other.getProduces().equals(info.getProduces()))) {
                    throw new RoutingException("Attempted to register multiple status routes for http status [" + route.statusCode() + "] in a route group: " + route);
                }
                infos.add(info);
            }
            return infos.toArray(StatusRouteInfo[]::new);
        }

        /**
         * The port of the routes of the group: exposed now, as the server opens the exposed ports
         * when it starts.
         *
         * @param port The port
         */
        public void port(int port) {
            RouteArguments.port(port);
            checkOpen();
            this.port = port;
            RouteAssembly.this.exposedPorts.add(port);
        }

        /**
         * @param annotation An annotation of the routes of the group
         */
        public void annotate(AnnotationValue<?> annotation) {
            checkOpen();
            annotations.add(annotation);
        }

        /**
         * An attribute of the routes of the group.
         *
         * @param name  The name
         * @param value The value
         */
        public void attribute(String name, Object value) {
            checkOpen();
            attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
        }

        /**
         * The order of the routes of the group among equally good routes.
         *
         * @param order The order
         */
        public void order(int order) {
            checkOpen();
            this.order = order;
        }

        /**
         * A condition the requests of the routes of the group must meet.
         *
         * @param condition The condition
         */
        public void where(Predicate<HttpRequest<?>> condition) {
            Objects.requireNonNull(condition, "condition");
            checkOpen();
            predicates.add(condition);
        }

        /**
         * Close the group: its lambda returned.
         */
        public void close() {
            closed = true;
        }

        /**
         * @return The order of the group, or of the closest enclosing group that has one, or {@code null}
         */
        @Nullable Integer order() {
            Integer own = order;
            if (own != null) {
                return own;
            }
            RouteGroup group = enclosing;
            return group == null ? null : group.order();
        }

        /**
         * Add the attributes of the enclosing groups, then of this group, which override them.
         *
         * @param routeAttributes The attributes to add to
         */
        void addAttributes(Map<String, Object> routeAttributes) {
            RouteGroup group = enclosing;
            if (group != null) {
                group.addAttributes(routeAttributes);
            }
            routeAttributes.putAll(attributes);
        }

        /**
         * Add the conditions of the enclosing groups, then of this group.
         *
         * @param conditions The conditions to add to
         */
        void addPredicates(List<Predicate<HttpRequest<?>>> conditions) {
            RouteGroup group = enclosing;
            if (group != null) {
                group.addPredicates(conditions);
            }
            conditions.addAll(predicates);
        }

        /**
         * @return The port of the group, or of the closest enclosing group that has one, or {@code null}
         */
        @Nullable Integer port() {
            Integer own = port;
            if (own != null) {
                return own;
            }
            RouteGroup group = enclosing;
            return group == null ? null : group.port();
        }

        private void checkOpen() {
            if (closed) {
                throw new IllegalStateException("The route group is closed: declare the settings of a group in its lambda");
            }
        }
    }

    /**
     * The default route impl.
     */
    @Internal
    public final class DefaultUriRoute extends AbstractRoute implements UriRoute, HandlerUriRoute {
        final String httpMethodName;
        final HttpMethod httpMethod;
        /**
         * The template, parsed by its engine.
         */
        final ParsedRouteTemplate template;
        /**
         * The template of a route of the Micronaut engine, otherwise {@code null}.
         */
        final @Nullable UriMatchTemplate uriMatchTemplate;
        final List<DefaultUriRoute> nestedRoutes = new ArrayList<>(2);
        private @Nullable Integer port;
        private @Nullable String executeOn;
        private boolean nonBlocking;
        private final RouteFilters filters = new RouteFilters(null, executorName -> new SchedulerConfigurationException(
            targetMethod.getExecutableMethod(), "No executor configured for name: " + executorName));
        private boolean implicitHead;
        private @Nullable RouteGroup group;
        private @Nullable Integer order;
        private Map<String, Object> attributes = new LinkedHashMap<>(0);

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, uriTemplate, MediaType.APPLICATION_JSON_TYPE, targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaType The Media type
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        MediaType mediaType,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), Collections.singletonList(mediaType), targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link CharSequence}
         * @param mediaTypes The Media types
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        CharSequence uriTemplate,
                        List<MediaType> mediaTypes,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, new UriMatchTemplate(uriTemplate), mediaTypes, targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        UriMatchTemplate uriTemplate,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, uriTemplate, Collections.singletonList(MediaType.APPLICATION_JSON_TYPE), targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod The HTTP method
         * @param uriTemplate The URI Template as a {@link UriMatchTemplate}
         * @param mediaTypes The media types
         * @param targetMethod The target method execution handle
         * @param httpMethodName The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        UriMatchTemplate uriTemplate,
                        List<MediaType> mediaTypes,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            this(httpMethod, MicronautRouteTemplateEngine.of(uriTemplate), mediaTypes, targetMethod, httpMethodName, conversionService);
        }

        /**
         * @param httpMethod        The HTTP method
         * @param template          The template, parsed by its engine
         * @param mediaTypes        The media types
         * @param targetMethod      The target method execution handle
         * @param httpMethodName    The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
         * @param conversionService The conversion service
         */
        DefaultUriRoute(HttpMethod httpMethod,
                        ParsedRouteTemplate template,
                        List<MediaType> mediaTypes,
                        MethodExecutionHandle<Object, Object> targetMethod,
                        String httpMethodName,
                        ConversionService conversionService) {
            super(targetMethod, conversionService, mediaTypes);
            this.httpMethod = httpMethod;
            this.template = template;
            this.uriMatchTemplate = MicronautRouteTemplateEngine.uriMatchTemplate(template);
            this.httpMethodName = httpMethodName;
        }

        @Override
        public UriRouteInfo<Object, Object> toRouteInfo() {
            if (group != null && targetMethod instanceof HandlerMethod<?> handlerMethod) {
                handlerMethod.groupAnnotations(group.annotations); // before the executor reads them
            }
            checkBlockingBody();
            RoutePattern pattern;
            if (uriMatchTemplate != null) {
                pattern = MicronautRouteTemplateEngine.INSTANCE.matcher(template);
            } else if (template instanceof RouteLocator.PrefixTemplate prefix) {
                // composed by the router, not by the engine
                pattern = prefix.pattern();
            } else {
                pattern = RouteTemplateEngines.defaults().matcher(template);
            }
            Integer effectivePort = effectivePort();
            DefaultUrlRouteInfo<Object, Object> routeInfo = new DefaultUrlRouteInfo<>(
                httpMethod,
                httpMethodName,
                pattern,
                defaultCharset,
                targetMethod,
                bodyArgumentName,
                bodyArgument,
                consumesMediaTypes,
                producesMediaTypes,
                // a copy: the route info must not change with the route it was built from
                predicates(effectivePort),
                effectivePort,
                conversionService,
                // the executor choice as it is now: a later change to the route does not change the route info
                new RouteExecutorSelector(executeOn, nonBlocking),
                messageBodyHandlerRegistry,
                implicitHead
            );
            routeInfo.routeFilters = routeFilters();
            routeInfo.order = effectiveOrder(order, group);
            routeInfo.attributes = attributes();
            RouteGroup routeGroup = group;
            if (routeGroup != null && routeGroup.hasErrorOrStatusRoutes()) {
                // built now: a duplicate fails when the router is built
                routeGroup.buildErrorAndStatusRoutes();
                routeInfo.errorScope = routeGroup;
            }
            return routeInfo;
        }

        /**
         * @return The attributes of the groups of the route, outer group first, then of the route,
         * each overriding the ones before
         */
        private Map<String, Object> attributes() {
            RouteGroup routeGroup = group;
            if (routeGroup == null && attributes.isEmpty()) {
                return Map.of();
            }
            Map<String, Object> all = new LinkedHashMap<>();
            if (routeGroup != null) {
                routeGroup.addAttributes(all);
            }
            all.putAll(attributes);
            return all.isEmpty() ? Map.of() : Collections.unmodifiableMap(all);
        }

        /**
         * @return The port of the route, or of its group, or {@code null}
         */
        private @Nullable Integer effectivePort() {
            Integer own = port;
            if (own != null) {
                return own;
            }
            RouteGroup routeGroup = group;
            return routeGroup == null ? null : routeGroup.port();
        }

        /**
         * The conditions of the route info: the conditions of the groups of the route, outer group
         * first, the {@link RouteCondition} of the target method, the conditions of the route, and
         * a request on the port of the route if it has one. They are read when the route info is
         * built: the annotations of a handler route may be given after the route is added.
         *
         * @param effectivePort The port of the route, or {@code null}
         * @return The conditions
         */
        private List<Predicate<HttpRequest<?>>> predicates(@Nullable Integer effectivePort) {
            List<Predicate<HttpRequest<?>>> predicates = new ArrayList<>(conditions.size() + 2);
            RouteGroup routeGroup = group;
            if (routeGroup != null) {
                routeGroup.addPredicates(predicates);
            }
            if (targetMethod.isPresent(RouteCondition.class, AnnotationMetadata.VALUE_MEMBER)) {
                AnnotationValue<RouteCondition> annotation = targetMethod.getAnnotation(RouteCondition.class);
                if (annotation instanceof EvaluatedAnnotationValue<RouteCondition>) {
                    predicates.add(request -> annotation.booleanValue().orElse(false));
                }
            }
            predicates.addAll(conditions);
            if (effectivePort != null) {
                int routePort = effectivePort;
                predicates.add(httpRequest -> httpRequest.getServerAddress().getPort() == routePort);
            }
            return List.copyOf(predicates);
        }

        /**
         * A handler that reads the body as an {@link InputStream} blocks until the body arrives:
         * on the event loop, which delivers the body, it would wait forever. Such a route must
         * run on an executor.
         */
        private void checkBlockingBody() {
            if (!(targetMethod instanceof HandlerMethod<?>) || executeOn != null) {
                return;
            }
            for (Argument<?> argument : targetMethod.getArguments()) {
                if (argument.getAnnotationMetadata().hasAnnotation(Body.class)
                    && InputStream.class.isAssignableFrom(argument.getType())
                    && new RouteExecutorSelector(null, nonBlocking).select(targetMethod.getExecutableMethod(), threadSelection).isEmpty()) {
                    throw new RoutingException("The route " + this + " reads the body as an InputStream, which blocks, on the event loop"
                        + ": run it on an executor, e.g. with executeOn(TaskExecutors.BLOCKING)");
                }
            }
        }

        /**
         * A {@code HEAD} copy of this finished route, marked as implicit.
         *
         * @return The copy
         */
        @Internal
        public DefaultUriRoute implicitHeadCopy() {
            DefaultUriRoute head = new DefaultUriRoute(HttpMethod.HEAD, template, consumesMediaTypes, targetMethod, HttpMethod.HEAD.name(), conversionService);
            head.conditions.clear();
            head.conditions.addAll(conditions);
            head.producesMediaTypes = producesMediaTypes;
            head.bodyArgumentName = bodyArgumentName;
            head.bodyArgument = bodyArgument;
            head.port = port;
            head.executeOn = executeOn;
            head.nonBlocking = nonBlocking;
            head.filters.copy(filters);
            head.group = group;
            head.order = order;
            head.attributes = new LinkedHashMap<>(attributes);
            head.implicitHead = true;
            return head;
        }

        /**
         * Marks this route as an implicit {@code HEAD} route derived from a {@code @Get} mapping.
         *
         * @see UriRouteInfo#isImplicitHead()
         */
        void markImplicitHead() {
            this.implicitHead = true;
        }

        @Override
        public String getHttpMethodName() {
            return httpMethodName;
        }

        @Override
        public String toString() {
            return getHttpMethodName() + ' '
                    + (uriMatchTemplate != null ? uriMatchTemplate : template.template())
                    + " -> " + target(targetMethod)
                    + " (" + String.join(",", consumesMediaTypes) + ')';
        }

        @Override
        public HttpMethod getHttpMethod() {
            return httpMethod;
        }

        @Override
        public UriRoute body(String argument) {
            return (UriRoute) super.body(argument);
        }

        @Override
        public HandlerUriRoute annotationMetadata(AnnotationMetadataProvider annotationMetadata) {
            handlerMethod("annotations").annotationMetadata(annotationMetadata);
            return this;
        }

        @Override
        public HandlerUriRoute annotate(AnnotationValue<?> annotation) {
            handlerMethod("annotations").annotate(annotation);
            return this;
        }

        @Override
        public HandlerUriRoute responseType(Argument<?> responseType) {
            handlerMethod("return type").responseType(responseType);
            return this;
        }

        private HandlerMethod<?> handlerMethod(String what) {
            if (!(targetMethod instanceof HandlerMethod<?> handlerMethod)) {
                throw new IllegalStateException("A route to a bean method has the " + what + " of the method: " + this);
            }
            return handlerMethod;
        }

        @Override
        public HandlerUriRoute executeOn(String executorName) {
            this.executeOn = RouteArguments.executorName(executorName);
            this.nonBlocking = false;
            return this;
        }

        @Override
        public HandlerUriRoute before(ContextReplacingRouteRequestFilter filter) {
            filters.before(filter, null);
            return this;
        }

        @Override
        public HandlerUriRoute before(String executorName, ContextReplacingRouteRequestFilter filter) {
            filters.before(filter, RouteArguments.executorName(executorName));
            return this;
        }

        @Override
        public HandlerUriRoute beforeAsync(AsyncContextReplacingRouteRequestFilter filter) {
            filters.beforeAsync(filter);
            return this;
        }

        @Override
        public HandlerUriRoute after(ContextReplacingRouteResponseFilter filter) {
            filters.after(filter, null);
            return this;
        }

        @Override
        public HandlerUriRoute after(String executorName, ContextReplacingRouteResponseFilter filter) {
            filters.after(filter, RouteArguments.executorName(executorName));
            return this;
        }

        @Override
        public HandlerUriRoute afterAsync(AsyncContextReplacingRouteResponseFilter filter) {
            filters.afterAsync(filter);
            return this;
        }

        @Override
        public HandlerUriRoute inGroup(RouteFilters group) {
            filters.inGroup(group);
            return this;
        }

        @Override
        public HandlerUriRoute inGroup(RouteGroup group) {
            this.group = Objects.requireNonNull(group, "group");
            return this;
        }

        /**
         * @return The filters of the route in the order the filter chain runs them: the filters of
         * the outer group, then of the inner groups, then of the route, see {@link RouteFilters#chain()}
         */
        List<GenericHttpFilter> routeFilters() {
            return filters.chain();
        }

        @Override
        public HandlerUriRoute nonBlocking() {
            this.nonBlocking = true;
            this.executeOn = null;
            return this;
        }

        @Override
        public UriRoute exposedPort(int port) {
            // the route info matches the requests on the port only, see predicates(Integer)
            this.port = port;
            RouteAssembly.this.exposedPorts.add(port);
            return this;
        }

        @Override
        public HandlerUriRoute port(int port) {
            exposedPort(RouteArguments.port(port));
            return this;
        }

        @Override
        public HandlerUriRoute order(int order) {
            this.order = order;
            return this;
        }

        @Override
        public HandlerUriRoute attribute(String name, Object value) {
            attributes.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, "value"));
            return this;
        }

        @Override
        public @Nullable Integer getPort() {
            return port;
        }

        @Override
        public DefaultUriRoute consumes(MediaType... mediaTypes) {
            super.consumes(mediaTypes);
            return this;
        }

        @Override
        public DefaultUriRoute produces(MediaType... mediaType) {
            super.produces(mediaType);
            return this;
        }

        @Override
        public DefaultUriRoute consumesAll() {
            super.consumesAll();
            return this;
        }

        @Override
        public UriRoute nest(Runnable nested) {
            DefaultUriRoute previous = RouteAssembly.this.currentParentRoute;
            RouteAssembly.this.currentParentRoute = this;
            try {
                nested.run();
            } finally {
                RouteAssembly.this.currentParentRoute = previous;
            }
            return this;
        }

        @Override
        public DefaultUriRoute where(Predicate<HttpRequest<?>> condition) {
            super.where(condition);
            return this;
        }

        @Override
        public UriMatchTemplate getUriMatchTemplate() {
            if (uriMatchTemplate == null) {
                throw new UnsupportedOperationException("The route " + this + " has a template of the route template engine '"
                    + template.engineId() + "', which is not a UriMatchTemplate: use getRouteTemplate()");
            }
            return uriMatchTemplate;
        }

        @Override
        public RouteTemplate getRouteTemplate() {
            return uriMatchTemplate != null ? RouteTemplate.micronaut(uriMatchTemplate.toString()) : template.template();
        }

        /**
         * @return The key of the template of the route for implicit {@code HEAD} routes: the
         * {@link UriMatchTemplate} of a Micronaut template, as before there were engines, otherwise
         * the template with its engine
         */
        Object headKey() {
            return uriMatchTemplate != null ? uriMatchTemplate : template.template();
        }

        @Override
        public int compareTo(UriRoute o) {
            if (uriMatchTemplate != null && (!(o instanceof DefaultUriRoute other) || other.uriMatchTemplate != null)) {
                return uriMatchTemplate.compareTo(o.getUriMatchTemplate());
            }
            if (o instanceof DefaultUriRoute other) {
                // the Micronaut selection policy: more literal text first, then fewer variables
                int rawCompare = Integer.compare(other.template.rawLength(), template.rawLength());
                return rawCompare != 0 ? rawCompare : Integer.compare(template.pathVariableCount(), other.template.pathVariableCount());
            }
            return 0;
        }

        /**
         * Selects the executor of a route info, with the choice of the route when the route info
         * was built.
         */
        private final class RouteExecutorSelector implements ExecutorSelector {
            private final @Nullable String executorName;
            private final boolean eventLoop;

            RouteExecutorSelector(@Nullable String executorName, boolean eventLoop) {
                this.executorName = executorName;
                this.eventLoop = eventLoop;
            }

            @Override
            public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> method, ThreadSelection threadSelection) {
                // like @ExecuteOn and @NonBlocking on the method
                String name = executorName;
                if (name != null) {
                    return Optional.of(select(name).orElseThrow(() -> new SchedulerConfigurationException(
                        targetMethod.getExecutableMethod(), "No executor configured for name: " + name)));
                }
                if (eventLoop && threadSelection == ThreadSelection.AUTO) {
                    return Optional.empty();
                }
                if (RouteAssembly.this.executorSelector != null) {
                    return RouteAssembly.this.executorSelector.select(targetMethod.getExecutableMethod(), threadSelection);
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public Optional<ExecutorService> select(String name) {
                if (RouteAssembly.this.executorSelector != null) {
                    return RouteAssembly.this.executorSelector.select(name);
                } else {
                    return Optional.empty();
                }
            }

            @Override
            public Executor selectExecutor(@Nullable MethodReference<?, ?> method, ThreadSelectionConfiguration configuration) {
                if (executorName != null || eventLoop) {
                    // selects with the route's choice, see select(MethodReference, ThreadSelection)
                    return ExecutorSelector.super.selectExecutor(method, configuration);
                }
                if (RouteAssembly.this.executorSelector != null) {
                    return RouteAssembly.this.executorSelector.selectExecutor(method, configuration);
                } else {
                    return ImmediateExecutor.INSTANCE;
                }
            }
        }
    }

}
