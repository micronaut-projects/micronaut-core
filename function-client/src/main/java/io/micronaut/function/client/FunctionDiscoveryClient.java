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
import io.micronaut.function.client.exceptions.FunctionNotFoundException;
import org.reactivestreams.Publisher;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * An interface for discovery functions, either remote or local.
 *
 * @author graemerocher
 * @since 1.0
 */
public interface FunctionDiscoveryClient {

    /**
     * Finds a function for the given function name.
     *
     * @param functionName The function name
     * @return A {@link Publisher} that emits the {@link java.net.URI} of the function or a {@link io.micronaut.function.client.exceptions.FunctionNotFoundException} if no function is found
     */
    Publisher<FunctionDefinition> getFunction(String functionName);

    /**
     * The {@link CompletionStage} counterpart of {@link #getFunction(String)}. By default, it
     * adapts the first {@link FunctionDefinition} emitted by {@link #getFunction(String)}, and
     * fails with a {@link FunctionNotFoundException} when the publisher completes without one.
     * Cancelling the stage cancels the subscription.
     *
     * <p>An implementation returns a new stage for each call, which a caller may cancel. The
     * framework never cancels a stage it did not create: it ignores its result instead.</p>
     *
     * @param functionName The function name
     * @return A {@link CompletionStage} completed with the {@link FunctionDefinition}, or with a {@link FunctionNotFoundException} if no function is found
     * @since 5.3.0
     */
    @Experimental
    default CompletionStage<FunctionDefinition> getFunctionAsync(String functionName) {
        CompletableFuture<FunctionDefinition> first = CompletionStagePublishers.first(getFunction(functionName), null);
        return CompletionStagePublishers.map(first, definition -> {
            if (definition == null) {
                throw new FunctionNotFoundException(functionName);
            }
            return definition;
        });
    }
}
