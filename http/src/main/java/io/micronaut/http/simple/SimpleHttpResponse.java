/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.http.simple;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.TypeHint;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.core.convert.value.MutableConvertibleValuesMap;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MutableHttpHeaders;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.CookieUtils;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.simple.cookies.SimpleCookies;
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
@TypeHint(value = SimpleHttpResponse.class)
class SimpleHttpResponse<B> implements MutableHttpResponse<B> {
    private final MutableHttpHeaders headers = new SimpleHttpHeaders(ConversionService.SHARED);
    private final SimpleCookies cookies = new SimpleCookies(ConversionService.SHARED);
    private final MutableConvertibleValues<Object> attributes = new MutableConvertibleValuesMap<>();

    private int status = HttpStatus.OK.getCode();
    private String reason = HttpStatus.OK.getReason();

    private Object body;

    @Override
    public MutableHttpResponse<B> cookie(Cookie cookie) {
        this.cookies.put(cookie.getName(), cookie);
        updateCookies();
        return this;
    }

    private void updateCookies() {
        headers.remove(HttpHeaders.SET_COOKIE);
        for (Cookie cookie : cookies.getAll()) {
            CookieUtils.setCookieHeader(headers, cookie);
        }
    }

    @Override
    public MutableHttpResponse<B> cookies(Set<Cookie> cookies) {
        for (Cookie cookie: cookies) {
            this.cookies.put(cookie.getName(), cookie);
        }
        updateCookies();
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
        return (Optional<B>) Optional.ofNullable(body);
    }

    @Override
    public <T> MutableHttpResponse<T> body(@Nullable T body) {
        this.body = body;
        return (MutableHttpResponse<T>) this;
    }

    @Override
    public int code() {
        return status;
    }

    @Override
    public String reason() {
        return reason;
    }

    @Override
    public MutableHttpResponse<B> status(int status, CharSequence message) {
        this.status = status;
        if (message == null) {
            this.reason = HttpStatus.getDefaultReason(status);
        } else {
            this.reason = message.toString();
        }
        return this;
    }

    /**
     * The cookies for this response.
     *
     * @return The cookies.
     */
    @Override
    public Cookies getCookies() {
        return cookies;
    }
}
