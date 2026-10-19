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
package io.micronaut.function.client;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.async.publisher.CompletionStagePublishers;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @param <I> input type
 * @param <O> output type
 * @author graemerocher
 * @since 1.0
 */
public interface FunctionInvoker<I, O> {

    /**
     * Invoke the given function definition for the given input and expected response type.
     *
     * @param definition The definition
     * @param input The input
     * @param outputType The response type
     * @return The result
     */
    @Nullable
    O invoke(FunctionDefinition definition, @Nullable I input, Argument<O> outputType);

    /**
     * Invoke the given function definition for the given input, completing with the result of
     * the expected type. The default implementation asks {@link #invoke} for a
     * {@link Publisher} of the result, and completes with its first item, or {@code null} if it
     * completes empty.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. Do not
     * call {@link #invoke} with a {@link CompletionStage} type from an override of this method:
     * {@link io.micronaut.function.client.http.HttpFunctionExecutor} answers such a call with
     * this method.</p>
     *
     * @param definition The definition
     * @param input      The input
     * @param valueType  The type of the result
     * @param <T>        The type of the result
     * @return A stage that completes with the result
     * @since 5.3.0
     */
    @Experimental
    @SuppressWarnings({"unchecked", "rawtypes"})
    default <T> CompletionStage<@Nullable T> invokeAsync(FunctionDefinition definition, @Nullable I input, Argument<T> valueType) {
        Publisher<T> result;
        try {
            result = (Publisher<T>) invoke(definition, input, (Argument) Argument.of(Publisher.class, valueType));
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        if (result == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("The function invoker returned no publisher"));
        }
        return CompletionStagePublishers.first(result, null);
    }
}
