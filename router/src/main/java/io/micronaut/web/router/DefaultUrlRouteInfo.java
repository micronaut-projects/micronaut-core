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
import io.micronaut.http.uri.UriMatchVariable;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
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

    @Nullable
    private ExecutorService executorService;
    private boolean noExecutor;

    @Nullable
    private Executor executor;

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
            messageBodyHandlerRegistry, implicitHead, List.of(), 0, Map.of(), null, false);
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
                        boolean anyMethod) {
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
}
