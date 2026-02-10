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
package io.micronaut.http.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * Default implementation of the {@link Event} interface.
 *
 * @param <T> The event type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
@Produces(MediaType.TEXT_EVENT_STREAM)
class DefaultEvent<T> implements Event<T> {

    private final T data;
    private @Nullable String id;
    private @Nullable String name;
    private @Nullable String comment;
    private @Nullable Duration retry;

    /**
     * @param data The event
     */
    DefaultEvent(T data) {
        this.data = data;
    }

    @Override
    public T getData() {
        return data;
    }

    @Override
    public @Nullable String getId() {
        return id;
    }

    @Override
    public @Nullable String getName() {
        return name;
    }

    @Override
    public @Nullable String getComment() {
        return comment;
    }

    @Override
    public @Nullable Duration getRetry() {
        return retry;
    }

    @Override
    public Event<T> retry(@Nullable Duration duration) {
        this.retry = duration;
        return this;
    }

    @Override
    public Event<T> id(@Nullable String id) {
        this.id = id;
        return this;
    }

    @Override
    public Event<T> name(@Nullable String name) {
        this.name = name;
        return this;
    }

    @Override
    public Event<T> comment(@Nullable String comment) {
        this.comment = comment;
        return this;
    }
}
