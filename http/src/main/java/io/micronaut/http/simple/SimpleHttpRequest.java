/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.simple;

import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpParameters;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.simple.cookies.SimpleCookies;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Set;

/**
 * Simple {@link MutableHttpRequest} implementation.
 *
 * @param <B> the type of the body
 * @author Vladimir Orany
 * @since 1.0
 */
public class SimpleHttpRequest<B> implements MutableHttpRequest<B> {

    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();
    private final SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
    private final SimpleHttpHeaders headers = new SimpleHttpHeaders(ConversionService.SHARED);
    private final SimpleHttpParameters parameters = new SimpleHttpParameters(ConversionService.SHARED);

    private HttpMethod method;
    private URI uri;
    private Object body;

    /**
     * Simple {@link MutableHttpRequest} implementation.
     *
     * @param method the HTTP method
     * @param uri    the URI of the request
     * @param body   the optional body of the request
     */
    public SimpleHttpRequest(HttpMethod method, String uri, B body) {
        this.method = method;
        try {
            this.uri = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Wrong URI", e);
        }
        this.body = body;
    }

    @Override
    public MutableHttpRequest<B> cookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
        return this;
    }

    @Override
    public MutableHttpRequest<B> cookies(Set<Cookie> cookies) {
        for (Cookie cookie: cookies) {
            cookie(cookie);
        }
        return this;
    }

    @Override
    public MutableHttpRequest<B> uri(URI uri) {
        this.uri = uri;
        return this;
    }

    @Override
    public <T> MutableHttpRequest<T> body(T body) {
        this.body = body;
        return (MutableHttpRequest<T>) this;
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public Cookies getCookies() {
        return cookies;
    }

    @Override
    public MutableHttpParameters getParameters() {
        return parameters;
    }

    @Override
    public HttpMethod getMethod() {
        return this.method;
    }

    @Override
    public URI getUri() {
        return this.uri;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Optional<B> getBody() {
        return (Optional<B>) Optional.ofNullable(this.body);
    }
}
