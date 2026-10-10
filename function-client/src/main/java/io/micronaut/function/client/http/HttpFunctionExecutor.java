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
package io.micronaut.function.client.http;

import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.function.client.FunctionDefinition;
import io.micronaut.function.client.FunctionInvoker;
import io.micronaut.function.client.FunctionInvokerChooser;
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.HttpClient;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.io.Closeable;
import java.net.URI;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A {@link io.micronaut.function.executor.FunctionExecutor} that uses a {@link io.micronaut.http.client.HttpClient} to execute a remote function definition.
 *
 * <p>{@link #invokeAsync(FunctionDefinition, Object, Argument)} invokes the functions with the
 * {@link io.micronaut.http.client.AsyncHttpClient} of the HTTP client, without Reactive Streams.
 * A subclass is called through {@link #invoke(FunctionDefinition, Object, Argument)} instead, so
 * that its override keeps working.</p>
 *
 * @param <I> input type
 * @param <O> output type
 * @author graemerocher
 * @since 1.0
 */
@Singleton
public class HttpFunctionExecutor<I, O> implements FunctionInvoker<I, O>, Closeable, FunctionInvokerChooser {

    private final ConversionService conversionService;
    private final HttpClient httpClient;

    /**
     * Constructor.
     *
     * @param conversionService The conversion service
     * @param httpClient The HTTP client
     */
    public HttpFunctionExecutor(ConversionService conversionService, HttpClient httpClient) {
        super();
        this.conversionService = conversionService;
        this.httpClient = httpClient;
    }

    @Override
    @Nullable
    @SuppressWarnings("unchecked")
    public O invoke(FunctionDefinition definition, @Nullable I input, Argument<O> outputType) {
        Class<O> outputJavaType = outputType.getType();
        if (outputType.isAsync()) {
            // a CompletionStage or a CompletableFuture of the result, from invokeAsync, which
            // invokes this method for a publisher unless the bean overrides it
            return (O) invokeAsync(definition, input, outputType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT)).toCompletableFuture();
        }
        MutableHttpRequest<?> request = toRequest(definition, input, outputJavaType);
        if (Publishers.isConvertibleToPublisher(outputJavaType)) {
            Publisher<?> publisher = httpClient.retrieve(request, outputType.getFirstTypeVariable().orElse(Argument.OBJECT_ARGUMENT));
            return Publishers.convertPublisher(conversionService, publisher, outputJavaType);
        } else if (outputType.isVoid()) {
            // the body is not needed, and a Void body is not buffered
            httpClient.toBlocking().exchange(request, Argument.VOID);
            return null;
        } else {
            return httpClient.toBlocking().retrieve(request, outputType);
        }
    }

    /**
     * Invoke the function with the {@link io.micronaut.http.client.AsyncHttpClient} of the HTTP
     * client. The request is the one {@link #invoke(FunctionDefinition, Object, Argument)} sends
     * for a {@link Publisher} of the value, which the default implementation asks for: a
     * {@code java.lang} value type does not ask for {@code text/plain}, as the type of the
     * result is a publisher. A {@link Void} result completes with {@code null}. A subclass is
     * called through {@link #invoke(FunctionDefinition, Object, Argument)}.
     *
     * @param definition The definition
     * @param input      The input
     * @param valueType  The type of the result
     * @param <T>        The type of the result
     * @return A stage that completes with the result
     * @since 5.3.0
     */
    @Override
    public <T> CompletionStage<@Nullable T> invokeAsync(FunctionDefinition definition, @Nullable I input, Argument<T> valueType) {
        if (getClass() != HttpFunctionExecutor.class) {
            return FunctionInvoker.super.invokeAsync(definition, input, valueType);
        }
        MutableHttpRequest<?> request;
        try {
            request = toRequest(definition, input, Publisher.class);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (valueType.isVoid() || valueType.getType() == Void.class) {
            // the body is not needed, and a Void body is not buffered
            return httpClient.toAsync().exchange(request, Argument.VOID).thenApply(response -> null);
        }
        return httpClient.toAsync().retrieve(request, valueType);
    }

    /**
     * The request that invokes the function.
     *
     * @param definition     The definition
     * @param input          The input
     * @param outputJavaType The type of the result
     * @return The request
     */
    final MutableHttpRequest<?> toRequest(FunctionDefinition definition, @Nullable I input, Class<?> outputJavaType) {
        URI uri = definition.getURI().orElseThrow(() -> new FunctionNotFoundException(definition.getName()));
        MutableHttpRequest<?> request;
        if (input == null) {
            request = HttpRequest.GET(uri.toString());
        } else {
            request = HttpRequest.POST(uri.toString(), input);
        }
        if (input != null && ClassUtils.isJavaLangType(input.getClass())) {
            request.contentType(MediaType.TEXT_PLAIN_TYPE);
        }
        if (ClassUtils.isJavaLangType(outputJavaType)) {
            request.accept(MediaType.TEXT_PLAIN_TYPE);
        }
        return request;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <I1, O2> Optional<FunctionInvoker<I1, O2>> choose(FunctionDefinition definition) {
        if (definition.getURI().isPresent()) {
            return Optional.of((FunctionInvoker<I1, O2>) this);
        }

        return Optional.empty();
    }

    @Override
    @PreDestroy
    public void close() {
        httpClient.close();
    }
}
