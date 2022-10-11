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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import org.reactivestreams.Publisher;

import java.net.URL;
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
    <I> Publisher<ByteBuffer<?>> dataStream(@NonNull HttpRequest<I> request);


    /**
     * Request a stream of data where each emitted item is a {@link ByteBuffer} instance.
     *
     * @since 3.1.0
     * @param request   The request
     * @param errorType The type that the response body should be coerced into if the server responds with an error
     * @param <I>       The request body type
     * @return A {@link Publisher} that emits a stream of {@link ByteBuffer} instances
     */
    <I> Publisher<ByteBuffer<?>> dataStream(@NonNull HttpRequest<I> request, @NonNull Argument<?> errorType);

    /**
     * Requests a stream data where each emitted item is a {@link ByteBuffer} wrapped in the {@link HttpResponse} object
     * (which remains the same for each emitted item).
     *
     * @param request The {@link HttpRequest}
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits a stream of {@link ByteBuffer} instances wrapped by a {@link HttpResponse}
     */
    <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull HttpRequest<I> request);

    /**
     * Requests a stream data where each emitted item is a {@link ByteBuffer} wrapped in the {@link HttpResponse} object
     * (which remains the same for each emitted item).
     *
     * @since 3.1.0
     * @param request   The {@link HttpRequest}
     * @param errorType The type that the response body should be coerced into if the server responds with an error
     * @param <I>       The request body type
     * @return A {@link Publisher} that emits a stream of {@link ByteBuffer} instances wrapped by a {@link HttpResponse}
     */
    <I> Publisher<HttpResponse<ByteBuffer<?>>> exchangeStream(@NonNull HttpRequest<I> request, @NonNull Argument<?> errorType);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param <I>     The request body type
     * @param request The {@link HttpRequest} to execute
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I> Publisher<Map<String, Object>> jsonStream(@NonNull HttpRequest<I> request);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription. Incoming data
     * is buffered.</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of object to convert the JSON into
     * @param <I>     The request body type
     * @param <O>     The response type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O> Publisher<O> jsonStream(@NonNull HttpRequest<I> request, @NonNull Argument<O> type);


    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription. Incoming data
     * is buffered.</p>
     *
     * @since 3.1.0
     * @param request   The {@link HttpRequest} to execute
     * @param type      The type of object to convert the JSON into
     * @param errorType The type that the response body should be coerced into if the server responds with an error
     * @param <I>       The request body type
     * @param <O>       The response type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O> Publisher<O> jsonStream(@NonNull HttpRequest<I> request, @NonNull Argument<O> type, @NonNull Argument<?> errorType);

    /**
     * <p>Perform an HTTP request and receive data as a stream of JSON objects as they become available without blocking.</p>
     *
     * <p>The downstream {@link org.reactivestreams.Subscriber} can regulate demand via the subscription</p>
     *
     * @param request The {@link HttpRequest} to execute
     * @param type    The type of object to convert the JSON into
     * @param <I>     The request body type
     * @param <O>     The response type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<O> jsonStream(@NonNull HttpRequest<I> request, @NonNull Class<O> type) {
        return jsonStream(request, Argument.of(type));
    }

    /**
     * Create a new {@link StreamingHttpClient}.
     * Note that this method should only be used outside of the context of a Micronaut application.
     * The returned {@link StreamingHttpClient} is not subject to dependency injection.
     * The creator is responsible for closing the client to avoid leaking connections.
     * Within a Micronaut application use {@link jakarta.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static StreamingHttpClient create(@Nullable URL url) {
        return StreamingHttpClientFactoryResolver.getFactory().createStreamingClient(url);
    }

    /**
     * Create a new {@link StreamingHttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link jakarta.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static StreamingHttpClient create(@Nullable URL url, @NonNull HttpClientConfiguration configuration) {
        return StreamingHttpClientFactoryResolver.getFactory().createStreamingClient(url, configuration);
    }
}
