/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.http.cookie;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.util.StringUtils;

import java.net.HttpCookie;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of {@link ServerCookieEncoder} which uses {@link HttpCookie} to encode cookies.
 * @author Sergio del Amo
 * @since 4.4.0
 */
@Internal
public final class DefaultServerCookieEncoder implements ServerCookieEncoder {
    private static final String SPACE = " ";
    private static final String EQUAL = "=";
    private static final String SEMICOLON = ";";
    private static final ZoneId GMT_ZONE = ZoneId.of("GMT");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'");

    @Override
    @NonNull
    public List<String> encode(@NonNull Cookie... cookies) {
        return Arrays.stream(cookies).map(this::encodeCookie).toList();
    }

    @NonNull
    private String encodeCookie(@NonNull Cookie cookie) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookie.getName()).append(EQUAL).append(cookie.getValue());
        if (isMaxAgeSet(cookie)) {
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_MAX_AGE).append(EQUAL).append(cookie.getMaxAge());
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_EXPIRES).append(EQUAL).append(expires(cookie.getMaxAge()));
        }
        if (StringUtils.isNotEmpty(cookie.getPath())) {
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_PATH).append(EQUAL).append(cookie.getPath());
        }
        if (StringUtils.isNotEmpty(cookie.getDomain())) {
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_DOMAIN).append(EQUAL).append(cookie.getDomain());
        }
        if (cookie.isSecure()) {
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_SECURE);
        }
        if (cookie.isHttpOnly()) {
            sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_HTTP_ONLY);
        }
        cookie.getSameSite().ifPresent(sameSite ->
                sb.append(SEMICOLON).append(SPACE).append(Cookie.ATTRIBUTE_SAME_SITE).append(EQUAL).append(sameSite));
        return sb.toString();
    }

    /**
     * {@link HttpCookie} default value for max age is -1 and io.netty.handler.codec.http.cookie.DefaultCookie is Long.MIN_VALUE.
     * @param cookie Cookie
     * @return true if the cookie's max age is set.
     */
    private boolean isMaxAgeSet(Cookie cookie) {
        return cookie.getMaxAge() != -1 && cookie.getMaxAge() != Long.MIN_VALUE;
    }

    private static String expires(Long maxAgeSeconds) {
        LocalDateTime localDateTime = LocalDateTime.now(GMT_ZONE).plusSeconds(maxAgeSeconds);
        ZonedDateTime gmtDateTime = ZonedDateTime.of(localDateTime, GMT_ZONE);
        return gmtDateTime.format(FORMATTER);
    }

}
