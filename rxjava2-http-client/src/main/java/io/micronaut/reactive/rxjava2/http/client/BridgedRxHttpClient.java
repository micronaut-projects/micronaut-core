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
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.SseClient;
import io.micronaut.http.sse.Event;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * Internal bridge for the HTTP client.
 *
 * @author Serigo del Amo
 * @since 3.0.0
 */
@Internal
class BridgedRxHttpClient implements RxHttpClient, RxSseClient, RxStreamingHttpClient {

    private final HttpClient httpClient;
    private final SseClient sseClient;
    private final StreamingHttpClient streamingHttpClient;

    /**
     * Default constructor.
     * @param httpClient The target client
     * @param sseClient Server Sent Events HTTP Client
     * @param streamingHttpClient Streaming HTTP Client
     */
    BridgedRxHttpClient(HttpClient httpClient,
                        SseClient sseClient,
                        StreamingHttpClient streamingHttpClient) {
        this.httpClient = httpClient;
        this.sseClient = sseClient;
        this.streamingHttpClient = streamingHttpClient;
    }

    @Override
    public BlockingHttpClient toBlocking() {
        return null;
    }

    @Override
    public <I, O, E> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flowable.fromPublisher(httpClient.exchange(request, bodyType, errorType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(httpClient.exchange(request, bodyType));
    }

    @Override
    public <I, O, E> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flowable.fromPublisher(httpClient.retrieve(request, bodyType));
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flowable.fromPublisher(httpClient.exchange(request));
    }

    @Override
    public Flowable<HttpResponse<ByteBuffer>> exchange(String uri) {
        return Flowable.fromPublisher(httpClient.exchange(uri));
    }

    @Override
    public <O> Flowable<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return Flowable.fromPublisher(httpClient.exchange(uri, bodyType));
    }

    @Override
    public <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(httpClient.exchange(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(httpClient.retrieve(request, bodyType));
    }

    @Override
    public <I, O> Flowable<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(httpClient.retrieve(request, bodyType));
    }

    @Override
    public <I> Flowable<String> retrieve(HttpRequest<I> request) {
        return Flowable.fromPublisher(httpClient.retrieve(request));
    }

    @Override
    public Flowable<String> retrieve(String uri) {
        return Flowable.fromPublisher(httpClient.retrieve(uri));
    }

    @Override
    public boolean isRunning() {
        return httpClient.isRunning();
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

    @Override
    public <I> Flowable<ByteBuffer<?>> dataStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(streamingHttpClient.dataStream(request));
    }

    @Override
    public <I> Flowable<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(streamingHttpClient.exchangeStream(request));
    }

    @Override
    public <I> Publisher<Map<String, Object>> jsonStream(HttpRequest<I> request) {
        return Flowable.fromPublisher(streamingHttpClient.jsonStream(request));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Argument<O> type) {
        return Flowable.fromPublisher(streamingHttpClient.jsonStream(request, type));
    }

    @Override
    public <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return Flowable.fromPublisher(streamingHttpClient.jsonStream(request, type));
    }
}
