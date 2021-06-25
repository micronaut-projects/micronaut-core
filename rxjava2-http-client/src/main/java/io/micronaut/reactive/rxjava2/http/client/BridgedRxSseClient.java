/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.reactive.rxjava2.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.reactivex.Flowable;

/**
 * RxJava 2 bridge for the Server side events HTTP client.
 *
 * @author Sergio del Amo
 * @since 3.0.0
 */
@Internal
class BridgedRxSseClient implements RxSseClient {

    private final SseClient sseClient;

    /**
     * Default constructor.
     * @param sseClient Server Sent Events HTTP Client
     */
    BridgedRxSseClient(SseClient sseClient) {
        this.sseClient = sseClient;
    }

    @Override
    public <I> Flowable<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(sseClient.eventStream(request));
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType) {
        return Flowable.fromPublisher(sseClient.eventStream(request, eventType));
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(HttpRequest<I> request, Class<B> eventType) {
        return Flowable.fromPublisher(sseClient.eventStream(request, eventType));
    }

    @Override
    public <B> Flowable<Event<B>> eventStream(String uri, Class<B> eventType) {
        return Flowable.fromPublisher(sseClient.eventStream(uri, eventType));
    }

    @Override
    public <B> Flowable<Event<B>> eventStream(String uri, Argument<B> eventType) {
        return Flowable.fromPublisher(sseClient.eventStream(uri, eventType));
    }
}
