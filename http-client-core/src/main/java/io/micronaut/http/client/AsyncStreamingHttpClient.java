/*
 * Copyright 2017-2026 original authors
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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The {@link StreamingHttpClient} with {@link CompletionStage} results and the body pulled one
 * element at a time, instead of Reactive Streams: an {@link AsyncHttpClient} that also streams
 * response bodies.
 *
 * <p>An exchange completes with the response, or its elements, once the status and the headers
 * arrived. The body is a {@link BodyElements} cursor: the next element is read from the
 * connection when {@link BodyElements#next()} is called, so nothing is read ahead of the caller.
 * The connection stays reserved until the elements were read to the end or closed. The stages
 * complete on a thread chosen by the client, usually an I/O thread: a consumer must not
 * block.</p>
 *
 * <p>When the response has an error status, the stage fails with an
 * {@link io.micronaut.http.client.exceptions.HttpClientResponseException} carrying the response
 * and its body, decoded into the error type.</p>
 *
 * <p>An instance is obtained from a {@link StreamingHttpClient} with
 * {@link StreamingHttpClient#toAsync()}, or injected.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncStreamingHttpClient extends AsyncHttpClient {

    /**
     * Perform an HTTP request and read the response body as its bytes arrive, see
     * {@link StreamingHttpClient#exchangeStream(HttpRequest, Argument)}. The elements are the
     * pieces of the body as they were received, as heap buffers that are not reference counted.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @return A {@link CompletionStage} that completes with the response, whose body is the pieces
     * of the response body
     */
    <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request, Argument<?> errorType);

    /**
     * Perform an HTTP request and read the response body as its bytes arrive.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link CompletionStage} that completes with the response, whose body is the pieces
     * of the response body
     * @see #exchangeStream(HttpRequest, Argument)
     */
    default <I> CompletionStage<HttpResponse<BodyElements<ByteBuffer<?>>>> exchangeStream(HttpRequest<I> request) {
        return exchangeStream(request, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request and read the response body as its bytes arrive, see
     * {@link StreamingHttpClient#dataStream(HttpRequest, Argument)}:
     * {@link #exchangeStream(HttpRequest, Argument)} without the response.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @return A {@link CompletionStage} that completes with the pieces of the response body, once
     * the response arrived
     */
    default <I> CompletionStage<BodyElements<ByteBuffer<?>>> dataStream(HttpRequest<I> request, Argument<?> errorType) {
        return exchangeStream(request, errorType)
            .thenApply(response -> Objects.requireNonNull(response.body(), "The response has no elements"));
    }

    /**
     * Perform an HTTP request and read the response body as its bytes arrive.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link CompletionStage} that completes with the pieces of the response body, once
     * the response arrived
     * @see #dataStream(HttpRequest, Argument)
     */
    default <I> CompletionStage<BodyElements<ByteBuffer<?>>> dataStream(HttpRequest<I> request) {
        return dataStream(request, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array, see
     * {@link StreamingHttpClient#jsonStream(HttpRequest, Argument, Argument)}. Each element is
     * decoded when it is read. Without a content type, the response is read as
     * {@code application/x-json-stream}.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param type      The type of an element
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @param <O>       The type of an element
     * @return A {@link CompletionStage} that completes with the elements, once the response arrived
     */
    <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType);

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array.
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of an element
     * @param <I>     The request body type
     * @param <O>     The type of an element
     * @return A {@link CompletionStage} that completes with the elements, once the response arrived
     * @see #jsonStream(HttpRequest, Argument, Argument)
     */
    default <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type) {
        return jsonStream(request, type, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array.
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of an element
     * @param <I>     The request body type
     * @param <O>     The type of an element
     * @return A {@link CompletionStage} that completes with the elements, once the response arrived
     * @see #jsonStream(HttpRequest, Argument, Argument)
     */
    default <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Class<O> type) {
        return jsonStream(request, Argument.of(type));
    }

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array as maps.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link CompletionStage} that completes with the elements, once the response arrived
     * @see #jsonStream(HttpRequest, Argument, Argument)
     */
    default <I> CompletionStage<BodyElements<Map<String, Object>>> jsonStream(HttpRequest<I> request) {
        return jsonStream(request, Argument.mapOf(String.class, Object.class));
    }
}
