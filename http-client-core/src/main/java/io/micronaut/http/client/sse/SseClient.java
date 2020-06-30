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

import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.sse.Event;
import org.reactivestreams.Publisher;

/**
 * A client for streaming Server Sent Event streams.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface SseClient {

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     * <p>
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits an {@link Event} with the data represented as a {@link ByteBuffer}
     */
    <I> Publisher<Event<ByteBuffer<?>>> eventStream(HttpRequest<I> request);

    /**
     * <p>Perform an HTTP request and receive data as a stream of SSE {@link Event} objects as they become available without blocking.</p>
     * <p>
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
     * <p>
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
     * <p>
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
     * <p>
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
}
