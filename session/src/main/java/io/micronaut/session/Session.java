/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.session;

import io.micronaut.core.convert.value.MutableConvertibleValues;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * <p>An interface representing a user session.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Session extends MutableConvertibleValues<Object> {

    /**
     * Returns the time when this session was created.
     *
     * @return An {@link Instant} instance
     * @throws IllegalStateException if this method is called on an invalidated session
     */
    @NonNull
    Instant getCreationTime();

    /**
     * A unique identifier for the session.
     *
     * @return The id of the session
     */
    @NonNull
    String getId();

    /**
     * Returns the last time the client sent a request associated with this session as an {@link Instant}.
     * <p>
     * <p>Actions that your application takes, such as getting or setting a value associated with the session, do not
     * affect the access time.
     *
     * @return An {@link Instant} representing the time the session was last accessed
     * @throws IllegalStateException if this method is called on an invalidated session
     */
    @NonNull
    Instant getLastAccessedTime();

    /**
     * Sets the last accessed time on the session.
     *
     * @param instant The instant that represents the last accessed time
     * @return The session
     */
    Session setLastAccessedTime(Instant instant);

    /**
     * Specifies the duration between client requests before session should be invalidated.
     *
     * @param duration A duration specifying the max inactive interval
     * @return The session
     */
    Session setMaxInactiveInterval(Duration duration);

    /**
     * Returns the maximum time interval as a {@link Duration} that sessions will  be kept open between client accesses.
     * After this interval, the servlet container will invalidate the session.  The maximum time interval can be set
     * with the <code>setMaxInactiveInterval</code> method.
     *
     * @return A duration specifying the time should session should remain open between client requests
     * @see #setMaxInactiveInterval
     */
    Duration getMaxInactiveInterval();

    /**
     * @return Is the session a newly created and unsaved session
     */
    boolean isNew();

    /**
     * @return Has the session been modified
     */
    boolean isModified();

    /**
     * Retrieve an attribute for the given name.
     *
     * @param attr The attribute name
     * @return An {@link Optional} of the attribute
     */
    default Optional<Object> get(CharSequence attr) {
        return get(attr, Object.class);
    }

    /**
     * @return Whether the session has expired
     */
    default boolean isExpired() {
        Duration maxInactiveInterval = getMaxInactiveInterval();
        if (maxInactiveInterval == null || maxInactiveInterval.isNegative()) {
            return false;
        } else {
            Instant now = Instant.now();
            return now.minus(maxInactiveInterval).compareTo(getLastAccessedTime()) >= 0;
        }
    }
}
