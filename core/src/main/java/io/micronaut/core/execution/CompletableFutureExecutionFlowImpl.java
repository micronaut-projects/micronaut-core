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
package io.micronaut.core.execution;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The completable future execution flow implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class CompletableFutureExecutionFlowImpl implements CompletableFutureExecutionFlow<Object> {

    private CompletionStage<Object> stage;

    CompletableFutureExecutionFlowImpl(CompletionStage<Object> stage) {
        this.stage = stage;
    }

    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super Object, ? extends ExecutionFlow<? extends R>> transformer) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            return completedFlow.flatMap(transformer);
        }
        stage = stage.thenCompose(value -> {
            if (value != null) {
                return (CompletionStage<Object>) transformer.apply(value).toCompletableFuture();
            }
            return CompletableFuture.completedFuture(null);
        });
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            return completedFlow.then(supplier);
        }
        stage = stage.thenCompose(value -> (CompletionStage<Object>) supplier.get().toCompletableFuture());
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super Object, ? extends R> function) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            return completedFlow.map(function);
        }
        stage = stage.thenApply(function);
        return (ExecutionFlow<R>) this;
    }

    @Override
    public ExecutionFlow<Object> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<?>> fallback) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            return completedFlow.onErrorResume(fallback);
        }
        stage = stage.exceptionallyCompose(throwable -> {
            if (throwable instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }
            return (CompletionStage<Object>) fallback.apply(throwable).toCompletableFuture();
        });
        return this;
    }

    @Override
    public ExecutionFlow<Object> putInContext(String key, Object value) {
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super Object, Throwable> fn) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            completedFlow.onComplete(fn);
            return;
        }
        stage.whenComplete((o, throwable) -> {
            if (throwable instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }
            fn.accept(o, throwable);
        });
    }

    @Override
    public void completeTo(CompletableFuture<Object> completableFuture) {
        ImperativeExecutionFlow<Object> completedFlow = tryComplete();
        if (completedFlow != null) {
            completedFlow.completeTo(completableFuture);
            return;
        }
        stage.whenComplete((o, throwable) -> {
            if (throwable instanceof CompletionException completionException) {
                throwable = completionException.getCause();
            }
            if (throwable != null) {
                completableFuture.completeExceptionally(throwable);
            } else {
                completableFuture.complete(o);
            }
        });
    }

    @Nullable
    @Override
    public ImperativeExecutionFlow<Object> tryComplete() {
        CompletableFuture<Object> completableFuture = stage.toCompletableFuture();
        if (completableFuture.isDone()) {
            try {
                return new ImperativeExecutionFlowImpl(completableFuture.getNow(null), null);
            } catch (Throwable throwable) {
                if (throwable instanceof CompletionException completionException) {
                    throwable = completionException.getCause();
                }
                return new ImperativeExecutionFlowImpl(null, throwable);
            }
        } else {
            return null;
        }
    }

    @Override
    public CompletableFuture<Object> toCompletableFuture() {
        return stage.toCompletableFuture();
    }

}
