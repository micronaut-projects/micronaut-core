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
import java.util.Map;

/**
 * Extended version of {@link StreamingHttpClient} that exposes an RxJava 2.x interface.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface RxStreamingHttpClient extends StreamingHttpClient, RxHttpClient {

    @Override
    <I> Flowable<ByteBuffer<?>> dataStream(HttpRequest<I> request);

    @Override
    <I> Flowable<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request);

    @Override
    <I> Flowable<Map<String, Object>> jsonStream(HttpRequest<I> request);

    @Override
    <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Argument<O> type);

    @Override
    default <I, O> Flowable<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return (Flowable<O>) StreamingHttpClient.super.jsonStream(request, type);
    }

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of an application. Within Micronaut use
     * {@link javax.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @return The client
     */
    static RxStreamingHttpClient create(URL url) {
        return HttpClientConfiguration.createStreamingClient(url);
    }
}
