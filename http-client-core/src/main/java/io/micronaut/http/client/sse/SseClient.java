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
package io.micronaut.http.client.sse;

import org.jspecify.annotations.Nullable;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

import java.net.URL;

/**
 * A client for streaming Server Sent Event streams.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface SseClient {

    /**
     * The {@link AsyncSseClient} view of this client, with {@link java.util.concurrent.CompletionStage}
     * results and events pulled one at a time instead of Reactive Streams. The view shares this
     * client: closing it closes this client.
     * <p>The default implementation adapts {@link #exchangeEventStream(HttpRequest, Argument, Argument)}.</p>
     *
     * @return The async view of this client
     * @since 5.3.0
     */
    @Experimental
    default AsyncSseClient toAsyncSse() {
        return new DefaultAsyncOverReactiveSseClient(this);
    }

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented as a {@link ByteBuffer}
     */
    <I> Publisher<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request);

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>     The request body type
     * @param <B> The event body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented by the eventType argument
     */
    <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType);

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @since 3.1.0
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param errorType The type that the response body should be coerced into if the server responds with an error
     * @param <I>       The request body type
     * @param <B>       The event body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented by the eventType argument
     */
    <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType);

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>     The request body type
     * @param <B> The event body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented by the eventType argument
     */
    default <I, B> Publisher<Event<B>> eventStream(HttpRequest<I> request, Class<B> eventType) {
        return eventStream(request, Argument.of(eventType));
    }

    /**
     * <p>Perform an HTTP GET request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param uri The request URI
     * @param eventType The event data type
     * @param <B> The event body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented by the eventType argument
     */
    default <B> Publisher<Event<B>> eventStream(String uri, Class<B> eventType) {
        return eventStream(HttpRequest.GET(uri), Argument.of(eventType));
    }

    /**
     * <p>Perform an HTTP GET request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param uri The request URI
     * @param eventType The event data type
     * @param <B> The event body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented by the eventType argument
     */
    default <B> Publisher<Event<B>> eventStream(String uri, Argument<B> eventType) {
        return eventStream(HttpRequest.GET(uri), eventType);
    }

    /**
     * <p>Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a single body, and
     * receive each event wrapped in an {@link HttpResponse} that exposes the response status and headers.</p>
     *
     * <p>This suits protocols where the server decides per request whether to answer with a single body or with
     * {@code text/event-stream}, such as the
     * <a href="https://modelcontextprotocol.io/specification/2025-11-25/basic/transports#streamable-http">MCP Streamable HTTP transport</a>:</p>
     *
     * <ul>
     *     <li>The {@code Accept} header of the request is kept when it accepts {@code text/event-stream}, for example
     *     {@code application/json, text/event-stream}. Otherwise {@code text/event-stream} is added to it.</li>
     *     <li>When the content type of the response is {@code text/event-stream}, the body is decoded as events, and each
     *     event is emitted as soon as it arrives. The data of each event is decoded as JSON into the event type.</li>
     *     <li>Otherwise the whole body is decoded into the event type, using the content type of the response, and
     *     emitted as a single event.</li>
     *     <li>When the body holds no event, for example a {@code 202 Accepted} response, a single response without a body
     *     is emitted, so the status and headers are always available.</li>
     *     <li>When the response has an error status, the publisher fails with an
     *     {@link io.micronaut.http.client.exceptions.HttpClientResponseException} carrying the response and its body,
     *     decoded into the error type.</li>
     * </ul>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param errorType The type that the response body should be coerced into if the server responds with an error
     * @param <I>       The request body type
     * @param <B>       The event body type
     * @return A {@link Publisher} that emits the events of the response, each wrapped in an {@link HttpResponse}
     * @since 5.3.0
     */
    default <I, B> Publisher<HttpResponse<Event<B>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType, Argument<?> errorType) {
        throw new UnsupportedOperationException("exchangeEventStream is not supported by " + getClass().getName());
    }

    /**
     * <p>Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a single body, and
     * receive each event wrapped in an {@link HttpResponse} that exposes the response status and headers.</p>
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event body type
     * @return A {@link Publisher} that emits the events of the response, each wrapped in an {@link HttpResponse}
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     * @since 5.3.0
     */
    default <I, B> Publisher<HttpResponse<Event<B>>> exchangeEventStream(HttpRequest<I> request, Argument<B> eventType) {
        return exchangeEventStream(request, eventType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * <p>Perform an HTTP request whose response is either a stream of SSE {@link Event} objects or a single body, and
     * receive each event wrapped in an {@link HttpResponse} that exposes the response status and headers.</p>
     *
     * @param request   The {@link HttpRequest} to execute
     * @param eventType The event data type
     * @param <I>       The request body type
     * @param <B>       The event body type
     * @return A {@link Publisher} that emits the events of the response, each wrapped in an {@link HttpResponse}
     * @see #exchangeEventStream(HttpRequest, Argument, Argument)
     * @since 5.3.0
     */
    default <I, B> Publisher<HttpResponse<Event<B>>> exchangeEventStream(HttpRequest<I> request, Class<B> eventType) {
        return exchangeEventStream(request, Argument.of(eventType));
    }

    /**
     * Create a new {@link SseClient}.
     * Note that this method should only be used outside the context of a Micronaut application.
     * The returned {@link SseClient} is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static SseClient create(@Nullable URL url) {
        return SseClientFactoryResolver.getFactory().createSseClient(url);
    }

    /**
     * Create a new {@link SseClient} with the specified configuration. Note that this method should only be used
     * outside the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static SseClient create(@Nullable URL url, HttpClientConfiguration configuration) {
        return SseClientFactoryResolver.getFactory().createSseClient(url, configuration);
    }
}
