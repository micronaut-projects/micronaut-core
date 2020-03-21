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
package io.micronaut.session.http;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.http.cookie.Cookies;
import io.micronaut.session.Session;
import io.micronaut.session.SessionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Resolves {@link io.micronaut.session.Session} identifiers from cookies.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Requires(property = SessionSettings.HTTP_COOKIE_STRATEGY, notEquals = StringUtils.FALSE)
public class CookieHttpSessionStrategy implements HttpSessionIdStrategy {

    private static final Logger LOGGER = LoggerFactory.getLogger(CookieHttpSessionStrategy.class);

    private final HttpSessionConfiguration configuration;
    private final CookieHttpSessionIdGenerator cookieHttpSessionIdGenerator;

    /**
     * Constructor.
     *
     * @param configuration The HTTP session configuration
     */
    public CookieHttpSessionStrategy(HttpSessionConfiguration configuration) {
        this(configuration, new CookieHttpSessionIdGenerator(configuration));
    }

    /**
     * Constructor.
     *
     * @param configuration The HTTP session configuration
     * @param cookieHttpSessionIdGenerator Cookie HTTP Session Id generator
     */
    public CookieHttpSessionStrategy(HttpSessionConfiguration configuration, CookieHttpSessionIdGenerator cookieHttpSessionIdGenerator) {
        this.configuration = configuration;
        this.cookieHttpSessionIdGenerator = cookieHttpSessionIdGenerator;
    }

    @Override
    public List<String> resolveIds(HttpRequest<?> message) {
        Cookies cookies = message.getCookies();
        List<String> resolvedIds = new ArrayList<>();
        String cookieName = getConfiguration().getCookieName();
        for (Map.Entry<String, Cookie> entry : cookies) {
            String name = entry.getKey();
            if (cookieName.equalsIgnoreCase(name)) {
                Cookie cookie = entry.getValue();
                String id = cookieHttpSessionIdGenerator.sessionIdFromCookie(cookie);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("path {}, session id: {}", id, message.getPath());
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
        HttpSessionConfiguration configuration = getConfiguration();
        if (session.isExpired()) {
            cookie = Cookie.of(configuration.getCookieName(), "")
                .maxAge(0);
        } else {
            String cookieValue = cookieHttpSessionIdGenerator.cookieValueFromSession(session);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("path {}, cookie value {}", request.getPath(), cookieValue);
            }
            cookie = Cookie.of(configuration.getCookieName(), cookieValue);
            if (configuration.isRememberMe()) {
                cookie.maxAge(Integer.MAX_VALUE);
            } else {
                configuration.getCookieMaxAge().ifPresent(maxAge -> cookie.maxAge(maxAge.get(ChronoUnit.SECONDS)));
            }
        }

        cookie.httpOnly(true).secure(configuration.isCookieSecure().orElse(request.isSecure()));

        configuration.getCookiePath().ifPresent(cookie::path);
        configuration.getDomainName().ifPresent(cookie::domain);
        configuration.getCookieSameSite().ifPresent(cookie::sameSite);

        response.cookie(cookie);
    }

    /**
     *
     * @return The HTTP session configuration
     */
    public HttpSessionConfiguration getConfiguration() {
        return configuration;
    }
}
