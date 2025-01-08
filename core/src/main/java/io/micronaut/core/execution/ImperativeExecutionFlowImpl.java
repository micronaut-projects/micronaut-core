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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The imperative execution flow implementation.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
final class ImperativeExecutionFlowImpl implements ImperativeExecutionFlow<Object> {

    @Nullable
    private Object value;
    @Nullable
    private Throwable error;
    @Nullable
    private Map<String, Object> context;

    <T> ImperativeExecutionFlowImpl(T value, Throwable error) {
        this.value = value;
        this.error = error;
    }

    @Nullable
    @Override
    public Object getValue() {
        return value;
    }

    @Nullable
    @Override
    public Throwable getError() {
        return error;
    }

    @Override
    public Map<String, Object> getContext() {
        return context == null ? Collections.emptyMap() : context;
    }

    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super Object, ? extends ExecutionFlow<? extends R>> transformer) {
        if (error == null) {
            try {
                if (value != null) {
                    return (ExecutionFlow<R>) transformer.apply(value);
                }
            } catch (Throwable e) {
                error = e;
                value = null;
            }
        }
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        if (error == null) {
            try {
                return (ExecutionFlow<R>) supplier.get();
            } catch (Throwable e) {
                error = e;
                value = null;
            }
        }
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super Object, ? extends R> transformer) {
        if (error == null) {
            try {
                value = transformer.apply(value);
            } catch (Throwable e) {
                error = e;
                value = null;
            }
        }
        return (ExecutionFlow<R>) this;
    }

    @Override
    public ExecutionFlow<Object> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<? extends Object>> fallback) {
        if (error != null) {
            try {
                return (ExecutionFlow<Object>) fallback.apply(error);
            } catch (Throwable e) {
                error = e;
                value = null;
            }
        }
        return this;
    }

    @Override
    public ExecutionFlow<Object> putInContext(String key, Object value) {
        if (context == null) {
            context = new LinkedHashMap<>();
        }
        context.put(key, value);
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super Object, Throwable> fn) {
        fn.accept(value, error);
    }

    @Override
    public void completeTo(CompletableFuture<Object> completableFuture) {
        if (error != null) {
            completableFuture.completeExceptionally(error);
        } else {
            completableFuture.complete(value);
        }
    }

    @Override
    public CompletableFuture<Object> toCompletableFuture() {
        if (error != null) {
            return CompletableFuture.failedFuture(error);
        }
        return CompletableFuture.completedFuture(value);
    }

}
