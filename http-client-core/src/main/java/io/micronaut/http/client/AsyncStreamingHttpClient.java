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
import io.micronaut.core.io.buffer.ReadBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.sse.EventStreams;
import io.micronaut.http.sse.Event;
import io.micronaut.json.JsonMapper;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/**
 * The {@link StreamingHttpClient} with {@link CompletionStage} results and the body pulled one
 * element at a time, instead of Reactive Streams: an {@link AsyncHttpClient} that also streams
 * response bodies.
 *
 * <p>An exchange completes with the response, or its elements, once the status and the headers
 * arrived. The body is a {@link BodyElements} cursor: the next element is read from the
 * connection when {@link BodyElements#next()} is called, so nothing is decoded ahead of the
 * caller.
 * The connection stays reserved until the elements were read to the end or closed. The stages
 * complete on a thread chosen by the client, usually an I/O thread: a consumer must not
 * block.</p>
 *
 * <p>When the response has an error status, the stage fails with an
 * {@link io.micronaut.http.client.exceptions.HttpClientResponseException} carrying the response
 * and its body, decoded into the error type.</p>
 *
 * <p>It also reads server-sent events, the counterpart of
 * {@link io.micronaut.http.client.sse.SseClient}, and the elements of JSON streams with their
 * response.</p>
 *
 * <p>An instance is obtained from a {@link StreamingHttpClient} with
 * {@link StreamingHttpClient#toAsyncStreaming()}, or injected.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncStreamingHttpClient extends AsyncHttpClient {

    /**
     * Read response pieces without copying the transport buffers. The caller owns every
     * returned {@link ReadBuffer} and must close it, even when processing fails. Closing the
     * elements releases unread pieces, not pieces already handed to the caller.
     *
     * <p>This optional operation is supported by the native Netty and JDK clients. The default
     * implementation fails with {@link UnsupportedOperationException}. Unlike
     * {@link #exchangeStream(HttpRequest, Argument)}, elements may be reference counted.</p>
     *
     * @param request The request
     * @param errorType The error body type
     * @param <I> The request body type
     * @return The response with caller-owned pieces
     * @since 5.3.0
     */
    default <I> CompletionStage<HttpResponse<BodyElements<ReadBuffer>>> exchangeReadBuffers(HttpRequest<I> request, Argument<?> errorType) {
        return CompletableFuture.failedStage(new UnsupportedOperationException("This client does not expose owned response buffers"));
    }

    /**
     * Read caller-owned response pieces using the default error type.
     *
     * @param request The request
     * @param <I> The request body type
     * @return The response with pieces that the caller must close
     * @since 5.3.0
     */
    default <I> CompletionStage<HttpResponse<BodyElements<ReadBuffer>>> exchangeReadBuffers(HttpRequest<I> request) {
        return exchangeReadBuffers(request, HttpClient.DEFAULT_ERROR_TYPE);
    }

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
        return ElementsStages.mapResponse(exchangeStream(request, errorType),
            response -> Objects.requireNonNull(response.body(), "The response has no elements"));
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
    default <I, O> CompletionStage<BodyElements<O>> jsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType) {
        return ElementsStages.mapResponse(exchangeJsonStream(request, type, errorType),
            response -> Objects.requireNonNull(response.body(), "The response has no elements"));
    }

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array, with the
     * response: {@link #jsonStream(HttpRequest, Argument, Argument)} with the status and the
     * headers of the response.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param type      The type of an element
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @param <O>       The type of an element
     * @return A {@link CompletionStage} that completes with the response, whose body is the
     * elements, once the response arrived
     */
    <I, O> CompletionStage<HttpResponse<BodyElements<O>>> exchangeJsonStream(HttpRequest<I> request, Argument<O> type, Argument<?> errorType);

    /**
     * Perform an HTTP request and read the elements of a JSON stream or a JSON array, with the
     * response.
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of an element
     * @param <I>     The request body type
     * @param <O>     The type of an element
     * @return A {@link CompletionStage} that completes with the response, whose body is the
     * elements, once the response arrived
     * @see #exchangeJsonStream(HttpRequest, Argument, Argument)
     */
    default <I, O> CompletionStage<HttpResponse<BodyElements<O>>> exchangeJsonStream(HttpRequest<I> request, Argument<O> type) {
        return exchangeJsonStream(request, type, HttpClient.DEFAULT_ERROR_TYPE);
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

    /**
     * Perform an HTTP request whose response is either a stream of server-sent
     * {@link Event events} or a single body, see
     * {@link io.micronaut.http.client.sse.SseClient#exchangeEventStream(HttpRequest, Argument, Argument)}.
     *
     * <ul>
     *     <li>The {@code Accept} header of the request is kept when it accepts
     *     {@code text/event-stream}, for example {@code application/json, text/event-stream}.
     *     Otherwise {@code text/event-stream} is added to it.</li>
     *     <li>When the content type of the response is {@code text/event-stream}, the elements are
     *     the events of the body, each decoded as it is read. The data of an event is decoded as
     *     JSON into the event type.</li>
     *     <li>Otherwise the whole body is decoded into the event type, using the content type of
     *     the response, and is the only element.</li>
     *     <li>When the body holds no event, for example a {@code 202 Accepted} response, the
     *     elements are empty.</li>
     * </ul>
     *
     * <p>The default implementation reads the pieces of {@link #exchangeStream(HttpRequest, Argument)}
     * and decodes them as server-sent events, the data with the default {@link JsonMapper}.</p>
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the events
     */
    default <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        EventStreams.acceptEvents(request);
        return ElementsStages.mapResponse(exchangeStream(request, errorType),
            response -> EventStreams.response(response, eventType, HttpClientConfiguration.DEFAULT_MAX_CONTENT_LENGTH));
    }

    /**
     * Perform an HTTP request whose response is either a stream of server-sent events or a
     * single body.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the events
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType) {
        return exchangeEventStream(request, eventType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request whose response is either a stream of server-sent events or a
     * single body.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the events
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Class<B> eventType) {
        return exchangeEventStream(request, Argument.of(eventType));
    }

    /**
     * Perform an HTTP request and read the server-sent events of the response, see
     * {@link io.micronaut.http.client.sse.SseClient#eventStream(HttpRequest, Argument, Argument)}.
     * The {@code Accept} header of a {@link MutableHttpRequest} is set to
     * {@code text/event-stream}, otherwise this is
     * {@link #exchangeEventStream(HttpRequest, Argument, Argument)} without the response.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the events, once the response arrived
     */
    default <I, B> CompletionStage<BodyElements<Event<B>>> eventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        if (request instanceof MutableHttpRequest<?> mutableRequest) {
            mutableRequest.getHeaders().set(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM);
        }
        return ElementsStages.mapResponse(exchangeEventStream(request, eventType, errorType),
            response -> Objects.requireNonNull(response.body(), "The response has no elements"));
    }

    /**
     * Perform an HTTP request and read the server-sent events of the response.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the events, once the response arrived
     * @see #eventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<BodyElements<Event<B>>> eventStream(HttpRequest<I> request, Argument<B> eventType) {
        return eventStream(request, eventType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request and read the server-sent events of the response.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the events, once the response arrived
     * @see #eventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<BodyElements<Event<B>>> eventStream(HttpRequest<I> request, Class<B> eventType) {
        return eventStream(request, Argument.of(eventType));
    }

    /**
     * Perform an HTTP GET request and read the server-sent events of the response.
     *
     * @param uri       The request URI
     * @param eventType The event data type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the events, once the response arrived
     * @see #eventStream(HttpRequest, Argument, Argument)
     */
    default <B> CompletionStage<BodyElements<Event<B>>> eventStream(String uri, Class<B> eventType) {
        return eventStream(HttpRequest.GET(uri), eventType);
    }

    /**
     * Perform an HTTP GET request and read the server-sent events of the response.
     *
     * @param uri       The request URI
     * @param eventType The event data type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the events, once the response arrived
     * @see #eventStream(HttpRequest, Argument, Argument)
     */
    default <B> CompletionStage<BodyElements<Event<B>>> eventStream(String uri, Argument<B> eventType) {
        return eventStream(HttpRequest.GET(uri), eventType);
    }
}
