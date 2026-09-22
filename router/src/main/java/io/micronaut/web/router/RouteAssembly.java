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

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.execution.ImmediateExecutor;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ExceptionUtils;
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
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplate;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import io.micronaut.inject.annotation.EvaluatedAnnotationValue;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.builder.AsyncRouteRequestFilter;
import io.micronaut.web.router.builder.AsyncRouteResponseFilter;
import io.micronaut.web.router.builder.DeclaredUriRoute;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.web.router.builder.HandlerUriRoute;
import io.micronaut.web.router.builder.RouteDeclaration;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import io.micronaut.web.router.builder.RouteRequestFilter;
import io.micronaut.web.router.builder.RouteResponseFilter;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
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
        this(beanLocator, conversionService, routeUri, routeCreated, false, null);
    }

    /**
     * An assembly of routes under a context path. The context path is a literal mount: the
     * templates of every engine are mounted under it by their engine.
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
        this(beanLocator, conversionService, uri -> underContextPath(contextPath, uri), routeCreated, true, mountPrefix(contextPath));
    }

    private RouteAssembly(@Nullable Object beanLocator,
                          ConversionService conversionService,
                          UnaryOperator<String> routeUri,
                          Consumer<DefaultUriRoute> routeCreated,
                          boolean mountKnown,
                          @Nullable String mountPrefix) {
        this.conversionService = conversionService;
        this.routeUri = routeUri;
        this.routeCreated = routeCreated;
        this.mountKnown = mountKnown;
        this.mountPrefix = mountPrefix;
        if (beanLocator instanceof ApplicationContext applicationContext) {
            Environment environment = applicationContext.getEnvironment();
            defaultCharset = environment.get("micronaut.application.default-charset", Charset.class, StandardCharsets.UTF_8);
            this.executorSelector = applicationContext.findBean(ExecutorSelector.class).orElse(null);
            this.messageBodyHandlerRegistry = applicationContext.findBean(MessageBodyHandlerRegistry.class).orElse(MessageBodyHandlerRegistry.EMPTY);
        } else {
            defaultCharset = StandardCharsets.UTF_8;
            this.executorSelector = null;
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
     * @return The ports the routes are exposed on
     */
    public Set<Integer> exposedPorts() {
        return exposedPorts;
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
                () -> new DefaultUriRoute(httpMethod, uri, List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle, httpMethodName, conversionService)
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
                () -> new DefaultUriRoute(httpMethod, parsed.get(), List.of(MediaType.APPLICATION_JSON_TYPE), executableHandle, httpMethodName, conversionService)
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
            infos.add(new LazyUriRouteInfo(route.declaration(), route.declaration().httpMethod(), false, route.parsedTemplate(), route::toRouteInfo));
        }
        for (DeclaredUriRoute route : implicitHeadDeclaredRoutes) {
            infos.add(new LazyUriRouteInfo(route.declaration(), HttpMethod.HEAD, true, route.parsedTemplate(), route::implicitHeadRouteInfo));
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
                    + " -> " + targetMethod.getDeclaringType().getSimpleName()
                    + '#' + targetMethod;
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
        private final List<GenericHttpFilter> requestFilters = new ArrayList<>(0);
        private final List<GenericHttpFilter> responseFilters = new ArrayList<>(0);
        private boolean implicitHead;

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
            if (targetMethod.isPresent(RouteCondition.class, AnnotationMetadata.VALUE_MEMBER)) {
                AnnotationValue<RouteCondition> annotation = targetMethod.getAnnotation(RouteCondition.class);
                if (annotation instanceof EvaluatedAnnotationValue<RouteCondition>) {
                    where(request -> annotation.booleanValue().orElse(false));
                }
            }
        }

        @Override
        public UriRouteInfo<Object, Object> toRouteInfo() {
            RoutePattern pattern;
            if (uriMatchTemplate != null) {
                pattern = MicronautRouteTemplateEngine.INSTANCE.matcher(template);
            } else if (template instanceof RouteLocator.PrefixTemplate prefix) {
                // composed by the router, not by the engine
                pattern = prefix.pattern();
            } else {
                pattern = RouteTemplateEngines.defaults().matcher(template);
            }
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
                List.copyOf(conditions),
                port,
                conversionService,
                // the executor choice as it is now: a later change to the route does not change the route info
                new RouteExecutorSelector(executeOn, nonBlocking),
                messageBodyHandlerRegistry,
                implicitHead
            );
            routeInfo.routeFilters = routeFilters();
            return routeInfo;
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
            head.requestFilters.addAll(requestFilters);
            head.responseFilters.addAll(responseFilters);
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
                    + " -> " + targetMethod.getDeclaringType().getSimpleName()
                    + '#' + targetMethod.getName()
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
        public HandlerUriRoute annotationMetadata(AnnotationMetadata annotationMetadata) {
            if (!(targetMethod instanceof HandlerMethod<?> handlerMethod)) {
                throw new IllegalStateException("A route to a bean method has the annotations of the method: " + this);
            }
            handlerMethod.annotationMetadata(annotationMetadata);
            return this;
        }

        @Override
        public HandlerUriRoute implementing(ExecutableMethod<?, ?> method) {
            if (!(targetMethod instanceof HandlerMethod<?> handlerMethod)) {
                throw new IllegalStateException("A route to a bean method already implements the method: " + this);
            }
            handlerMethod.implementing(method);
            return this;
        }

        @Override
        public HandlerUriRoute executeOn(String executorName) {
            this.executeOn = Objects.requireNonNull(executorName, "executorName");
            this.nonBlocking = false;
            return this;
        }

        @Override
        public HandlerUriRoute before(RouteRequestFilter filter) {
            return addRequestFilter(filter, null);
        }

        @Override
        public HandlerUriRoute before(String executorName, RouteRequestFilter filter) {
            return addRequestFilter(filter, executor(executorName));
        }

        @Override
        public HandlerUriRoute beforeAsync(AsyncRouteRequestFilter filter) {
            Objects.requireNonNull(filter, "filter");
            requestFilters.add(GenericHttpFilter.createAsyncRouteRequestFilter(request -> {
                try {
                    return filter.filter(request);
                } catch (Exception e) {
                    return ExceptionUtils.sneakyThrow(e);
                }
            }));
            return this;
        }

        @Override
        public HandlerUriRoute after(RouteResponseFilter filter) {
            return addResponseFilter(filter, null);
        }

        @Override
        public HandlerUriRoute after(String executorName, RouteResponseFilter filter) {
            return addResponseFilter(filter, executor(executorName));
        }

        @Override
        public HandlerUriRoute afterAsync(AsyncRouteResponseFilter filter) {
            Objects.requireNonNull(filter, "filter");
            responseFilters.add(GenericHttpFilter.createAsyncRouteResponseFilter((request, response) -> {
                try {
                    return filter.filter(request, response);
                } catch (Exception e) {
                    return ExceptionUtils.sneakyThrow(e);
                }
            }));
            return this;
        }

        private HandlerUriRoute addRequestFilter(RouteRequestFilter filter, @Nullable Supplier<Executor> executor) {
            Objects.requireNonNull(filter, "filter");
            requestFilters.add(GenericHttpFilter.createRouteRequestFilter(request -> {
                try {
                    return filter.filter(request);
                } catch (Exception e) {
                    return ExceptionUtils.sneakyThrow(e);
                }
            }, executor));
            return this;
        }

        private HandlerUriRoute addResponseFilter(RouteResponseFilter filter, @Nullable Supplier<Executor> executor) {
            Objects.requireNonNull(filter, "filter");
            responseFilters.add(GenericHttpFilter.createRouteResponseFilter((request, response) -> {
                try {
                    filter.filter(request, response);
                } catch (Exception e) {
                    ExceptionUtils.sneakyThrow(e);
                }
            }, executor));
            return this;
        }

        /**
         * The named executor, looked up when a filter first runs on it.
         *
         * @param executorName The name of the executor
         * @return The executor
         */
        private Supplier<Executor> executor(String executorName) {
            Objects.requireNonNull(executorName, "executorName");
            return SupplierUtil.memoized(() -> {
                ExecutorSelector selector = RouteAssembly.this.executorSelector;
                if (selector == null) {
                    throw new IllegalStateException("No executor selector to find executor: " + executorName);
                }
                return selector.select(executorName).orElseThrow(() -> new SchedulerConfigurationException(
                    targetMethod.getExecutableMethod(), "No executor configured for name: " + executorName));
            });
        }

        /**
         * @return The filters of the route in the order the filter chain runs them: the request
         * filters as declared, then the response filters in reverse, as response filters run from
         * the last to the first
         */
        List<GenericHttpFilter> routeFilters() {
            if (requestFilters.isEmpty() && responseFilters.isEmpty()) {
                return List.of();
            }
            List<GenericHttpFilter> filters = new ArrayList<>(requestFilters.size() + responseFilters.size());
            // response filters first: the chain runs them on the way back from wherever the response
            // was produced, the route or a request filter that answered instead of it, in the order
            // they were declared
            filters.addAll(responseFilters.reversed());
            filters.addAll(requestFilters);
            return List.copyOf(filters);
        }

        @Override
        public HandlerUriRoute nonBlocking() {
            this.nonBlocking = true;
            this.executeOn = null;
            return this;
        }

        @Override
        public UriRoute exposedPort(int port) {
            this.port = port;
            where(httpRequest -> httpRequest.getServerAddress().getPort() == port);
            RouteAssembly.this.exposedPorts.add(port);
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
        public UriRoute where(Predicate<HttpRequest<?>> condition) {
            return (UriRoute) super.where(condition);
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
