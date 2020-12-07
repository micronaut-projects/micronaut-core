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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.context.LifeCycle;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.net.URL;
import java.util.Optional;

/**
 * A non-blocking HTTP client interface designed around the Micronaut API and Reactive Streams.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpClient extends Closeable, LifeCycle<HttpClient> {

    /**
     * The default error type.
     */
    Argument<JsonError> DEFAULT_ERROR_TYPE = Argument.of(JsonError.class);

    /**
     * @return A blocking HTTP client suitable for testing and non-production scenarios.
     */
    BlockingHttpClient toBlocking();

    /**
     * <p>Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.</p>
     * <p>
     * <p>This method will send a {@code Content-Length} header and except a content length header the response and is
     * designed for simple non-streaming exchanges of data</p>
     * <p>
     * <p>By default the exchange {@code Content-Type} is application/json, unless otherwise specified in the passed
     * {@link HttpRequest}</p>
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param errorType The error type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @param <E>      The error type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    <I, O, E> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType);

    /**
     * <p>Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.</p>
     * <p>
     * <p>This method will send a {@code Content-Length} header and except a content length header the response and is
     * designed for simple non-streaming exchanges of data</p>
     * <p>
     * <p>By default the exchange {@code Content-Type} is application/json, unless otherwise specified in the passed
     * {@link HttpRequest}</p>
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I, O> Publisher<HttpResponse<O>> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return exchange(request, bodyType, DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher}.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <I> Publisher<HttpResponse<ByteBuffer>> exchange(HttpRequest<I> request) {
        return exchange(request, ByteBuffer.class);
    }

    /**
     * Perform an HTTP GET request for the given request object emitting the full HTTP response from returned
     * {@link Publisher}.
     *
     * @param uri The Uri
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default Publisher<HttpResponse<ByteBuffer>> exchange(String uri) {
        return exchange(HttpRequest.GET(uri), ByteBuffer.class);
    }

    /**
     * Perform an HTTP GET request for the given request object emitting the full HTTP response from returned
     * {@link Publisher}.
     *
     * @param uri      The request URI
     * @param bodyType The body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits the full {@link HttpResponse} object
     */
    default <O> Publisher<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return exchange(HttpRequest.GET(uri), Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
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
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param errorType The error type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @param <E>      The error type
     * @return A {@link Publisher} that emits a result of the given type
     */
    default <I, O, E> Publisher<O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return Publishers.map(exchange(request, bodyType, errorType), response -> {
            if (bodyType.getType() == HttpStatus.class) {
                return (O) response.getStatus();
            } else {
                Optional<O> body = response.getBody();
                if (!body.isPresent() && response.getBody(ByteBuffer.class).isPresent()) {
                    throw new HttpClientResponseException(
                            String.format("Failed to decode the body for the given content type [%s]", response.getContentType().orElse(null)),
                            response
                    );
                } else {
                    return body.orElseThrow(() -> new HttpClientResponseException(
                            "Empty body",
                            response
                    ));
                }
            }
        });
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits a result of the given type
     */
    default <I, O> Publisher<O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return retrieve(request, bodyType, DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link Publisher} that emits a result of the given type
     */
    default <I, O> Publisher<O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link Publisher} that emits String result
     */
    default <I> Publisher<String> retrieve(HttpRequest<I> request) {
        return retrieve(request, String.class);
    }

    /**
     * Perform an HTTP GET request for the given request object emitting the full HTTP response from returned
     * {@link Publisher} and converting the response body to the specified type.
     *
     * @param uri The URI
     * @return A {@link Publisher} that emits String result
     */
    default Publisher<String> retrieve(String uri) {
        return retrieve(HttpRequest.GET(uri), String.class);
    }

    @Override
    default HttpClient refresh() {
        stop();
        return start();
    }

    /**
     * Create a new {@link HttpClient}. Note that this method should only be used outside of the context of a
     * Micronaut application. Within Micronaut use {@link javax.inject.Inject} to inject a client instead.
     *
     * @param url The base URL
     * @return The client
     */
    static HttpClient create(@Nullable URL url) {
        return HttpClientConfiguration.createClient(url);
    }

    /**
     * Create a new {@link HttpClient} with the specified configuration. Note that this method should only be used
     * outside of the context of an application. Within Micronaut use {@link javax.inject.Inject} to inject a client instead
     *
     * @param url The base URL
     * @param configuration the client configuration
     * @return The client
     * @since 2.2.0
     */
    static HttpClient create(@Nullable URL url, HttpClientConfiguration configuration) {
        return HttpClientConfiguration.createClient(url, configuration);
    }
}
