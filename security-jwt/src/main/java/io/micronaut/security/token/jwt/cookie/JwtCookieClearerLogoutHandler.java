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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.cookie.Cookie;
import io.micronaut.security.handlers.LogoutHandler;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 *
 * @author Sergio del Amo
 * @since 1.0
 */
@Requires(property = JwtCookieConfigurationProperties.PREFIX + ".enabled")
@Singleton
public class JwtCookieClearerLogoutHandler implements LogoutHandler {

    protected final JwtCookieConfiguration jwtCookieConfiguration;

    /**
     * @param jwtCookieConfiguration JWT Cookie Configuration
     */
    public JwtCookieClearerLogoutHandler(JwtCookieConfiguration jwtCookieConfiguration) {
        this.jwtCookieConfiguration = jwtCookieConfiguration;
    }

    @Override
    public HttpResponse logout(HttpRequest<?> request) {
        Optional<Cookie> cookie = request.getCookies().findCookie(jwtCookieConfiguration.getCookieName());
        try {
            URI location = new URI(jwtCookieConfiguration.getLogoutTargetUrl());
            if (cookie.isPresent()) {
                return HttpResponse.seeOther(location).cookie(cookie.get().maxAge(0));
            }
            return HttpResponse.seeOther(location);
        } catch (URISyntaxException var5) {
            return HttpResponse.serverError();
        }
    }
}
