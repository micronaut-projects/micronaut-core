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
package io.micronaut.function.client.http;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The {@link HttpFunctionExecutor} bean, which invokes the functions asynchronously with the
 * {@link io.micronaut.http.client.AsyncHttpClient} of the HTTP client, without Reactive Streams.
 * It is final, so that a subclass of {@link HttpFunctionExecutor} that overrides
 * {@link #invoke(FunctionDefinition, Object, Argument)} is called through it.
 *
 * @param <I> input type
 * @param <O> output type
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
@Singleton
final class AsyncHttpFunctionExecutor<I, O> extends HttpFunctionExecutor<I, O> {

    private final HttpClient httpClient;

    /**
     * @param conversionService The conversion service
     * @param httpClient        The HTTP client
     */
    AsyncHttpFunctionExecutor(ConversionService conversionService, HttpClient httpClient) {
        super(conversionService, httpClient);
        this.httpClient = httpClient;
    }

    /**
     * Invoke the function with the {@link io.micronaut.http.client.AsyncHttpClient} of the HTTP
     * client. The request is the one {@link #invoke(FunctionDefinition, Object, Argument)} sends
     * for a {@link Publisher} of the value, which the default implementation asks for: a
     * {@code java.lang} value type does not ask for {@code text/plain}, as the type of the
     * result is a publisher.
     */
    @Override
    public <T> CompletionStage<@Nullable T> invokeAsync(FunctionDefinition definition, @Nullable I input, Argument<T> valueType) {
        MutableHttpRequest<?> request;
        try {
            request = toRequest(definition, input, Publisher.class);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (valueType.isVoid()) {
            return httpClient.toAsync().exchange(request).thenApply(response -> null);
        }
        return httpClient.toAsync().retrieve(request, valueType);
    }
}
