/*
 * Copyright 2017-2019 original authors
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
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.simple.cookies.SimpleCookies;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.Set;

/**
 * Simple {@link MutableHttpResponse} implementation.
 *
 * @param <B> the type of the body
 *
 * @author Vladimir Orany
 * @since 1.0
 */
class SimpleHttpResponse<B> implements MutableHttpResponse<B> {

    private final MutableHttpHeaders headers = new SimpleHttpHeaders(ConversionService.SHARED);
    private final SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();

    private HttpStatus status = HttpStatus.OK;
    private B body;

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
        return this;
    }

    @Override
    public MutableHttpResponse<B> cookies(Set<Cookie> cookies) {
        cookies.forEach(cookie -> {
            this.cookies.put(cookie.getName(), cookie);
        });

        return this;
    }

    @Override
    public MutableHttpHeaders getHeaders() {
        return this.headers;
    }

    @Override
    public MutableConvertibleValues<Object> getAttributes() {
        return attributes;
    }

    @Override
    public Optional<B> getBody() {
        return Optional.ofNullable(body);
    }

    @Override
    public MutableHttpResponse<B> body(@Nullable B body) {
        this.body = body;
        return this;
    }

    @Override
    public MutableHttpResponse<B> status(HttpStatus status, CharSequence message) {
        this.status = status;
        return this;
    }

    @Override
    public HttpStatus getStatus() {
        return this.status;
    }

    /**
     * The cookies for this response.
     *
     * @return The cookies.
     */
    public Cookies getCookies() {
        return cookies;
    }
}
