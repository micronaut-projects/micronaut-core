/*
 * Copyright 2017-2020 original authors
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

import io.micronaut.core.annotation.Blocking;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;

import java.io.Closeable;

/**
 * A blocking HTTP client interface that features a subset of the operations provided by {@link HttpClient} and
 * is designed primarily for testing purposes.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Blocking
public interface BlockingHttpClient extends Closeable {

    /**
     * <p>Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.</p>
     * <p>
     * <p>This method will send a {@code Content-Length} header and except a content length header the response and
     * is designed for simple non-streaming exchanges of data</p>
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
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    <I, O, E> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType);


    /**
     * <p>Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.</p>
     * <p>
     * <p>This method will send a {@code Content-Length} header and except a content length header the response and
     * is designed for simple non-streaming exchanges of data</p>
     * <p>
     * <p>By default the exchange {@code Content-Type} is application/json, unless otherwise specified in the passed
     * {@link HttpRequest}</p>
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType) {
        return exchange(request, bodyType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher}.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> HttpResponse<O> exchange(HttpRequest<I> request) {
        return exchange(request, (Argument<O>) null);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return exchange(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A result of the given type or null the URI returns a 404
     * @throws HttpClientResponseException when an error status is returned
     */
    @SuppressWarnings("unchecked")
    default <I, O> O retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return retrieve(request, bodyType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param errorType The error type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @param <E>      The error type
     * @return A result of the given type or null the URI returns a 404
     * @throws HttpClientResponseException when an error status is returned
     */
    @SuppressWarnings("unchecked")
    default <I, O, E> O retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        HttpResponse<O> response = exchange(request, bodyType, errorType);
        if (HttpStatus.class.isAssignableFrom(bodyType.getType())) {
            return (O) response.getStatus();
        } else {
            return response
                    .getBody()
                    .orElseThrow(() -> new HttpClientResponseException(
                            "Empty body",
                            response
                    ));
        }
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A result of the given type or null the URI returns a 404
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> O retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A string result or null if a 404 is returned
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I> String retrieve(HttpRequest<I> request) {
        return retrieve(request, String.class);
    }

    /**
     * Perform an HTTP GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param uri The URI
     * @return A string result or null if a 404 is returned
     * @throws HttpClientResponseException when an error status is returned
     */
    default String retrieve(String uri) {
        return retrieve(HttpRequest.GET(uri), String.class);
    }

    /**
     * Perform an HTTP GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher} and converting the response body to the specified type.
     *
     * @param uri The URI
     * @param bodyType The body type
     * @param <O> The body generic type
     * @return A result or null if a 404 is returned
     * @throws HttpClientResponseException when an error status is returned
     */
    default <O> O retrieve(String uri, Class<O> bodyType) {
        return retrieve(HttpRequest.GET(uri), bodyType);
    }

    /**
     * Perform a GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher}.
     *
     * @param uri      The URI of the GET request
     * @param <O>      The response body type
     * @param <E>      The error type
     * @param bodyType The body type
     * @param errorType The error type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <O, E> O retrieve(String uri, Class<O> bodyType, Class<E> errorType) {
        return retrieve(HttpRequest.GET(uri), Argument.of(bodyType), Argument.of(errorType));
    }

    /**
     * Perform a GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher}.
     *
     * @param uri The URI of the GET request
     * @param <O> The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <O> HttpResponse<O> exchange(String uri) {
        return exchange(HttpRequest.GET(uri), (Argument<O>) null);
    }

    /**
     * Perform a GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher}.
     *
     * @param uri      The URI of the GET request
     * @param <O>      The response body type
     * @param bodyType The body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <O> HttpResponse<O> exchange(String uri, Class<O> bodyType) {
        return exchange(HttpRequest.GET(uri), Argument.of(bodyType));
    }

    /**
     * Perform a GET request for the given request object emitting the full HTTP response from returned
     * {@link org.reactivestreams.Publisher}.
     *
     * @param uri      The URI of the GET request
     * @param <O>      The response body type
     * @param <E>      The error type
     * @param bodyType The body type
     * @param errorType The error type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <O, E> HttpResponse<O> exchange(String uri, Class<O> bodyType, Class<E> errorType) {
        return exchange(HttpRequest.GET(uri), Argument.of(bodyType), Argument.of(errorType));
    }
}
