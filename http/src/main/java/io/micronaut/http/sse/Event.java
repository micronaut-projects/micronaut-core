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
package io.micronaut.http.sse;

import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Represents a Server Sent Event. See https://www.w3.org/TR/2011/WD-eventsource-20111020/.
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @since 1.0
 */
public interface Event<T> {

    /**
     * The id parameter.
     */
    String ID = "id";

    /**
     * The event parameter.
     */
    String EVENT = "event";

    /**
     * The data parameter.
     */
    String DATA = "data";

    /**
     * The retry parameter.
     */
    String RETRY = "retry";

    /**
     * @return The data object to write
     */
    T getData();

    /**
     * @return The ID of the event, or null if there is no ID
     */
    String getId();

    /**
     * @return The name of the event
     */
    String getName();

    /**
     * @return A comment for the event, or null if there is no comment
     */
    String getComment();

    /**
     * @return The duration to retry
     */
    Duration getRetry();

    /**
     * Sets the retry duration.
     *
     * @param duration The duration
     * @return The event
     */
    Event<T> retry(@Nullable Duration duration);

    /**
     * Sets the id.
     *
     * @param id The id to set
     * @return The event
     */
    Event<T> id(@Nullable String id);

    /**
     * Sets the event name.
     *
     * @param name The event name
     * @return The event
     */
    Event<T> name(@Nullable String name);

    /**
     * Sets the event comment.
     *
     * @param comment The Event comment
     * @return The event
     */
    Event<T> comment(@Nullable String comment);

    /**
     * Constructs a new event for the given data.
     *
     * @param data The data
     * @param <ET> The data type
     * @return The event instance
     */
    static <ET> Event<ET> of(ET data) {
        ArgumentUtils.check("data", data).notNull();
        return new DefaultEvent<>(data);
    }

    /**
     * Constructs a new event for the given data.
     *
     * @param event The event
     * @param data  The data
     * @param <ET>  The data type
     * @return The event instance
     */
    static <ET> Event<ET> of(Event event, ET data) {
        ArgumentUtils.check("data", data).notNull();
        return new DefaultEvent<>(data)
            .id(event.getId())
            .comment(event.getComment())
            .name(event.getName())
            .retry(event.getRetry());
    }
}
