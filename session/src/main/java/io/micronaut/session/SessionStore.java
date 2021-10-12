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
package io.micronaut.session;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * <p>Interface for locating and saving sessions.</p>
 *
 * @param <S> The session
 * @author Graeme Rocher
 * @since 1.0
 */
public interface SessionStore<S extends Session> {

    /**
     * Create a new unsaved session.
     *
     * @return The created session
     */
    S newSession();

    /**
     * Find a session for the given ID.
     *
     * @param id The ID of the session
     * @return A future the completes with an {@link Optional} session
     */
    CompletableFuture<Optional<S>> findSession(String id);

    /**
     * Delete a session for the given ID.
     *
     * @param id The ID of the session
     * @return A future that outputs <tt>true</tt> if the session was successfully deleted
     */
    CompletableFuture<Boolean> deleteSession(String id);

    /**
     * Save the given session.
     *
     * @param session The session to save
     * @return A future that completes with the saved session once the operation is complete
     */
    CompletableFuture<S> save(S session);
}
