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

import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.security.authentication.AuthenticationFailed;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.handlers.LoginHandler;
import io.micronaut.security.filters.SecurityFilter;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import io.micronaut.session.http.HttpSessionFilter;

import javax.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * {@link LoginHandler} implementation for Session-based Authentication.
 * @author Sergio del Amo
 * @since 1.0
 */
@Singleton
public class SessionLoginHandler implements LoginHandler {

    protected final SessionStore<Session> sessionStore;
    protected final SecuritySessionConfiguration securitySessionConfiguration;

    /**
     * Constructor.
     * @param securitySessionConfiguration Security Session Configuration
     * @param sessionStore The session store
     */
    public SessionLoginHandler(SecuritySessionConfiguration securitySessionConfiguration,
                               SessionStore<Session> sessionStore) {
        this.securitySessionConfiguration = securitySessionConfiguration;
        this.sessionStore = sessionStore;
    }

    @Override
    public HttpResponse loginSuccess(UserDetails userDetails, HttpRequest<?> request) {
        Session session = findSession(request);
        session.put(SecurityFilter.AUTHENTICATION, userDetails);
        try {
            URI location = new URI(securitySessionConfiguration.getLoginSuccessTargetUrl());
            return HttpResponse.seeOther(location);
        } catch (URISyntaxException e) {
            return HttpResponse.serverError();
        }
    }

    @Override
    public HttpResponse loginFailed(AuthenticationFailed authenticationFailed) {
        try {
            URI location = new URI(securitySessionConfiguration.getLoginFailureTargetUrl());
            return HttpResponse.seeOther(location);
        } catch (URISyntaxException e) {
            return HttpResponse.serverError();
        }
    }

    private Session findSession(HttpRequest<?> request) {
        MutableConvertibleValues<Object> attrs = request.getAttributes();
        Optional<Session> existing = attrs.get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
        if (existing.isPresent()) {
            return existing.get();
        } else {
            // create a new session store it in the attribute
            Session newSession = sessionStore.newSession();
            attrs.put(HttpSessionFilter.SESSION_ATTRIBUTE, newSession);
            return newSession;
        }
    }
}
