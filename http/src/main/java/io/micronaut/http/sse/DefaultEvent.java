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

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Produces;

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
    private String id;
    private String name;
    private String comment;
    private Duration retry;

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
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getComment() {
        return comment;
    }

    @Override
    public Duration getRetry() {
        return retry;
    }

    @Override
    public Event<T> retry(Duration duration) {
        this.retry = duration;
        return this;
    }

    @Override
    public Event<T> id(String id) {
        this.id = id;
        return this;
    }

    @Override
    public Event<T> name(String name) {
        this.name = name;
        return this;
    }

    @Override
    public Event<T> comment(String comment) {
        this.comment = comment;
        return this;
    }
}
