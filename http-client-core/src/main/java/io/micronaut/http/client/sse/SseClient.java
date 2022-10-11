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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
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
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented as a {@link ByteBuffer}
     */
    <I> Publisher<Event<ByteBuffer<?>>> eventStream(@NonNull HttpRequest<I> request);

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
    <I, B> Publisher<Event<B>> eventStream(@NonNull HttpRequest<I> request, @NonNull Argument<B> eventType);

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
    <I, B> Publisher<Event<B>> eventStream(@NonNull HttpRequest<I> request, @NonNull Argument<B> eventType, @NonNull Argument<?> errorType);

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
    default <I, B> Publisher<Event<B>> eventStream(@NonNull HttpRequest<I> request, @NonNull Class<B> eventType) {
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
    default <B> Publisher<Event<B>> eventStream(@NonNull String uri, @NonNull Class<B> eventType) {
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
    default <B> Publisher<Event<B>> eventStream(@NonNull String uri, @NonNull Argument<B> eventType) {
        return eventStream(HttpRequest.GET(uri), eventType);
    }

    /**
     * Create a new {@link SseClient}.
     * Note that this method should only be used outside of the context of a Micronaut application.
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
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static SseClient create(@Nullable URL url, @NonNull HttpClientConfiguration configuration) {
        return SseClientFactoryResolver.getFactory().createSseClient(url, configuration);
    }
}
