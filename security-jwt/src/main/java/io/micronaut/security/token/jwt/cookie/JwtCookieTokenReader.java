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

package io.micronaut.security.token.jwt.cookie;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.token.reader.TokenReader;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * Reads the token from the configured io.micronaut.security.token.jwt.cookie.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = JwtCookieConfigurationProperties.PREFIX + ".enabled")
@Singleton
public class JwtCookieTokenReader implements TokenReader {

    /*
     *
     * The order of the TokenReader.
     */
    public static final Integer ORDER = 0;

    protected final JwtCookieConfiguration jwtCookieConfiguration;

    /**
     *
     * @param jwtCookieConfiguration Configuration properties for JWT Cookie support
     */
    public JwtCookieTokenReader(JwtCookieConfiguration jwtCookieConfiguration) {
        this.jwtCookieConfiguration = jwtCookieConfiguration;
    }

    @Override
    public Optional<String> findToken(HttpRequest<?> request) {
        Optional<Cookie> optionalCookie = request.getCookies().findCookie(jwtCookieConfiguration.getCookieName());
        return optionalCookie.map(Cookie::getValue);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
