/*
 * Copyright 2018 original authors
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
package org.particleframework.http.client;

import org.particleframework.core.async.publisher.Publishers;
import org.particleframework.core.io.buffer.ByteBuffer;
import org.particleframework.core.type.Argument;
import org.particleframework.http.HttpRequest;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.MutableHttpRequest;
import org.particleframework.http.client.exceptions.HttpClientException;
import org.particleframework.http.client.exceptions.HttpClientResponseException;
import org.particleframework.http.sse.Event;
import org.reactivestreams.Publisher;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;

/**
 * A non-blocking HTTP client interface designed around the Particle API and Reactive Streams.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpClient {

    /**
     * @return A blocking HTTP client suitable for testing and non-production scenarios.
     */
    BlockingHttpClient toBlocking();

    /**
     * Perform a request a listen for a stream of Server Sent events. Expects a response of type {@link org.particleframework.http.MediaType#TEXT_EVENT_STREAM}
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits a stream of response objects with the body of each response object containing a {@link Event}
     */
    <I> Publisher<HttpResponse<Event<ByteBuffer<?>>>> eventStream(HttpRequest<I> request);

    /**
     * Perform a request a listen for a stream of Server Sent events. Expects a response of type {@link org.particleframework.http.MediaType#TEXT_EVENT_STREAM}
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @param <O>     The event type
     * @return A {@link Publisher} that emits a stream of response objects with the body of each response object containing a {@link Event}
     */
    <I, O> Publisher<HttpResponse<Event<O>>> eventStream(HttpRequest<I> request, Argument<O> bodyType);

    /**
     * Perform an HTTP request and receive data chunk by chunk as it becomes available
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I> Publisher<HttpResponse<ByteBuffer<?>>> dataStream(HttpRequest<I> request);

    /**
     * Perform an HTTP request and receive data as a stream of JSON objects as they become available
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I> Publisher<HttpResponse<Map<String, Object>>> jsonStream(HttpRequest<I> request);

    /**
     * Perform an HTTP request and receive data as a stream of JSON objects as they become available
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O> Publisher<HttpResponse<O>> jsonStream(HttpRequest<I> request, Argument<O> bodyType);

    /**
     * <p>Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type</p>
     * <p>
     * <p>This method will send a {@code Content-Length} header and except a content length header the response and is designed for simple non-streaming exchanges of data</p>
     * <p>
     * <p>By default the exchange {@code Content-Type} is application/json, unless otherwise specified in the passed {@link HttpRequest}</p>
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType);

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher}
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I> Publisher<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return exchange(request, ByteBuffer.class);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return exchange(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return Publishers.map(exchange(request, bodyType), response ->
                response.getBody()
                        .orElseThrow(() -> new HttpClientResponseException(
                                "Empty body",
                                response
                        )));
    }


    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned {@link Publisher} and converting
     * the response body to the specified type
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request and receive data as a stream of JSON objects as they become available
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<HttpResponse<O>> jsonStream(HttpRequest<I> request, Class<O> bodyType) {
        return jsonStream(request, Argument.of(bodyType));
    }

    /**
     * Create a new {@link HttpClient}
     *
     * @param url The base URL
     * @return The client
     */
    static HttpClient create(URL url) {
        return new DefaultHttpClient(url);
    }
}
