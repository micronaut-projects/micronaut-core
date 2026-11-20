/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.http.client.sse;

import io.micronaut.http.sse.Event;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * A block without data that set the last event id: it dispatches no event, but takes its place
 * among the events, so that the {@link EventStreamState} takes the id once the events before it
 * were delivered. It is never handed to the subscriber.
 *
 * @param id  The last event id
 * @param <T> The event data type
 * @author Denis Stepanov
 * @since 5.3.0
 */
record ControlEvent<T>(String id) implements Event<T> {

    /**
     * @param <B> The event data type
     * @return This marker, for another event data type
     */
    @SuppressWarnings("unchecked")
    <B> ControlEvent<B> cast() {
        return (ControlEvent<B>) this;
    }

    @Override
    public T getData() {
        throw new IllegalStateException("A block without data has no data");
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public @Nullable String getName() {
        return null;
    }

    @Override
    public @Nullable String getComment() {
        return null;
    }

    @Override
    public @Nullable Duration getRetry() {
        return null;
    }

    @Override
    public Event<T> retry(@Nullable Duration duration) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Event<T> id(@Nullable String id) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Event<T> name(@Nullable String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Event<T> comment(@Nullable String comment) {
        throw new UnsupportedOperationException();
    }
}
