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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.SupplierUtil;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.bind.RequestBinderRegistry;
import io.micronaut.http.bind.binders.RequestArgumentBinder;
import io.micronaut.http.body.MessageBodyReader;
import io.micronaut.http.body.MessageBodyWriter;
import io.micronaut.http.uri.MicronautRouteTemplateEngine;
import io.micronaut.http.uri.ParsedRouteTemplate;
import io.micronaut.http.uri.RouteTemplate;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ThreadSelection;
import io.micronaut.scheduling.executor.ThreadSelectionConfiguration;
import io.micronaut.web.router.spi.IndexedRouteDeclaration;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * A URI route that is built the first time it is needed: a precompiled controller route, or a
 * handler function bound to a {@link IndexedRouteDeclaration}. Until then, the router indexes and orders
 * it with keys computed at compile time.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class LazyUriRouteInfo implements UriRouteInfo<Object, Object>, IndexedRoute {
    private final HttpMethod httpMethod;
    private final String methodKey;
    private final RouteTemplate template;
    private final Supplier<ParsedRouteTemplate> parsedTemplate;
    private final String requiredPathPrefix;
    private final int rawLength;
    private final int pathVariableCount;
    private final int patternVariableCount;
    private final boolean implicitHead;
    private final String target;
    private final @Nullable IndexedRouteDeclaration declaration;
    private final Supplier<UriRouteInfo<Object, Object>> delegate;

    /**
     * @param route   A precompiled controller route
     * @param builder Builds the route
     */
    LazyUriRouteInfo(PrecompiledRoute route, Supplier<UriRouteInfo<Object, Object>> builder) {
        this(HttpMethod.parse(route.httpMethod()), route.httpMethodName(), RouteTemplate.micronaut(route.uri()), null, route.requiredPathPrefix(),
            route.rawLength(), route.pathVariableCount(), route.patternVariableCount(), route.implicitHead(), route.controllerType() + '#' + route.methodName(), null, builder);
    }

    /**
     * @param declaration    The declaration a handler function is bound to
     * @param httpMethod     The HTTP method of the route, which is {@code HEAD} for the implicit {@code HEAD} route of a {@code GET} declaration
     * @param implicitHead   Whether the route is an implicit {@code HEAD} route
     * @param parsedTemplate The template of the declaration as its engine parses it
     * @param builder        Builds the route
     */
    LazyUriRouteInfo(IndexedRouteDeclaration declaration,
                     HttpMethod httpMethod,
                     boolean implicitHead,
                     Supplier<ParsedRouteTemplate> parsedTemplate,
                     Supplier<UriRouteInfo<Object, Object>> builder) {
        // the custom name for a custom method, so that the router indexes the route under it
        this(httpMethod, implicitHead ? httpMethod.name() : declaration.httpMethodName(), declaration.template(), parsedTemplate, declaration.requiredPathPrefix(),
            declaration.rawLength(), declaration.pathVariableCount(), declaration.patternVariableCount(), implicitHead, String.valueOf(declaration), declaration, builder);
    }

    private LazyUriRouteInfo(HttpMethod httpMethod,
                             String methodKey,
                             RouteTemplate template,
                             @Nullable Supplier<ParsedRouteTemplate> parsedTemplate,
                             String requiredPathPrefix,
                             int rawLength,
                             int pathVariableCount,
                             int patternVariableCount,
                             boolean implicitHead,
                             String target,
                             @Nullable IndexedRouteDeclaration declaration,
                             Supplier<UriRouteInfo<Object, Object>> builder) {
        this.httpMethod = httpMethod;
        this.methodKey = methodKey;
        this.template = template;
        this.parsedTemplate = parsedTemplate != null
            ? SupplierUtil.memoized(parsedTemplate)
            // a Micronaut template parses its segments and facts without building the matcher
            : SupplierUtil.memoized(() -> MicronautRouteTemplateEngine.INSTANCE.parse(template));
        this.requiredPathPrefix = requiredPathPrefix;
        this.rawLength = rawLength;
        this.pathVariableCount = pathVariableCount;
        this.patternVariableCount = patternVariableCount;
        this.implicitHead = implicitHead;
        this.target = target;
        this.declaration = declaration;
        this.delegate = SupplierUtil.memoized(builder);
    }

    /**
     * @return The declaration a handler function is bound to, or {@code null} for a precompiled controller route
     */
    @Nullable IndexedRouteDeclaration declaration() {
        return declaration;
    }

    /**
     * @return The route, built on first use
     */
    UriRouteInfo<Object, Object> delegate() {
        return delegate.get();
    }

    @Override
    public String getRequiredPathPrefix() {
        return requiredPathPrefix;
    }

    @Override
    public int getRawLength() {
        return rawLength;
    }

    @Override
    public int getPathVariableCount() {
        return pathVariableCount;
    }

    @Override
    public int getPatternVariableCount() {
        return patternVariableCount;
    }

    @Override
    public HttpMethod getHttpMethod() {
        return httpMethod;
    }

    @Override
    public String getHttpMethodName() {
        // the custom name for a custom method, like the route once it is built
        return methodKey();
    }

    /**
     * @return The declared template as its engine parsed it, without building the route
     */
    ParsedRouteTemplate parsedTemplate() {
        return parsedTemplate.get();
    }

    /**
     * @return Whether the declared template is of the Micronaut engine, without building the route
     */
    boolean isMicronautTemplate() {
        return template.isMicronaut();
    }

    /**
     * @return The name of the HTTP method the router registers the route under, which is the custom name for a custom method
     */
    String methodKey() {
        return methodKey;
    }

    @Override
    public int compareTo(UriRouteInfo<Object, Object> o) {
        if (o == this) {
            return 0;
        }
        if (o instanceof IndexedRoute other) {
            return IndexedRoute.compare(this, other);
        }
        return delegate().compareTo(o);
    }

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return delegate().getUriMatchTemplate();
    }

    @Override
    public RouteTemplate getRouteTemplate() {
        // the string of the built UriMatchTemplate for a Micronaut template, as before; a template
        // of another engine is known without building the route
        return template.isMicronaut() ? delegate().getRouteTemplate() : template;
    }

    @Override
    public Optional<UriRouteMatch<Object, Object>> match(URI uri) {
        return delegate().match(uri);
    }

    @Override
    public @Nullable UriRouteMatch<Object, Object> tryMatch(URI uri) {
        return delegate().tryMatch(uri);
    }

    @Override
    public Optional<UriRouteMatch<Object, Object>> match(String uri) {
        return delegate().match(uri);
    }

    @Override
    public @Nullable UriRouteMatch<Object, Object> tryMatch(String uri) {
        return delegate().tryMatch(uri);
    }

    @Override
    public @Nullable Integer getPort() {
        return delegate().getPort();
    }

    @Override
    public boolean isImplicitHead() {
        return implicitHead;
    }

    @Override
    public boolean matching(HttpRequest<?> httpRequest) {
        return delegate().matching(httpRequest);
    }

    @Override
    public MethodExecutionHandle<Object, Object> getTargetMethod() {
        return delegate().getTargetMethod();
    }

    @Override
    public String[] getArgumentNames() {
        return delegate().getArgumentNames();
    }

    @Override
    public RequestArgumentBinder<Object>[] resolveArgumentBinders(RequestBinderRegistry requestBinderRegistry) {
        return delegate().resolveArgumentBinders(requestBinderRegistry);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return delegate().getAnnotationMetadata();
    }

    @Override
    public @Nullable MessageBodyWriter<Object> getMessageBodyWriter() {
        return delegate().getMessageBodyWriter();
    }

    @Override
    public @Nullable MessageBodyReader<?> getMessageBodyReader() {
        return delegate().getMessageBodyReader();
    }

    @Override
    public ReturnType<?> getReturnType() {
        return delegate().getReturnType();
    }

    @Override
    public Argument<?> getResponseBodyType() {
        return delegate().getResponseBodyType();
    }

    @Override
    public boolean isResponseBodyJsonFormattable() {
        return delegate().isResponseBodyJsonFormattable();
    }

    @Override
    public Argument<?> getBodyType() {
        return delegate().getBodyType();
    }

    @Override
    public Optional<Argument<?>> getRequestBodyType() {
        return delegate().getRequestBodyType();
    }

    @Override
    public Optional<Argument<?>> getBodyArgument() {
        return delegate().getBodyArgument();
    }

    @Override
    public Optional<Argument<?>> getFullRequestBodyType() {
        return delegate().getFullRequestBodyType();
    }

    @Override
    public Class<?> getDeclaringType() {
        return delegate().getDeclaringType();
    }

    @Override
    public List<MediaType> getProduces() {
        return delegate().getProduces();
    }

    @Override
    public List<MediaType> getConsumes() {
        return delegate().getConsumes();
    }

    @Override
    public boolean consumesAll() {
        return delegate().consumesAll();
    }

    @Override
    public boolean doesConsume(@Nullable MediaType contentType) {
        return delegate().doesConsume(contentType);
    }

    @Override
    public boolean producesAll() {
        return delegate().producesAll();
    }

    @Override
    public boolean doesProduce(@Nullable Collection<MediaType> acceptableTypes) {
        return delegate().doesProduce(acceptableTypes);
    }

    @Override
    public boolean doesProduce(@Nullable MediaType acceptableType) {
        return delegate().doesProduce(acceptableType);
    }

    @Override
    public boolean explicitlyConsumes(@Nullable MediaType contentType) {
        return delegate().explicitlyConsumes(contentType);
    }

    @Override
    public boolean explicitlyProduces(@Nullable MediaType contentType) {
        return delegate().explicitlyProduces(contentType);
    }

    @Override
    public boolean isSuspended() {
        return delegate().isSuspended();
    }

    @Override
    public boolean isImperative() {
        return delegate().isImperative();
    }

    @Override
    public boolean isReactive() {
        return delegate().isReactive();
    }

    @Override
    public boolean isSingleResult() {
        return delegate().isSingleResult();
    }

    @Override
    public boolean isSpecifiedSingle() {
        return delegate().isSpecifiedSingle();
    }

    @Override
    public boolean isCompletable() {
        return delegate().isCompletable();
    }

    @Override
    public boolean isAsync() {
        return delegate().isAsync();
    }

    @Override
    public boolean isAsyncOrReactive() {
        return delegate().isAsyncOrReactive();
    }

    @Override
    public boolean isVoid() {
        return delegate().isVoid();
    }

    @Override
    public boolean isErrorRoute() {
        return delegate().isErrorRoute();
    }

    @Override
    public HttpStatus findStatus(@Nullable HttpStatus defaultStatus) {
        return delegate().findStatus(defaultStatus);
    }

    @Override
    public @Nullable String findContentDispositionHeader() {
        return delegate().findContentDispositionHeader();
    }

    @Override
    public boolean isWebSocketRoute() {
        return delegate().isWebSocketRoute();
    }

    @Override
    public boolean isPermitsRequestBody() {
        return delegate().isPermitsRequestBody();
    }

    @Override
    public @Nullable ExecutorService getExecutor(@Nullable ThreadSelection threadSelection) {
        return delegate().getExecutor(threadSelection);
    }

    @Override
    public Executor getExecutor(ThreadSelectionConfiguration threadSelection) {
        return delegate().getExecutor(threadSelection);
    }

    @Override
    public boolean needsRequestBody() {
        return delegate().needsRequestBody();
    }

    @Override
    public String toString() {
        return methodKey + ' ' + template + " -> " + target;
    }
}
