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
package io.micronaut.http.client;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.reactivex.Flowable;

import java.net.URL;

/**
 * Extended version of {@link HttpClient} that exposes an RxJava 2.x specific interface.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface RxHttpClient extends HttpClient {

       @Override
    default <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(HttpClient.super.exchange(request, bodyType));
    }

    @Override
    <I, O, E> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType);

    @Override
    default <I, O, E> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Flowable.fromPublisher(HttpClient.super.retrieve(request, bodyType, errorType));
    }

    @Override
    default <I> Flowable<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return Flowable.fromPublisher(HttpClient.super.exchange(request));
    }

    @Override
    default Flowable<HttpResponse<ByteBuffer>> exchange(String uri) {
        return Flowable.fromPublisher(HttpClient.super.exchange(uri));
    }

    @Override
    default <O> Flowable<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return Flowable.fromPublisher(HttpClient.super.exchange(uri, bodyType));
    }

    @Override
    default <I, O> Flowable<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return Flowable.fromPublisher(HttpClient.super.exchange(request, bodyType));
    }

    @Override
    default <I, O> Flowable<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return Flowable.fromPublisher(HttpClient.super.retrieve(request, bodyType));
    }

    @Override
    default <I, O> Flowable<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(
                request,
                Argument.of(bodyType),
                DEFAULT_ERROR_TYPE
        );
    }

    @Override
    default <I> Flowable<String> retrieve(HttpRequest<I> request) {
        return retrieve(
                request,
                Argument.STRING,
                DEFAULT_ERROR_TYPE
        );
    }

    @Override
    default Flowable<String> retrieve(String uri) {
        return retrieve(
                HttpRequest.GET(uri),
                Argument.STRING,
                DEFAULT_ERROR_TYPE
        );
    }

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link javax.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    static RxHttpClient create(URL url) {
        return HttpClientConfiguration.createClient(url);
    }
}
