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
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.List;
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
     * The filters of this route only, in the order the filter chain runs them. Set when the route
     * is built, before the route info is published.
     */
    List<GenericHttpFilter> routeFilters = List.of();
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
     * A match of this route whose path variables were captured by a {@link CompiledRouteMatcher}.
     *
     * @param path     The matched path
     * @param captured The raw values of the path variables, in the order of the template
     * @return The match
     */
    UriRouteMatch<T, R> capturedMatch(String path, String[] captured) {
        return new DefaultUriRouteMatch<>(new CapturedUriMatchInfo(path, uriMatchTemplate.getVariables(), captured), this, defaultCharset, conversionService);
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
    public int compareTo(UriRouteInfo o) {
        if (o instanceof DefaultUrlRouteInfo<?, ?> other) {
            return uriTemplateMatcher.compareTo(other.uriTemplateMatcher);
        }
        // e.g. a precompiled route that is not built yet
        return IndexedRoute.compare(this, (IndexedRoute) o);
    }

    @Override
    public String toString() {
        return getHttpMethodName() + ' '
                + uriMatchTemplate + " -> " + getTargetMethod().getDeclaringType().getSimpleName()
                + '#' + getTargetMethod().getName()
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
