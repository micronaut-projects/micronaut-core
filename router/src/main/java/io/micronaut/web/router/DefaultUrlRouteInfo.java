/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.filter.GenericHttpFilter;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.inject.MethodReference;
import io.micronaut.scheduling.exceptions.SchedulerConfigurationException;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.builder.DefaultPathVariables;
import io.micronaut.web.router.builder.HandlerMethod;
import io.micronaut.http.PathVariables;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

/**
 * The default {@link UriRouteInfo} implementation.
 *
 * @param <T> The target
 * @param <R> The result
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class DefaultUrlRouteInfo<T, R> extends DefaultRequestMatcher<T, R> implements UriRouteInfo<T, R>, IndexedRoute {

    /**
     * The route, or a group of it, declares the media types it consumes.
     */
    static final int DECLARED_CONSUMES = 1;
    /**
     * The route, or a group of it, declares the media types it produces.
     */
    static final int DECLARED_PRODUCES = 1 << 1;
    /**
     * The route, or a group of it, declares its executor.
     */
    static final int DECLARED_EXECUTOR = 1 << 2;

    private static final Logger LOG = LoggerFactory.getLogger(DefaultUrlRouteInfo.class);
    private static final InheritingRoute[] NO_INHERITING_ROUTES = new InheritingRoute[0];

    /**
     * The filters of this route only, in the order the filter chain runs them.
     */
    final List<GenericHttpFilter> routeFilters;
    /**
     * The innermost group of the route that has error or status routes, in it or around it, or
     * {@code null}.
     */
    final RouteAssembly.@Nullable RouteGroup errorScope;
    /**
     * Whether the route is a route of {@code HttpRouteBuilder.any(...)}, see {@link AnyMethodRoutes}.
     */
    final boolean anyMethod;
    /**
     * The order of the route among equally good routes.
     */
    private final int order;
    /**
     * The attributes of the route.
     */
    private final Map<String, Object> attributes;
    private final HttpMethod httpMethod;
    private final String httpMethodName;
    private final UriMatchTemplate uriMatchTemplate;
    private final UriTemplateMatcher uriTemplateMatcher;
    private final Charset defaultCharset;
    private final @Nullable Integer port;
    private final ConversionService conversionService;
    private final ExecutorSelector executorSelector;
    private final boolean implicitHead;
    /**
     * The constraints on the path variables of the route, of its groups first, empty for a route without them.
     */
    private final List<Predicate<? super PathVariables>> constraints;

    @Nullable
    private ExecutorService executorService;
    private boolean noExecutor;

    @Nullable
    private Executor executor;
    /**
     * Whether the route has a {@link DynamicRouteTarget}, e.g. a locator route, whose target's
     * routes decide what they consume and produce.
     */
    private final boolean dynamicTarget;
    /**
     * Builds this route again with what it inherits at a location, see {@link #inheriting(LocationInheritance)},
     * or {@code null} for a route that is not built again: a route to a bean method.
     */
    private final @Nullable Rebuild<T, R> rebuild;
    /**
     * What this route inherits at a location, or {@code null} for a route that is not built at a location.
     */
    private final @Nullable LocationInheritance inherited;
    /**
     * What the routes of this locator route inherit, built once, see {@link #locatedInheritance()}.
     */
    @SuppressWarnings("java:S3077") // an immutable record, built once
    private volatile @Nullable LocationInheritance locatedInheritance;
    /**
     * The routes built by {@link #inheriting(LocationInheritance)}, by the identity of the inheritance.
     */
    @SuppressWarnings("java:S3077") // the array is never changed: a new array replaces it
    private volatile InheritingRoute[] inheritingRoutes = NO_INHERITING_ROUTES;

    @SuppressWarnings("ParameterNumber")
    public DefaultUrlRouteInfo(HttpMethod httpMethod,
                               UriMatchTemplate uriMatchTemplate,
                               Charset defaultCharset,
                               MethodExecutionHandle<T, R> targetMethod,
                               @Nullable String bodyArgumentName,
                               @Nullable Argument<?> bodyArgument,
                               List<MediaType> consumesMediaTypes,
                               List<MediaType> producesMediaTypes,
                               List<Predicate<HttpRequest<?>>> predicates,
                               @Nullable Integer port,
                               ConversionService conversionService,
                               ExecutorSelector executorSelector,
                               MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        this(httpMethod, uriMatchTemplate, defaultCharset, targetMethod, bodyArgumentName, bodyArgument,
            consumesMediaTypes, producesMediaTypes, predicates, port, conversionService, executorSelector,
            messageBodyHandlerRegistry, false);
    }

    @SuppressWarnings("ParameterNumber")
    public DefaultUrlRouteInfo(HttpMethod httpMethod,
                               UriMatchTemplate uriMatchTemplate,
                               Charset defaultCharset,
                               MethodExecutionHandle<T, R> targetMethod,
                               @Nullable String bodyArgumentName,
                               @Nullable Argument<?> bodyArgument,
                               List<MediaType> consumesMediaTypes,
                               List<MediaType> producesMediaTypes,
                               List<Predicate<HttpRequest<?>>> predicates,
                               @Nullable Integer port,
                               ConversionService conversionService,
                               ExecutorSelector executorSelector,
                               MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                               boolean implicitHead) {
        this(httpMethod, httpMethod.name(), uriMatchTemplate, defaultCharset, targetMethod, bodyArgumentName, bodyArgument,
            consumesMediaTypes, producesMediaTypes, predicates, port, conversionService, executorSelector,
            messageBodyHandlerRegistry, implicitHead);
    }

    /**
     * @param httpMethod                 The HTTP method
     * @param httpMethodName             The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
     * @param uriMatchTemplate           The URI match template
     * @param defaultCharset             The default charset
     * @param targetMethod               The target method
     * @param bodyArgumentName           The body argument name
     * @param bodyArgument               The body argument
     * @param consumesMediaTypes         The consumed media types
     * @param producesMediaTypes         The produced media types
     * @param predicates                 The predicates
     * @param port                       The port
     * @param conversionService          The conversion service
     * @param executorSelector           The executor selector
     * @param messageBodyHandlerRegistry The message body handler registry
     * @param implicitHead               Whether this is an implicit {@code HEAD} route
     * @since 5.2.3
     */
    @SuppressWarnings("ParameterNumber")
    public DefaultUrlRouteInfo(HttpMethod httpMethod,
                               String httpMethodName,
                               UriMatchTemplate uriMatchTemplate,
                               Charset defaultCharset,
                               MethodExecutionHandle<T, R> targetMethod,
                               @Nullable String bodyArgumentName,
                               @Nullable Argument<?> bodyArgument,
                               List<MediaType> consumesMediaTypes,
                               List<MediaType> producesMediaTypes,
                               List<Predicate<HttpRequest<?>>> predicates,
                               @Nullable Integer port,
                               ConversionService conversionService,
                               ExecutorSelector executorSelector,
                               MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                               boolean implicitHead) {
        this(httpMethod, httpMethodName, uriMatchTemplate, defaultCharset, targetMethod, bodyArgumentName, bodyArgument,
            consumesMediaTypes, producesMediaTypes, predicates, port, conversionService, executorSelector,
            messageBodyHandlerRegistry, implicitHead, List.of(), 0, Map.of(), null, false, List.of(), 0, null);
    }

    /**
     * A route to a handler function, with its filters, order, attributes and error scope.
     *
     * @param httpMethod                 The HTTP method
     * @param httpMethodName             The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
     * @param uriMatchTemplate           The URI match template
     * @param defaultCharset             The default charset
     * @param targetMethod               The target method
     * @param bodyArgumentName           The body argument name
     * @param bodyArgument               The body argument
     * @param consumesMediaTypes         The consumed media types
     * @param producesMediaTypes         The produced media types
     * @param predicates                 The predicates
     * @param port                       The port
     * @param conversionService          The conversion service
     * @param executorSelector           The executor selector
     * @param messageBodyHandlerRegistry The message body handler registry
     * @param implicitHead               Whether this is an implicit {@code HEAD} route
     * @param routeFilters               The filters of this route only, in the order the filter chain runs them
     * @param order                      The order of the route among equally good routes
     * @param attributes                 The attributes of the route
     * @param errorScope                 The innermost group of the route that has error or status routes, or {@code null}
     * @param anyMethod                  Whether the route is a route of {@code HttpRouteBuilder.any(...)}
     * @param constraints                The constraints on the path variables, of the groups of the route first, see
     *                                   {@code RouteSpec#constrain(Predicate)}
     * @param declaredSettings           The settings the route, or a group of it, declares, which it does not inherit
     *                                   at a location: {@link #DECLARED_CONSUMES}, {@link #DECLARED_PRODUCES}, {@link #DECLARED_EXECUTOR}
     * @param inherited                  What the route inherits at a location, see {@link #inheriting(LocationInheritance)}, or {@code null}
     */
    @SuppressWarnings("ParameterNumber")
    DefaultUrlRouteInfo(HttpMethod httpMethod,
                        String httpMethodName,
                        UriMatchTemplate uriMatchTemplate,
                        Charset defaultCharset,
                        MethodExecutionHandle<T, R> targetMethod,
                        @Nullable String bodyArgumentName,
                        @Nullable Argument<?> bodyArgument,
                        List<MediaType> consumesMediaTypes,
                        List<MediaType> producesMediaTypes,
                        List<Predicate<HttpRequest<?>>> predicates,
                        @Nullable Integer port,
                        ConversionService conversionService,
                        ExecutorSelector executorSelector,
                        MessageBodyHandlerRegistry messageBodyHandlerRegistry,
                        boolean implicitHead,
                        List<GenericHttpFilter> routeFilters,
                        int order,
                        Map<String, Object> attributes,
                        RouteAssembly.@Nullable RouteGroup errorScope,
                        boolean anyMethod,
                        List<Predicate<? super PathVariables>> constraints,
                        int declaredSettings,
                        @Nullable LocationInheritance inherited) {
        super(targetMethod, bodyArgument, bodyArgumentName, consumesMediaTypes, producesMediaTypes, httpMethod.permitsRequestBody(), false, predicates, messageBodyHandlerRegistry);
        this.implicitHead = implicitHead;
        this.httpMethod = httpMethod;
        this.httpMethodName = httpMethodName;
        this.uriMatchTemplate = uriMatchTemplate;
        this.uriTemplateMatcher = new UriTemplateMatcher(uriMatchTemplate.getTemplateString());
        this.defaultCharset = defaultCharset;
        this.port = port;
        this.conversionService = conversionService;
        this.executorSelector = executorSelector;
        this.routeFilters = routeFilters;
        this.order = order;
        this.attributes = attributes;
        this.errorScope = errorScope;
        this.anyMethod = anyMethod;
        this.constraints = List.copyOf(constraints);
        this.dynamicTarget = targetMethod instanceof HandlerMethod<?> handler && handler.getTarget() instanceof DynamicRouteTarget;
        this.inherited = inherited;
        if (targetMethod instanceof HandlerMethod<?>) {
            // only a route to a handler function is built again at a location, see inheriting(LocationInheritance)
            this.rebuild = (method, inheritance) -> new DefaultUrlRouteInfo<>(httpMethod, httpMethodName, uriMatchTemplate,
                defaultCharset, method, bodyArgumentName, bodyArgument,
                inheritedMediaTypes(declaredSettings, DECLARED_CONSUMES, inheritance.consumes(), consumesMediaTypes),
                inheritedMediaTypes(declaredSettings, DECLARED_PRODUCES, inheritance.produces(), producesMediaTypes),
                predicates, port, conversionService, inheritedExecutor(declaredSettings, inheritance, executorSelector, method),
                messageBodyHandlerRegistry, implicitHead, routeFilters, order, inheritedAttributes(inheritance.attributes(), attributes),
                errorScope, anyMethod, this.constraints, declaredSettings, inheritance);
        } else {
            this.rebuild = null;
        }
    }

    /**
     * @param declaredSettings The settings the route declares
     * @param setting          The setting of the media types
     * @param inherited        The inherited media types, or {@code null}
     * @param own              The media types of the route
     * @return The media types of the route at a location
     */
    private static List<MediaType> inheritedMediaTypes(int declaredSettings, int setting, @Nullable List<MediaType> inherited, List<MediaType> own) {
        return (declaredSettings & setting) != 0 || inherited == null ? own : inherited;
    }

    /**
     * @param declaredSettings The settings the route declares
     * @param inheritance      What the route inherits
     * @param own              The executor selector of the route
     * @param method           The handler of the route
     * @return The executor selector of the route at a location
     */
    private static ExecutorSelector inheritedExecutor(int declaredSettings, LocationInheritance inheritance, ExecutorSelector own,
                                                      MethodExecutionHandle<?, ?> method) {
        if ((declaredSettings & DECLARED_EXECUTOR) != 0 || !inheritance.hasExecutor()) {
            return own;
        }
        return new InheritedExecutorSelector(inheritance.executorName(), inheritance.nonBlocking(), own, method);
    }

    /**
     * This route with the settings it inherits at a location, which its own settings override:
     * the route a route of a {@link io.micronaut.web.router.builder.LocatedRoutes} table is at a
     * location, with the annotations, the attributes, the media types and the executor of the
     * groups of the locator routes, like a route declared in those groups has them. Everything
     * the route derives from its annotations, such as its {@code @Produces}, {@code @Consumes}
     * and {@code @Status}, the filters bound to its annotations and its executor, derives from
     * both. The media types and the executor the route, or a group of its table, declares are
     * its own.
     *
     * <p>The routes are built once per inheritance, which a location keeps the same for every
     * request, see {@link RouteLocator}: the route of a request is one of them, like an ordinary
     * route, and a cache keyed by route, such as the CORS configuration, stays bounded.</p>
     *
     * @param inheritance What the route inherits
     * @return The route with the settings of both, or this route if it inherits nothing or its
     * target is not a handler function
     */
    @SuppressWarnings("unchecked")
    DefaultUrlRouteInfo<T, R> inheriting(LocationInheritance inheritance) {
        Rebuild<T, R> builder = rebuild;
        if (inheritance.isEmpty() || builder == null || !(getTargetMethod() instanceof HandlerMethod<?> handler)) {
            return this;
        }
        DefaultUrlRouteInfo<T, R> found = findInheriting(inheritingRoutes, inheritance);
        if (found != null) {
            return found;
        }
        synchronized (this) {
            InheritingRoute[] routes = inheritingRoutes;
            found = findInheriting(routes, inheritance);
            if (found != null) {
                return found;
            }
            DefaultUrlRouteInfo<T, R> route = builder.rebuild(
                (MethodExecutionHandle<T, R>) handler.inheriting(inheritance.annotationMetadata()), inheritance);
            InheritingRoute[] grown = Arrays.copyOf(routes, routes.length + 1);
            grown[routes.length] = new InheritingRoute(inheritance, route);
            inheritingRoutes = grown;
            return route;
        }
    }

    @SuppressWarnings("unchecked")
    private @Nullable DefaultUrlRouteInfo<T, R> findInheriting(InheritingRoute[] routes, LocationInheritance inheritance) {
        for (InheritingRoute route : routes) {
            if (route.inherited() == inheritance) {
                return (DefaultUrlRouteInfo<T, R>) route.route();
            }
        }
        return null;
    }

    /**
     * @param inherited The inherited attributes
     * @param own       The attributes of the route, which override them
     * @return The attributes of both
     */
    private static Map<String, Object> inheritedAttributes(Map<String, Object> inherited, Map<String, Object> own) {
        if (inherited.isEmpty()) {
            return own;
        }
        if (own.isEmpty()) {
            return inherited;
        }
        Map<String, Object> all = new LinkedHashMap<>(inherited);
        all.putAll(own);
        return Collections.unmodifiableMap(all);
    }

    /**
     * What the routes this locator route locates inherit, see {@link LocationInheritance}: its
     * annotations and attributes, with the ones of its groups, the media types and the executor
     * of its groups, and what it inherits from the locator routes that located it. Built once.
     *
     * @return The inheritance, {@link LocationInheritance#NONE} for a route that is not a locator route
     */
    LocationInheritance locatedInheritance() {
        LocationInheritance result = locatedInheritance;
        if (result == null) {
            if (getTargetMethod() instanceof HandlerMethod<?> handler && handler.getTarget() instanceof RouteLocator locator) {
                result = LocationInheritance.of(handler.getAnnotationMetadata(), attributes, locator.groupSettings(), inherited);
            } else {
                result = inherited == null ? LocationInheritance.NONE : inherited;
            }
            locatedInheritance = result;
        }
        return result;
    }

    /**
     * @return Whether the route has a {@link DynamicRouteTarget}, e.g. a locator route: the routes
     * of its target decide which media types the request may have and accept, see {@link DynamicRouteTarget#of}
     */
    boolean hasDynamicTarget() {
        return dynamicTarget;
    }

    /**
     * @return Whether the route has constraints on its path variables, see {@link #acceptsVariables(Map)}
     */
    boolean isConstrained() {
        return !constraints.isEmpty();
    }

    /**
     * Whether the path variables of a match of this route pass its constraints, viewed as the
     * {@link PathVariables} the handler gets. A constraint that throws rejects them.
     *
     * @param variables The variable values of the match
     * @return Whether all the constraints accept them
     */
    boolean acceptsVariables(Map<String, Object> variables) {
        PathVariables pathVariables = new DefaultPathVariables(variables, conversionService);
        for (Predicate<? super PathVariables> constraint : constraints) {
            try {
                if (!constraint.test(pathVariables)) {
                    return false;
                }
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("A constraint of the route {} rejected the path variables {}: {}", this, variables, e.getMessage(), e);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    /**
     * @return A literal that every path this route matches starts with, see
     * {@link UriTemplateMatcher#getRequiredPrefix()}
     * @since 5.3.0
     */
    @Internal
    @Override
    public String getRequiredPathPrefix() {
        return uriTemplateMatcher.getRequiredPrefix();
    }

    @Internal
    @Override
    public int getRawLength() {
        return uriTemplateMatcher.getRawLength();
    }

    @Internal
    @Override
    public int getPathVariableCount() {
        return uriTemplateMatcher.getPathVariableCount();
    }

    @Internal
    @Override
    public int getPatternVariableCount() {
        return uriTemplateMatcher.getPatternVariableCount();
    }

    @Override
    public String getHttpMethodName() {
        return httpMethodName;
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return uriMatchTemplate;
    }

    @Override
    public Optional<UriRouteMatch<T, R>> match(String uri) {
        return Optional.ofNullable(tryMatch(uri));
    }

    @Override
    public @Nullable UriRouteMatch<T, R> tryMatch(String uri) {
        UriMatchInfo matchInfo = uriTemplateMatcher.tryMatch(uri);
        if (matchInfo != null) {
            return new DefaultUriRouteMatch<>(matchInfo, this, defaultCharset, conversionService);
        }
        return null;
    }

    /**
     * A match of this route resolved by a {@link DynamicRouteTarget}.
     *
     * @param matchInfo The match info, e.g. a {@link DynamicRouteTarget.ResolvedMatchInfo}
     * @return The match
     */
    UriRouteMatch<T, R> resolvedMatch(UriMatchInfo matchInfo) {
        return new DefaultUriRouteMatch<>(matchInfo, this, defaultCharset, conversionService);
    }

    @Override
    public @Nullable Integer getPort() {
        return port;
    }

    @Override
    public boolean isImplicitHead() {
        return implicitHead;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public int compareTo(UriRouteInfo o) {
        if (o instanceof DefaultUrlRouteInfo<?, ?> other) {
            return uriTemplateMatcher.compareTo(other.uriTemplateMatcher);
        }
        // e.g. a declared route that is not built yet
        return IndexedRoute.compare(this, (IndexedRoute) o);
    }

    @Override
    public String toString() {
        return getHttpMethodName() + ' '
                + uriMatchTemplate + " -> " + RouteAssembly.target(getTargetMethod())
                + " (" + String.join(",", consumesMediaTypes) + ')';
    }

    @Override
    public @Nullable ExecutorService getExecutor(@Nullable ThreadSelection threadSelection) {
        if (executorService != null || noExecutor) {
            return executorService;
        }
        ExecutorService es = executorSelector.select(getTargetMethod(), threadSelection == null ? ThreadSelection.AUTO : threadSelection).orElse(null);
        if (es == null) {
            noExecutor = true;
            return null;
        }
        executorService = es;
        return executorService;
    }

    @Override
    public Executor getExecutor(ThreadSelectionConfiguration configuration) {
        Executor executor = this.executor;
        if (executor == null) {
            executor = executorSelector.selectExecutor(getTargetMethod(), configuration);
            this.executor = executor;
        }
        return executor;
    }

    /**
     * A route built by {@link #inheriting(AnnotationMetadata)}.
     *
     * @param inherited The inherited annotations
     * @param route     The route with them
     */
    private record InheritingRoute(LocationInheritance inherited, DefaultUrlRouteInfo<?, ?> route) {
    }

    /**
     * Builds a route again with what it inherits at a location.
     *
     * @param <T> The target type
     * @param <R> The result type
     */
    @FunctionalInterface
    private interface Rebuild<T, R> {
        /**
         * @param method      The handler, with the annotations of both
         * @param inheritance What the route inherits
         * @return The route
         */
        DefaultUrlRouteInfo<T, R> rebuild(MethodExecutionHandle<T, R> method, LocationInheritance inheritance);
    }

    /**
     * The executor of a route that inherits the executor of the groups of its locator routes, see
     * {@code HttpRouteGroup#executeOn(String)} and {@code HttpRouteGroup#nonBlocking()}, like the
     * executor of the route had it declared it.
     *
     * @param executorName The name of the executor, or {@code null}
     * @param nonBlocking  Whether the route runs on the event loop when the threads are selected automatically
     * @param route        The executor selector of the route, which declares no executor
     * @param method       The handler of the route
     */
    private record InheritedExecutorSelector(@Nullable String executorName, boolean nonBlocking, ExecutorSelector route,
                                             MethodExecutionHandle<?, ?> method) implements ExecutorSelector {

        @Override
        public Optional<ExecutorService> select(@Nullable MethodReference<?, ?> reference, ThreadSelection threadSelection) {
            String name = executorName;
            if (name != null) {
                return Optional.of(route.select(name).orElseThrow(() -> new SchedulerConfigurationException(
                    method.getExecutableMethod(), "No executor configured for name: " + name)));
            }
            if (nonBlocking && threadSelection == ThreadSelection.AUTO) {
                return Optional.empty();
            }
            return route.select(reference, threadSelection);
        }

        @Override
        public Optional<ExecutorService> select(String name) {
            return route.select(name);
        }
    }
}
