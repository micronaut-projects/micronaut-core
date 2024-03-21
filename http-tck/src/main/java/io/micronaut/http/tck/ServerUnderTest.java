/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.http.tck;

import io.micronaut.context.ApplicationContextProvider;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;

import java.io.Closeable;
import java.net.URL;
import java.util.Optional;

/**
 * An API for a Micronaut HTTP Server under test. An implementation can be Netty or AWS Lambda Handler.
 * @author Sergio del Amo
 * @since 1.8.0
 */
@Experimental
public interface ServerUnderTest extends ApplicationContextProvider, Closeable, AutoCloseable {

    /**
     * The property name used to signify we want to use a non-blocking client.
     *
     * This is used as the implementation varies for the javanet client.
     */
    String BLOCKING_CLIENT_PROPERTY = "use.blocking.client";

    /*
     * Perform an HTTP request for the given request against the server under test and returns the full HTTP response
     * @param request  The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> HttpResponse<O> exchange(HttpRequest<I> request) {
        return exchange(request, (Argument<O>) null);
    }

    /*
     * Perform an HTTP request for the given request against the server under test and returns the full HTTP response
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return exchange(request, Argument.of(bodyType));
    }

    /*
     * Perform an HTTP request for the given request against the server under test and returns the full HTTP response
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param errorType The error type
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @param <E>     The error body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    default <I, O, E> HttpResponse<O> exchange(HttpRequest<I> request, Class<O> bodyType, Class<E> errorType) {
        return exchange(request, Argument.of(bodyType), Argument.of(errorType));
    }

    /*
     * Perform an HTTP request for the given request against the server under test and returns the full HTTP response
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    <I, O> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType);

    /*
     * Perform an HTTP request for the given request against the server under test and returns the full HTTP response
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>     The request body type
     * @param <O>     The response body type
     * @return The full {@link HttpResponse} object
     * @throws HttpClientResponseException when an error status is returned
     */
    <I, O, E> HttpResponse<O> exchange(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType);

    default Optional<String> getScheme() {
        return Optional.of("http");
    }

    @NonNull
    default Optional<Integer> getPort() {
        return Optional.empty();
    }

    @NonNull
    default Optional<URL> getURL() {
        return Optional.empty();
    }
}
