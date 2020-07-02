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
package io.micronaut.session.http;

import io.micronaut.http.HttpRequest;
import io.micronaut.session.Session;
import io.micronaut.session.SessionStore;
import java.util.Optional;

/**
 * Utility class with methods to create or retrieve a session associated to a request.
 *
 * @author Sergio del Amo
 * @since 1.1.0
 */
public class SessionForRequest {

    /**
     * Creates a session and stores it in the request attributes.
     *
     * @param sessionStore the session store
     * @param request the Http Request
     * @return A new session stored in the request attributes
     */
    public static Session create(SessionStore sessionStore, HttpRequest<?> request) {
        Session session = sessionStore.newSession();
        request.getAttributes().put(HttpSessionFilter.SESSION_ATTRIBUTE, session);
        return session;
    }

    /**
     * Finds a session.
     *
     * @param request the Http Request
     * @return A session if found in the request attributes.
     */
    public static Optional<Session> find(HttpRequest<?> request) {
        return request.getAttributes().get(HttpSessionFilter.SESSION_ATTRIBUTE, Session.class);
    }

    /**
     * Finds a session or creates a new one and stores it in the request attributes.
     *
     * @param request The Http Request
     * @param sessionStore The session store to create the session if not found
     * @return A session if found in the request attributes or a new session
     * stored in the request attributes.
     */
    public static Session findOrCreate(HttpRequest<?> request, SessionStore sessionStore) {
        return find(request).orElseGet(() -> create(sessionStore, request));
    }
}
