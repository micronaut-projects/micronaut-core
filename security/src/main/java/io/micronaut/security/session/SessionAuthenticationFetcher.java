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

package io.micronaut.security.session;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.filters.AuthenticationFetcher;
import io.micronaut.security.token.TokenAuthenticationFetcher;
import io.micronaut.session.Session;

import javax.inject.Singleton;
import java.util.Optional;

import static io.micronaut.security.filters.SecurityFilter.AUTHENTICATION;
import static io.micronaut.session.http.HttpSessionFilter.SESSION_ATTRIBUTE;

@Requires(property = SecuritySessionConfigurationProperties.PREFIX + ".enabled")
@Singleton
public class SessionAuthenticationFetcher implements AuthenticationFetcher {
    /**
     * The order of the fetcher.
     */
    public static final Integer ORDER = TokenAuthenticationFetcher.ORDER - 100;

    @Override
    public Optional<Authentication> fetchAuthentication(HttpRequest<?> request) {
        Optional<Session> opt = request.getAttributes().get(SESSION_ATTRIBUTE, Session.class);
        if (opt.isPresent()) {
            Session session = opt.get();
            Optional<Object> optObj = session.get(AUTHENTICATION);
            if (optObj.isPresent()) {
                Object obj = optObj.get();
                if (obj instanceof Authentication) {
                    return Optional.of((Authentication)obj);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
