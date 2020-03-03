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
import io.micronaut.http.cookie.Cookie;
import io.micronaut.session.Session;
import io.micronaut.session.SessionSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.validation.constraints.NotNull;
import java.util.Base64;

/**
 * Utility to generate a session id from a cookie value or builds a cookie value from session.
 *
 * @author Sergio del Amo
 * @since 1.0.1
 */
@Singleton
@Requires(property = SessionSettings.HTTP_COOKIE_STRATEGY, notEquals = "false")
public class CookieHttpSessionIdGenerator {
    private static final Logger LOGGER = LoggerFactory.getLogger(CookieHttpSessionIdGenerator.class);

    private final boolean base64Decode;
    private final String prefix;

    /**
     * Constructor.
     *
     * @param configuration The HTTP session configuration
     */
    public CookieHttpSessionIdGenerator(HttpSessionConfiguration configuration) {
        this.base64Decode = configuration.isBase64Encode();
        this.prefix = configuration.getPrefix().orElse(null);
    }

    /**
     * @return Whether the Base64 encode sessions IDs sent back to clients
     */
    public boolean isBase64Decode() {
        return this.base64Decode;
    }

    /**
     * @return The prefix to use when serializing session ID
     */
    public String getPrefix() {
        return this.prefix;
    }

    /**
     * @param cookie A Cookie
     * @return A session id from a cookie value
     */
    public @NotNull String sessionIdFromCookie(@NotNull Cookie cookie) {
        String id = cookie.getValue();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("cookie value: {}", id);
        }
        if (isBase64Decode()) {
            id = new String(Base64.getDecoder().decode(id));
        }
        int len = id.length();
        if (getPrefix() != null && len < getPrefix().length()) {
            id = id.substring(getPrefix().length());
        }
        return id;
    }

    /**
     *
     * @param session The session
     * @return Cookie value from session.
     */
    public @NotNull String cookieValueFromSession(@NotNull Session session) {
        String id = session.getId();
        if (getPrefix() != null) {
            id = getPrefix() + id;
        }
        if (isBase64Decode()) {
            id = Base64.getEncoder().encodeToString(id.getBytes());
        }
        return id;
    }
}
