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
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Maps a client operation while keeping ownership of cancellation on the returned future.
 */
@Internal
final class AsyncClientFuture {
    private AsyncClientFuture() {
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    static <T, R extends @Nullable Object> CompletionStage<R> map(CompletionStage<T> source, Function<? super T, ? extends R> mapper) {
        CompletableFuture<R> result = new CompletableFuture<>();
        result.whenComplete((value, error) -> {
            if (error != null) {
                source.toCompletableFuture().cancel(false);
            }
        });
        source.whenComplete((value, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
            } else if (!result.isDone()) {
                try {
                    result.complete(mapper.apply(value));
                } catch (Exception | Error e) {
                    result.completeExceptionally(e);
                }
            }
        });
        return result;
    }
}
