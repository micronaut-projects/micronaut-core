/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.http.client;

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;

import java.util.Map;

/**
 * Extended version of the {@link HttpClient} that supports streaming responses.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface StreamingHttpClient extends HttpClient {

    /**
     * Request a stream of data where each emitted item is a {@link ByteBuffer} instance.
     *
     * @param request The request
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits a stream of {@link ByteBuffer} instances
     */
    <I> Publisher<ByteBuffer<?>> dataStream(HttpRequest<I> request);

    /**
     * Requests a stream data where each emitted item is a {@link ByteBuffer} wrapped in the {@link HttpResponse} object
     * (which remains the same for each emitted item).
     *
     * @param request The {@link HttpRequest}
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits a stream of {@link ByteBuffer} instances wrapped by a {@link HttpResponse}
     */
    <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(HttpRequest<I> request);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     * <p>
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I> Publisher<Map<String, Object>> jsonStream(HttpRequest<I> request);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     * <p>
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription. Incoming data
     * is buffered.</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of object to convert the JSON into
     * @param <I>     The request body type
     * @param <O>     The response type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Argument<O> type);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     * <p>
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of object to convert the JSON into
     * @param <I>     The request body type
     * @param <O>     The response type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<O> jsonStream(HttpRequest<I> request, Class<O> type) {
        return jsonStream(request, Argument.of(type));
    }
}
