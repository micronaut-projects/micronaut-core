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
package io.micronaut.core.flow;

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The completable future flow implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class CompletableFutureFlowImpl implements CompletableFutureFlow<Object> {

    private CompletableFuture<Object> stage;

    public CompletableFutureFlowImpl(CompletableFuture<Object> stage) {
        this.stage = stage;
    }

    @Override
    public <R> Flow<R> flatMap(Function<? super Object, ? extends Flow<? extends R>> transformer) {
        stage = stage.thenCompose(value -> {
            if (value != null) {
                return (CompletionStage<Object>) transformer.apply(value).toCompletableFuture();
            }
            return CompletableFuture.completedFuture(null);
        });
        return (Flow<R>) this;
    }

    @Override
    public <R> Flow<R> then(Supplier<? extends Flow<? extends R>> supplier) {
        stage = stage.thenCompose(value -> (CompletionStage<Object>) supplier.get().toCompletableFuture());
        return (Flow<R>) this;
    }

    @Override
    public <R> Flow<R> map(Function<? super Object, ? extends R> function) {
        stage = stage.thenApply(function::apply);
        return (Flow<R>) this;
    }

    @Override
    public Flow<Object> onErrorResume(Function<? super Throwable, ? extends Flow<?>> fallback) {
        stage = stage.exceptionallyCompose(throwable -> (CompletionStage<Object>) fallback.apply(throwable).toCompletableFuture());
        return this;
    }

    @Override
    public Flow<Object> putInContext(String key, Object value) {
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super Object, Throwable> fn) {
        stage.handle((o, throwable) -> {
            fn.accept(o, throwable);
            return null;
        });
    }

    @Override
    public CompletableFuture<Object> toCompletableFuture() {
        return stage;
    }

}
