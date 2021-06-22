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
package io.micronaut.http.client.sse;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.sse.Event;
import reactor.core.publisher.Flux;

/**
 * Extended version of {@link SseClient} for Project Reactor.
 *
 * @author Sergio del Amo
 * @since 3.3.0
 */
public interface ReactorSseClient extends SseClient {

    @Override
    <I> Flux<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request);

    @Override
    <I, B> Flux<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType);

    @Override
    default <I, B> Flux<Event<B>> eventStream(HttpRequest<I> request, Class<B> eventType) {
        return (Flux<Event<B>>) SseClient.super.eventStream(request, eventType);
    }

    @Override
    default <B> Flux<Event<B>> eventStream(String uri, Class<B> eventType) {
        return (Flux<Event<B>>) SseClient.super.eventStream(uri, eventType);
    }

    @Override
    default <B> Flux<Event<B>> eventStream(String uri, Argument<B> eventType) {
        return (Flux<Event<B>>) SseClient.super.eventStream(uri, eventType);
    }
}
