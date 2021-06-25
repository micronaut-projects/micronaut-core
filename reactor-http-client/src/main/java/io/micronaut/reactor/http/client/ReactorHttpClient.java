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
package io.micronaut.reactor.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.StreamingHttpClient;
import io.micronaut.http.client.sse.SseClient;
import reactor.core.publisher.Flux;

import java.net.URL;

/**
 * Reactor variation of the {@link HttpClient} interface.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public interface ReactorHttpClient extends HttpClient {

    @Override
    default <I, O> Flux<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return Flux.from(HttpClient.super.exchange(request, bodyType));
    }

    @Override
    <I, O, E> Flux<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType);

    @Override
    default <I, O, E> Flux<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flux.from(HttpClient.super.retrieve(request, bodyType, errorType));
    }

    @Override
    default <I> Flux<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flux.from(HttpClient.super.exchange(request));
    }

    @Override
    default Flux<HttpResponse<ByteBuffer>> exchange(String uri) {
        return Flux.from(HttpClient.super.exchange(uri));
    }

    @Override
    default <O> Flux<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return Flux.from(HttpClient.super.exchange(uri, bodyType));
    }

    @Override
    default <I, O> Flux<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flux.from(HttpClient.super.exchange(request, bodyType));
    }

    @Override
    default <I, O> Flux<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return Flux.from(HttpClient.super.retrieve(request, bodyType));
    }

    @Override
    default <I, O> Flux<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(
                request,
                Argument.of(bodyType),
                DEFAULT_ERROR_TYPE
        );
    }

    @Override
    default <I> Flux<String> retrieve(HttpRequest<I> request) {
        return retrieve(
                request,
                Argument.STRING,
                DEFAULT_ERROR_TYPE
        );
    }

    @Override
    default Flux<String> retrieve(String uri) {
        return retrieve(
                HttpRequest.GET(uri),
                Argument.STRING,
                DEFAULT_ERROR_TYPE
        );
    }

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of a
     * Micronaut application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static ReactorHttpClient create(@Nullable URL url) {
        return new BridgedReactorHttpClient(HttpClient.create(url),
                HttpClient.createSseClient(url),
                HttpClient.createStreamingClient(url));
    }

    /**
     * Create a new {@link HttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static ReactorHttpClient create(@Nullable URL url, HttpClientConfiguration configuration) {
        return new BridgedReactorHttpClient(HttpClient.create(url, configuration),
                HttpClient.createSseClient(url, configuration),
                HttpClient.createStreamingClient(url, configuration));
    }
}
