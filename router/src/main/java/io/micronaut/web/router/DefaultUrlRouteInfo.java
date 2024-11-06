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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.body.MessageBodyHandlerRegistry;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.http.uri.UriTemplateMatcher;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.scheduling.executor.ExecutorSelector;
import io.micronaut.scheduling.executor.ThreadSelection;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
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
public final class DefaultUrlRouteInfo<T, R> extends DefaultRequestMatcher<T, R> implements UriRouteInfo<T, R> {

    private final HttpMethod httpMethod;
    private final UriMatchTemplate uriMatchTemplate;
    private final UriTemplateMatcher uriTemplateMatcher;
    private final Charset defaultCharset;
    private final Integer port;
    private final ConversionService conversionService;
    private final ExecutorSelector executorSelector;
    @Nullable
    private ExecutorService executorService;
    private boolean noExecutor;

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
                               Integer port,
                               ConversionService conversionService,
                               ExecutorSelector executorSelector,
                               MessageBodyHandlerRegistry messageBodyHandlerRegistry) {
        super(targetMethod, bodyArgument, bodyArgumentName, consumesMediaTypes, producesMediaTypes, httpMethod.permitsRequestBody(), false, predicates, messageBodyHandlerRegistry);
        this.httpMethod = httpMethod;
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

    @Override
    public UriMatchTemplate getUriMatchTemplate() {
        return uriMatchTemplate;
    }

    @Override
    public Optional<UriRouteMatch<T, R>> match(String uri) {
        return Optional.ofNullable(tryMatch(uri));
    }

    @Override
    public UriRouteMatch<T, R> tryMatch(@NonNull String uri) {
        UriMatchInfo matchInfo = uriTemplateMatcher.tryMatch(uri);
        if (matchInfo != null) {
            return new DefaultUriRouteMatch<>(matchInfo, this, defaultCharset, conversionService);
        }
        return null;
    }

    @Override
    public Integer getPort() {
        return port;
    }

    @Override
    public int compareTo(@NonNull UriRouteInfo o) {
        return uriTemplateMatcher.compareTo(((DefaultUrlRouteInfo) o).uriTemplateMatcher);
    }

    @Override
    public String toString() {
        return getHttpMethodName() + ' '
                + uriMatchTemplate + " -> " + getTargetMethod().getDeclaringType().getSimpleName()
                + '#' + getTargetMethod().getName()
                + " (" + String.join(",", consumesMediaTypes) + ')';
    }

    @Override
    public ExecutorService getExecutor(ThreadSelection threadSelection) {
        if (executorService != null || noExecutor) {
            return executorService;
        }
        ExecutorService es = executorSelector.select(getTargetMethod(), threadSelection).orElse(null);
        if (es == null) {
            noExecutor = true;
            return null;
        }
        executorService = es;
        return executorService;
    }
}
