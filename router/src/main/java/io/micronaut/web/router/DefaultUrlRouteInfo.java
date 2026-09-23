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
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteCaptures;
import io.micronaut.http.uri.RoutePattern;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.http.uri.spi.RouteTemplateEngines;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.spi.CompiledRouteMatcher;
import io.micronaut.web.router.spi.RouteMatchSelector;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
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
     * The filters of this route only, in the order the filter chain runs them. Set when the route
     * is built, before the route info is published.
     */
    List<GenericHttpFilter> routeFilters = List.of();
    /**
     * The order of the route among equally good routes. Set when the route is built, before the
     * route info is published.
     */
    int order;
    /**
     * The attributes of the route. Set when the route is built, before the route info is published.
     */
    Map<String, Object> attributes = Map.of();
    private final HttpMethod httpMethod;
    private final String httpMethodName;
    /**
     * The template of a route of the Micronaut engine, otherwise {@code null}.
     */
    private final @Nullable UriMatchTemplate uriMatchTemplate;
    /**
     * The matcher of a route of the Micronaut engine, otherwise {@code null}: such routes match
     * with it directly, as before there were engines.
     */
    private final @Nullable UriTemplateMatcher uriTemplateMatcher;
    private final RoutePattern pattern;
    private final ParsedRouteTemplate parsedTemplate;
    private @Nullable RouteTemplate routeTemplate;
    private final Charset defaultCharset;
    private final @Nullable Integer port;
    private final ConversionService conversionService;
    private final ExecutorSelector executorSelector;
    private final boolean implicitHead;
    /**
     * The route selector of the engine of the template, or {@code null}, see {@link RouteMatchSelector}.
     */
    private final @Nullable RouteMatchSelector routeMatchSelector;

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
        this(httpMethod, httpMethodName, MicronautRouteTemplateEngine.INSTANCE.matcher(MicronautRouteTemplateEngine.of(uriMatchTemplate)),
            defaultCharset, targetMethod, bodyArgumentName, bodyArgument, consumesMediaTypes, producesMediaTypes, predicates, port,
            conversionService, executorSelector, messageBodyHandlerRegistry, implicitHead);
    }

    /**
     * @param httpMethod                 The HTTP method
     * @param httpMethodName             The actual name of the method - may differ from {@link HttpMethod#name()} for non-standard http methods
     * @param pattern                    The matcher the engine of the template prepared
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
     * @since 5.3.0
     */
    @SuppressWarnings("ParameterNumber")
    public DefaultUrlRouteInfo(HttpMethod httpMethod,
                               String httpMethodName,
                               RoutePattern pattern,
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
        this.pattern = pattern;
        this.parsedTemplate = pattern.template();
        this.uriTemplateMatcher = MicronautRouteTemplateEngine.uriTemplateMatcher(pattern);
        this.uriMatchTemplate = uriTemplateMatcher == null ? null : MicronautRouteTemplateEngine.uriMatchTemplate(parsedTemplate);
        this.defaultCharset = defaultCharset;
        this.port = port;
        this.conversionService = conversionService;
        this.executorSelector = executorSelector;
        this.routeMatchSelector = uriTemplateMatcher == null
            && RouteTemplateEngines.defaults().engine(parsedTemplate.engineId()) instanceof RouteMatchSelector selector ? selector : null;
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
        return uriTemplateMatcher != null ? uriTemplateMatcher.getRequiredPrefix() : parsedTemplate.requiredPrefix();
    }

    @Internal
    @Override
    public int getRawLength() {
        return uriTemplateMatcher != null ? uriTemplateMatcher.getRawLength() : parsedTemplate.rawLength();
    }

    @Internal
    @Override
    public int getPathVariableCount() {
        return uriTemplateMatcher != null ? uriTemplateMatcher.getPathVariableCount() : parsedTemplate.pathVariableCount();
    }

    /**
     * @return The template the engine of the route parsed
     */
    ParsedRouteTemplate parsedTemplate() {
        return parsedTemplate;
    }

    /**
     * @return Whether the template of the route is of the Micronaut engine
     */
    boolean isMicronautTemplate() {
        return uriMatchTemplate != null;
    }

    @Internal
    @Override
    public int getPatternVariableCount() {
        return uriTemplateMatcher != null ? uriTemplateMatcher.getPatternVariableCount() : parsedTemplate.patternVariableCount();
    }

    /**
     * @return The route selector of the engine of the template, or {@code null}
     */
    @Nullable RouteMatchSelector routeMatchSelector() {
        return routeMatchSelector;
    }

    /**
     * @return Whether the route selector of the engine of the template selects the matches of
     * this route, which may have a negotiated media type, see
     * {@link UriRouteMatch#getSelectedMediaType()}
     * @since 5.3.0
     */
    @Internal
    public boolean isSelectedByEngine() {
        return routeMatchSelector != null;
    }

    /**
     * The route selector of the engine negotiates the media types (a JAX-RS engine, for example,
     * with {@code image/*} against {@code image/png;qs=0.6}), so every route whose types are
     * compatible with the content type reaches it, not only the routes that consume the same
     * type. The routes of other engines keep the Micronaut checks.
     */
    @Override
    public boolean doesConsume(@Nullable MediaType contentType) {
        if (routeMatchSelector == null) {
            return super.doesConsume(contentType);
        }
        return contentType == null || consumesMediaTypesContainsAll || anyCompatible(consumesMediaTypes, contentType);
    }

    /**
     * The route selector of the engine negotiates the media types, so every route whose types
     * are compatible with an accepted type reaches it, see {@link #doesConsume(MediaType)}.
     */
    @Override
    public boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes) {
        if (routeMatchSelector == null) {
            return super.doesProduce(acceptableTypes);
        }
        if (producesMediaTypesContainsAll || acceptableTypes == null || acceptableTypes.isEmpty()) {
            return true;
        }
        for (MediaType acceptableType : acceptableTypes) {
            if (anyCompatible(producesMediaTypes, acceptableType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The route selector of the engine negotiates the media types, see
     * {@link #doesConsume(MediaType)}.
     */
    @Override
    public boolean doesProduce(@Nullable MediaType acceptableType) {
        if (routeMatchSelector == null) {
            return super.doesProduce(acceptableType);
        }
        return producesMediaTypesContainsAll || acceptableType == null || anyCompatible(producesMediaTypes, acceptableType);
    }

    /**
     * @param types     The types of a route
     * @param mediaType A type of a request
     * @return Whether one of the types matches the type in one direction or the other, without
     * the parameters, {@code *}{@code /*} matching every type
     */
    private static boolean anyCompatible(List<MediaType> types, MediaType mediaType) {
        for (MediaType type : types) {
            if (type.matches(mediaType) || mediaType.matches(type)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getHttpMethodName() {
        return httpMethodName;
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        if (uriMatchTemplate == null) {
            throw new UnsupportedOperationException("The route " + this + " has a template of the route template engine '"
                + parsedTemplate.engineId() + "', which is not a UriMatchTemplate: use getRouteTemplate()");
        }
        return uriMatchTemplate;
    }

    @Override
    public RouteTemplate getRouteTemplate() {
        RouteTemplate template = routeTemplate;
        if (template == null) {
            template = uriMatchTemplate != null ? RouteTemplate.micronaut(uriMatchTemplate.toString()) : parsedTemplate.template();
            routeTemplate = template;
        }
        return template;
    }

    @Override
    public Optional<UriRouteMatch<T, R>> match(String uri) {
        return Optional.ofNullable(tryMatch(uri));
    }

    @Override
    public @Nullable UriRouteMatch<T, R> tryMatch(String uri) {
        UriMatchInfo matchInfo;
        if (uriTemplateMatcher != null) {
            matchInfo = uriTemplateMatcher.tryMatch(uri);
        } else {
            RouteCaptures captures = pattern.match(uri);
            matchInfo = captures == null ? null : captures.toUriMatchInfo();
        }
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
        UriMatchInfo matchInfo = uriMatchTemplate != null
            ? new CapturedUriMatchInfo(path, uriMatchTemplate.getVariables(), captured)
            : new RouteCaptures(path, parsedTemplate.variables(), Arrays.asList(captured)).toUriMatchInfo();
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
        if (o instanceof DefaultUrlRouteInfo<?, ?> other && uriTemplateMatcher != null && other.uriTemplateMatcher != null) {
            return uriTemplateMatcher.compareTo(other.uriTemplateMatcher);
        }
        // e.g. a precompiled route that is not built yet
        return IndexedRoute.compare(this, (IndexedRoute) o);
    }

    @Override
    public String toString() {
        return getHttpMethodName() + ' '
                + (uriMatchTemplate != null ? uriMatchTemplate : parsedTemplate.template()) + " -> " + getTargetMethod().getDeclaringType().getSimpleName()
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
