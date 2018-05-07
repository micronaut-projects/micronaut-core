/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.session.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.session.Session;
import io.micronaut.session.SessionSettings;

import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link io.micronaut.session.Session} identifiers from cookies.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = SessionSettings.HTTP_COOKIE_STRATEGY, notEquals = "false")
public class CookieHttpSessionStrategy implements HttpSessionIdStrategy {

    private final boolean base64Decode;
    private final String prefix;
    private final HttpSessionConfiguration configuration;

    /**
     * Constructor.
     *
     * @param configuration The HTTP session configuration
     */
    public CookieHttpSessionStrategy(HttpSessionConfiguration configuration) {
        this.configuration = configuration;
        this.base64Decode = configuration.isBase64Encode();
        this.prefix = configuration.getPrefix().orElse(null);
    }

    @Override
    public List<String> resolveIds(HttpRequest<?> message) {
        Cookies cookies = message.getCookies();
        List<String> resolvedIds = new ArrayList<>();
        String cookieName = configuration.getCookieName();
        for (Map.Entry<String, Cookie> entry : cookies) {
            String name = entry.getKey();
            if (cookieName.equalsIgnoreCase(name)) {
                Cookie cookie = entry.getValue();
                String id = cookie.getValue();
                if (base64Decode) {
                    id = new String(Base64.getDecoder().decode(id));
                }
                int len = id.length();
                if (prefix != null && len < prefix.length()) {
                    id = id.substring(prefix.length());
                }
                resolvedIds.add(id);
            }
        }

        return resolvedIds;
    }

    @Override
    public void encodeId(HttpRequest<?> request,
                         MutableHttpResponse<?> response,
                         Session session) {
        Cookie cookie;
        if (session.isExpired()) {
            cookie = Cookie.of(configuration.getCookieName(), "")
                .maxAge(0);
        } else {
            String id = session.getId();
            if (prefix != null) {
                id = prefix + id;
            }
            if (base64Decode) {
                id = Base64.getEncoder().encodeToString(id.getBytes());
            }
            cookie = Cookie.of(configuration.getCookieName(), id);
            if (configuration.isRememberMe()) {
                cookie.maxAge(Integer.MAX_VALUE);
            } else {
                configuration.getCookieMaxAge().ifPresent(cookie::maxAge);
            }
        }

        cookie.httpOnly(true).secure(request.isSecure());

        configuration.getCookiePath().ifPresent(cookie::path);
        configuration.getDomainName().ifPresent(cookie::domain);

        response.cookie(cookie);
    }
}
