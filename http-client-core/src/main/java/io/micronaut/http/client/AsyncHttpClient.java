/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.context.LifeCycle;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.util.Optional;
import java.util.concurrent.CompletionStage;

/**
 * An HTTP client API that exposes asynchronous operations backed by the Java {@link java.util.concurrent.Future}
 * contract instead of Reactive Streams.
 *
 * @author Denis Stepanov
 * @since 5.0
 */
public interface AsyncHttpClient extends Closeable, LifeCycle<AsyncHttpClient> {

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response and converting the response
     * body to the specified type.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param bodyType  The body type
     * @param errorType The error type
     * @param <I>       The request body type
     * @param <O>       The response body type
     * @param <E>       The error type
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    <I, O, E> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request, @Nullable Argument<O> bodyType, Argument<E> errorType);

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response and converting the response
     * body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    default <I, O> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request, @Nullable Argument<O> bodyType) {
        return exchange(request, bodyType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    default <I> CompletionStage<HttpResponse<ByteBuffer<?>>> exchange(HttpRequest<I> request) {
        @SuppressWarnings("unchecked")
        Argument<ByteBuffer<?>> bodyType = (Argument<ByteBuffer<?>>) (Argument<?>) Argument.of(ByteBuffer.class);
        return exchange(request, bodyType);
    }

    /**
     * Perform an HTTP GET request for the given URI emitting the full HTTP response.
     *
     * @param uri The URI
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    default CompletionStage<HttpResponse<ByteBuffer<?>>> exchange(String uri) {
        return exchange(HttpRequest.GET(uri));
    }

    /**
     * Perform an HTTP GET request for the given URI emitting the full HTTP response and converting the response body
     * to the specified type.
     *
     * @param uri      The URI
     * @param bodyType The body type
     * @param <O>      The response body type
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    default <O> CompletionStage<HttpResponse<O>> exchange(String uri, Class<O> bodyType) {
        return exchange(HttpRequest.GET(uri), Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request for the given request object emitting the full HTTP response and converting the response
     * body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link CompletionStage} that completes with the full {@link HttpResponse}
     */
    default <I, O> CompletionStage<HttpResponse<O>> exchange(HttpRequest<I> request, Class<O> bodyType) {
        return exchange(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request and convert the response body to the specified type.
     *
     * @param request   The {@link HttpRequest} to execute
     * @param bodyType  The body type
     * @param errorType The error type
     * @param <I>       The request body type
     * @param <O>       The response body type
     * @param <E>       The error type
     * @return A {@link CompletionStage} that completes with the converted response body
     */
    default <I, O, E> CompletionStage<@Nullable O> retrieve(HttpRequest<I> request, Argument<O> bodyType, Argument<E> errorType) {
        return exchange(request, bodyType, errorType).thenApply(response -> extractBody(response, bodyType));
    }

    /**
     * Perform an HTTP request and convert the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link CompletionStage} that completes with the converted response body
     */
    default <I, O> CompletionStage<@Nullable O> retrieve(HttpRequest<I> request, Argument<O> bodyType) {
        return retrieve(request, bodyType, HttpClient.DEFAULT_ERROR_TYPE);
    }

    /**
     * Perform an HTTP request and convert the response body to the specified type.
     *
     * @param request  The {@link HttpRequest} to execute
     * @param bodyType The body type
     * @param <I>      The request body type
     * @param <O>      The response body type
     * @return A {@link CompletionStage} that completes with the converted response body
     */
    default <I, O> CompletionStage<@Nullable O> retrieve(HttpRequest<I> request, Class<O> bodyType) {
        return retrieve(request, Argument.of(bodyType));
    }

    /**
     * Perform an HTTP request and convert the response body to a {@link String}.
     *
     * @param request The {@link HttpRequest} to execute
     * @param <I>     The request body type
     * @return A {@link CompletionStage} that completes with the converted response body
     */
    default <I> CompletionStage<@Nullable String> retrieve(HttpRequest<I> request) {
        return retrieve(request, String.class);
    }

    /**
     * Perform an HTTP GET request for the given URI and convert the response body to a {@link String}.
     *
     * @param uri The URI
     * @return A {@link CompletionStage} that completes with the converted response body
     */
    default CompletionStage<@Nullable String> retrieve(String uri) {
        return retrieve(HttpRequest.GET(uri));
    }

    private static <O> @Nullable O extractBody(HttpResponse<O> response, @Nullable Argument<O> bodyType) {
        if (bodyType == null) {
            return response.getBody().orElse(null);
        }
        Class<?> type = bodyType.getType();
        if (type == void.class || type == Void.class) {
            return null;
        }
        if (HttpStatus.class.isAssignableFrom(type)) {
            @SuppressWarnings("unchecked")
            O status = (O) response.getStatus();
            return status;
        }
        Optional<O> body = response.getBody();
        if (body.isEmpty() && response.getBody(Argument.of(byte[].class)).isPresent()) {
            throw new HttpClientResponseException(
                "Failed to decode the body for the given content type [%s]".formatted(response.getContentType().orElse(null)),
                response
            );
        }
        return body.orElseThrow(() -> new HttpClientResponseException("Empty body", response));
    }
}
