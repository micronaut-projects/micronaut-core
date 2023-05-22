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
package io.micronaut.http.client.jdk.cookie;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.netty.NettyClientHttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.http.simple.cookies.SimpleCookie;
import io.micronaut.http.simple.cookies.SimpleCookies;
import jakarta.inject.Singleton;

import java.net.HttpCookie;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A cookie decoder that extracts cookies from the {@link NettyClientHttpRequest} if it is present.
 * Required as {@link NettyClientHttpRequest} does not implement {@link HttpRequest#getCookies()}.
 *
 * @since 4.0.0
 * @author Tim Yates
 */
@Singleton
@Experimental
@Internal
@Requires(classes = NettyClientHttpRequest.class)
public class NettyCookieDecoder implements CookieDecoder {

    public static final int ORDER = 1;
    private final ConversionService conversionService;

    public NettyCookieDecoder(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @NonNull
    @Override
    public Optional<Cookies> decode(HttpRequest<?> request) {
        if (request instanceof NettyClientHttpRequest nettyClientHttpRequest) {
            SimpleCookies entries = new SimpleCookies(conversionService);

            List<HttpCookie> headerCookies = nettyClientHttpRequest
                .getHeaders()
                .getAll(HttpHeaders.COOKIE)
                .stream()
                .map(HttpCookie::parse)
                .flatMap(Collection::stream)
                .toList();

            headerCookies.forEach(cookie -> {
                Cookie c = new SimpleCookie(cookie.getName(), cookie.getValue());
                c.maxAge(cookie.getMaxAge());
                c.domain(cookie.getDomain());
                c.httpOnly(cookie.isHttpOnly());
                c.path(cookie.getPath());
                c.secure(cookie.getSecure());
                entries.put(cookie.getName(), c);
            });
            return Optional.of(entries);
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
