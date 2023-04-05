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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.uri.UriMatchInfo;
import io.micronaut.http.uri.UriMatchTemplate;
import io.micronaut.inject.MethodExecutionHandle;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
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
    private final Charset defaultCharset;
    private final Integer port;
    private final ConversionService conversionService;

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
                               ConversionService conversionService) {
        super(targetMethod, bodyArgument, bodyArgumentName, consumesMediaTypes, producesMediaTypes, HttpMethod.permitsRequestBody(httpMethod), false, predicates);
        this.httpMethod = httpMethod;
        this.uriMatchTemplate = uriMatchTemplate;
        this.defaultCharset = defaultCharset;
        this.port = port;
        this.conversionService = conversionService;
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
        Optional<UriMatchInfo> matchInfo = uriMatchTemplate.match(uri);
        return matchInfo.map(info -> new DefaultUriRouteMatch<>(info, this, defaultCharset, conversionService));
    }

    @Override
    public Integer getPort() {
        return port;
    }

    @Override
    public int compareTo(UriRouteInfo o) {
        return uriMatchTemplate.compareTo(o.getUriMatchTemplate());
    }

    @Override
    public String toString() {
        return new StringBuilder(getHttpMethodName()).append(' ')
                .append(uriMatchTemplate)
                .append(" -> ")
                .append(getTargetMethod().getDeclaringType().getSimpleName())
                .append('#')
                .append(getTargetMethod().getName())
                .append(" (")
                .append(String.join(",", consumesMediaTypes))
                .append(")")
                .toString();
    }
}
