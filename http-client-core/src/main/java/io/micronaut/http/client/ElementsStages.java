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
package io.micronaut.http.client;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.body.BodyElements;

import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Maps the stages of the streaming exchanges of the async clients without losing the elements:
 * when the mapping fails, or the mapped stage was cancelled before the result arrived, the
 * elements are closed, so that the connection is not reserved until the read timeout. Cancelling
 * the mapped stage cancels the stage it maps.
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class ElementsStages {

    private ElementsStages() {
    }

    /**
     * Map the response of a streaming exchange.
     *
     * @param stage  The stage of the response, whose body is the elements
     * @param mapper The mapping, which takes the elements over when it succeeds
     * @param <T>    The type of an element
     * @param <R>    The mapped type
     * @return The mapped stage
     */
    public static <T, R> CompletionStage<R> mapResponse(CompletionStage<HttpResponse<BodyElements<T>>> stage,
                                                        Function<? super HttpResponse<BodyElements<T>>, ? extends R> mapper) {
        return map(stage, mapper, ElementsStages::closeElements);
    }

    /**
     * Map the elements of a streaming exchange.
     *
     * @param stage  The stage of the elements
     * @param mapper The mapping, which takes the elements over when it succeeds
     * @param <T>    The type of an element
     * @param <R>    The mapped type
     * @return The mapped stage
     */
    public static <T, R> CompletionStage<R> mapElements(CompletionStage<BodyElements<T>> stage,
                                                        Function<? super BodyElements<T>, ? extends R> mapper) {
        return map(stage, mapper, BodyElements::close);
    }

    /**
     * The future of the response of a streaming exchange that runs as a flow: cancelling it
     * before the response arrived cancels the exchange, and a response that arrives anyway is
     * closed.
     *
     * @param flow The flow of the response
     * @param <T>  The type of an element
     * @return The future of the response, whose body is the elements
     */
    public static <T> CompletableFuture<HttpResponse<BodyElements<T>>> response(ExecutionFlow<HttpResponse<BodyElements<T>>> flow) {
        return toFuture(flow, ElementsStages::closeElements);
    }

    /**
     * The future of the elements of a streaming exchange that runs as a flow, like
     * {@link #response(ExecutionFlow)}.
     *
     * @param flow The flow of the response
     * @param <T>  The type of an element
     * @return The future of the elements of the response
     */
    public static <T> CompletableFuture<BodyElements<T>> elements(ExecutionFlow<HttpResponse<BodyElements<T>>> flow) {
        return toFuture(flow.map(response -> Objects.requireNonNull(response.body(), "The response has no elements")), BodyElements::close);
    }

    /**
     * The future of the result of an exchange whose body was read whole: cancelling it before
     * the result arrived cancels the exchange.
     *
     * @param flow The flow of the result
     * @param <R>  The type of the result
     * @return The future of the result
     */
    public static <R> CompletableFuture<R> result(ExecutionFlow<R> flow) {
        return toFuture(flow, result -> { });
    }

    /**
     * Close the elements of the response of a streaming exchange, if it has them.
     *
     * @param response The response
     */
    public static void closeElements(HttpResponse<?> response) {
        if (response.getBody().orElse(null) instanceof BodyElements<?> elements) {
            elements.close();
        }
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

    private static <S, R> CompletionStage<R> map(CompletionStage<S> stage,
                                                 Function<? super S, ? extends R> mapper,
                                                 Consumer<S> discard) {
        CompletableFuture<R> mapped = new CompletableFuture<>();
        mapped.whenComplete((result, error) -> {
            if (error instanceof CancellationException) {
                // cancels the exchange, if the result has not arrived yet
                try {
                    stage.toCompletableFuture().cancel(false);
                } catch (UnsupportedOperationException ignored) {
                    // a stage that cannot be cancelled: its result is closed when it arrives
                }
            }
        });
        stage.whenComplete((value, error) -> {
            if (error != null) {
                // as thenApply completes a dependent stage
                mapped.completeExceptionally(error instanceof CompletionException ? error : new CompletionException(error));
                return;
            }
            R result;
            try {
                result = mapper.apply(value);
            } catch (Throwable e) {
                discard.accept(value);
                mapped.completeExceptionally(new CompletionException(e));
                return;
            }
            if (!mapped.complete(result)) {
                // cancelled before the result arrived: nobody reads the elements
                discard.accept(value);
            }
        });
        return mapped;
    }
}
