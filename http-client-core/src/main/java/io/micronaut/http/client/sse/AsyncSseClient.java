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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.sse.Event;

import java.io.Closeable;
import java.util.Objects;
import java.util.concurrent.CompletionStage;

/**
 * The {@link SseClient} with {@link CompletionStage} results and events pulled one at a time,
 * instead of Reactive Streams: the counterpart of {@link io.micronaut.http.client.AsyncHttpClient}
 * for server-sent events.
 *
 * <p>An exchange completes with the response once its status and headers arrived. Its body is a
 * {@link BodyElements} cursor over the events: the next event is read from the connection and
 * decoded when {@link BodyElements#next()} is called, so nothing is read ahead of the caller.</p>
 *
 * <pre>{@code
 * AsyncSseClient client = sseClient.toAsyncSse();
 * client.exchangeEventStream(request, Message.class)
 *     .thenCompose(response -> {
 *         sessionId.set(response.getHeaders().get("Mcp-Session-Id"));
 *         // the next event is read when the stage of the previous one completed
 *         return response.body().forEach(event -> messages.saveAsync(event.getData()));
 *     });
 * }</pre>
 *
 * <p>The connection stays reserved until the events were read to the end or the
 * {@link BodyElements} was closed: close the elements of a stream that is not read to the end.
 * The stages complete on a thread chosen by the client, usually an I/O thread: a consumer must
 * not block.</p>
 *
 * <p>An instance is obtained from an {@link SseClient} with {@link SseClient#toAsyncSse()}.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Experimental
public interface AsyncSseClient extends Closeable {

    /**
     * Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a
     * single body, see {@link SseClient#exchangeEventStream(HttpRequest, Argument, Argument)}.
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
     *     <li>When the response has an error status, the stage fails with an
     *     {@link io.micronaut.http.client.exceptions.HttpClientResponseException} carrying the
     *     response and its body, decoded into the error type.</li>
     * </ul>
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param errorType The type that the response body should be coerced into if the server
     *                  responds with an error
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the
     * elements of the response
     */
    <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType);

    /**
     * Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a
     * single body.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the
     * elements of the response
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType) {
        return exchangeEventStream(request, eventType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a
     * single body.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event data type
     * @return A {@link CompletionStage} that completes with the response, whose body is the
     * elements of the response
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     */
    default <I, B> CompletionStage<HttpResponse<BodyElements<Event<B>>>> exchangeEventStream(HttpRequest<I> request, Class<B> eventType) {
        return exchangeEventStream(request, Argument.of(eventType));
    }

    /**
     * Perform an HTTP request and read the events of the response, see
     * {@link SseClient#eventStream(HttpRequest, Argument, Argument)}. The {@code Accept} header of
     * a {@link MutableHttpRequest} is set to {@code text/event-stream}, otherwise this is
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
        return exchangeEventStream(request, eventType, errorType)
            .thenApply(response -> Objects.requireNonNull(response.body(), "The response has no elements"));
    }

    /**
     * Perform an HTTP request and read the events of the response.
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
     * Perform an HTTP request and read the events of the response.
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
     * Perform an HTTP GET request and read the events of the response.
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
     * Perform an HTTP GET request and read the events of the response.
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
