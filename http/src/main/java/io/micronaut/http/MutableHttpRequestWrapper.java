/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.cookie.Cookie;

import java.net.URI;
import java.util.Optional;

/**
 * Wrapper around an immutable {@link HttpRequest} that allows mutation.
 *
 * @param <B> Body type
 * @since 4.0.0
 */
@Internal
public class MutableHttpRequestWrapper<B> extends HttpRequestWrapper<B> implements MutableHttpRequest<B> {
    private ConversionService conversionService;

    @Nullable
    private B body;
    @Nullable
    private URI uri;

    protected MutableHttpRequestWrapper(ConversionService conversionService, HttpRequest<B> delegate) {
        super(delegate);
        this.conversionService = conversionService;
    }

    public static MutableHttpRequest<?> wrapIfNecessary(ConversionService conversionService, HttpRequest<?> request) {
        if (request instanceof MutableHttpRequest<?> httpRequest) {
            return httpRequest;
        } else {
            return new MutableHttpRequestWrapper<>(conversionService, request);
        }
    }

    @NonNull
    @Override
    public Optional<B> getBody() {
        if (body == null) {
            return getDelegate().getBody();
        } else {
            return Optional.of(body);
        }
    }

    @NonNull
    @Override
    public <T> Optional<T> getBody(@NonNull Class<T> type) {
        if (body == null) {
            return getDelegate().getBody(type);
        } else {
            return conversionService.convert(body, ConversionContext.of(type));
        }
    }

    @Override
    public <T> Optional<T> getBody(ArgumentConversionContext<T> conversionContext) {
        if (body == null) {
            return getDelegate().getBody(conversionContext);
        } else {
            return conversionService.convert(body, conversionContext);
        }
    }

    @Override
    public MutableHttpRequest<B> cookie(Cookie cookie) {
        throw new UnsupportedOperationException();
    }

    @Override
    public MutableHttpRequest<B> uri(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    @NonNull
    public URI getUri() {
        if (uri == null) {
            return getDelegate().getUri();
        } else {
            return uri;
        }
    }

    @NonNull
    @Override
    public MutableHttpParameters getParameters() {
        return (MutableHttpParameters) super.getParameters();
    }

    @NonNull
    @Override
    public MutableHttpHeaders getHeaders() {
        return (MutableHttpHeaders) super.getHeaders();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> MutableHttpRequest<T> body(T body) {
        this.body = (B) body;
        return (MutableHttpRequest<T>) this;
    }

    @Override
    public void setConversionService(ConversionService conversionService) {
        this.conversionService = conversionService;
    }
}
