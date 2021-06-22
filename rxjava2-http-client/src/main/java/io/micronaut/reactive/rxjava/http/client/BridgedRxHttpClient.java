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
package io.micronaut.reactive.rxjava.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.ReactorHttpClient;
import io.micronaut.http.client.ReactorStreamingHttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.ReactorSseClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.reactivex.Flowable;
import java.util.Map;

/**
 * Internal bridge for the HTTP client.
 *
 * @author Serigo del Amo
 * @since 3.0.0
 */
@Internal
class BridgedRxHttpClient implements RxHttpClient, RxSseClient, RxStreamingHttpClient {

    private final ReactorHttpClient reactorHttpClient;

    /**
     * Default constructor.
     * @param reactorHttpClient The target client
     */
    BridgedRxHttpClient(ReactorHttpClient reactorHttpClient) {
        this.reactorHttpClient = reactorHttpClient;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return null;
    }

    @Override
    public <I, O, E> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(request, bodyType, errorType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(request, bodyType));
    }

    @Override
    public <I, O, E> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).retrieve(request, bodyType));
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(request));
    }

    @Override
    public Flowable<HttpResponse<ByteBuffer>> exchange(String uri) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(uri));
    }

    @Override
    public <O> Flowable<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(uri, bodyType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).retrieve(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).retrieve(request, bodyType));
    }

    @Override
    public <I> Flowable<String> retrieve(HttpRequest<I> request) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).retrieve(request));
    }

    @Override
    public Flowable<String> retrieve(String uri) {
        return Flowable.fromPublisher(((HttpClient) reactorHttpClient).retrieve(uri));
    }

    @Override
    public boolean isRunning() {
        return reactorHttpClient.isRunning();
    }

    @Override
    public <I> Flowable<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(((SseClient) reactorHttpClient).eventStream(request));
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType) {
        return Flowable.fromPublisher(((SseClient) reactorHttpClient).eventStream(request, eventType));
    }

    @Override
    public <I, B> Flowable<Event<B>> eventStream(HttpRequest<I> request, Class<B> eventType) {
        return Flowable.fromPublisher(((SseClient) reactorHttpClient).eventStream(request, eventType));
    }

    @Override
    public <B> Flowable<Event<B>> eventStream(String uri, Class<B> eventType) {
        return Flowable.fromPublisher(((SseClient) reactorHttpClient).eventStream(uri, eventType));
    }

    @Override
    public <B> Flowable<Event<B>> eventStream(String uri, Argument<B> eventType) {
        return Flowable.fromPublisher(((SseClient) reactorHttpClient).eventStream(uri, eventType));
    }

    @Override
    public <I> Flowable<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(((StreamingHttpClient) reactorHttpClient).dataStream(request));
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(((StreamingHttpClient) reactorHttpClient).exchangeStream(request));
    }

    @Override
    public <I> Flowable<Map<String, Object>> jsonStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(((StreamingHttpClient) reactorHttpClient).jsonStream(request));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Argument<O> type) {
        return Flowable.fromPublisher(((StreamingHttpClient) reactorHttpClient).jsonStream(request, type));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return Flowable.fromPublisher(((StreamingHttpClient) reactorHttpClient).jsonStream(request, type));
    }
}
