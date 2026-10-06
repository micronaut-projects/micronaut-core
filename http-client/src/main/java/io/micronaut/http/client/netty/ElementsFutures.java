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
package io.micronaut.http.client.netty;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * The futures of the streaming exchanges of the async clients: cancelling one before the result
 * arrived cancels the exchange, and a result that arrives anyway is closed.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class ElementsFutures {

    private ElementsFutures() {
    }

    /**
     * @param flow The flow of the response
     * @param <T>  The type of an element
     * @return The future of the response, whose body is the elements
     */
    static <T> CompletableFuture<HttpResponse<BodyElements<T>>> response(ExecutionFlow<HttpResponse<BodyElements<T>>> flow) {
        return toFuture(flow, response -> {
            BodyElements<T> elements = response.body();
            if (elements != null) {
                elements.close();
            }
        });
    }

    /**
     * @param flow The flow of the response
     * @param <T>  The type of an element
     * @return The future of the elements of the response
     */
    static <T> CompletableFuture<BodyElements<T>> elements(ExecutionFlow<HttpResponse<BodyElements<T>>> flow) {
        return toFuture(flow.map(response -> Objects.requireNonNull(response.body(), "The response has no elements")), BodyElements::close);
    }

    private static <R> CompletableFuture<R> toFuture(ExecutionFlow<R> flow, Consumer<R> discard) {
        CompletableFuture<R> future = new CompletableFuture<>();
        future.whenComplete((result, error) -> {
            if (error instanceof CancellationException) {
                flow.cancel();
            }
        });
        flow.onComplete((result, error) -> {
            if (error != null) {
                future.completeExceptionally(error);
            } else if (!future.complete(result)) {
                // cancelled before the result arrived: nobody reads the elements
                discard.accept(result);
            }
        });
        return future;
    }
}
