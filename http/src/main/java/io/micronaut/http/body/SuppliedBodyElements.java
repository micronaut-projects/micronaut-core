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
package io.micronaut.http.body;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The {@link BodyElements} of {@link BodyElements#of}: the elements a function produces, which
 * enforce the rules of {@link BodyElements}. Each {@link #next()} produces one element, so nothing
 * is queued.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class SuppliedBodyElements<T> implements BodyElements<T> {

    private final Supplier<? extends CompletionStage<Optional<T>>> next;
    private final @Nullable Runnable close;
    /**
     * The operation in progress, or {@code null}. Guarded by this.
     */
    private @Nullable CompletableFuture<?> operation;
    /**
     * Completes when the elements were closed, or {@code null} while they are open. Guarded by
     * this.
     */
    private @Nullable CompletableFuture<@Nullable Void> closed;

    SuppliedBodyElements(Supplier<? extends CompletionStage<Optional<T>>> next, @Nullable Runnable close) {
        this.next = next;
        this.close = close;
    }

    @Override
    public CompletionStage<Optional<T>> next() {
        CompletableFuture<Optional<T>> result = new CompletableFuture<>();
        start(result);
        CompletionStage<Optional<T>> element;
        try {
            element = Objects.requireNonNull(next.get(), "The elements returned no stage");
        } catch (Throwable e) {
            end(result);
            result.completeExceptionally(e);
            return result;
        }
        element.whenComplete((value, error) -> {
            // the operation ends before its stage completes: a continuation may read the next one
            end(result);
            if (error != null) {
                result.completeExceptionally(error instanceof CompletionException && error.getCause() != null ? error.getCause() : error);
            } else {
                // no optional is the end too
                result.complete(Objects.requireNonNullElse(value, Optional.empty()));
            }
        });
        return result;
    }

    @Override
    public CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        start(result);
        result.whenComplete((ignored, error) -> end(result));
        // the loop reads the function directly: this operation spans the reads
        BodyElementsLoop.run(next, consumer, result);
        return result;
    }

    @Override
    public CompletionStage<Void> closeAsync() {
        CompletableFuture<?> pending;
        CompletableFuture<@Nullable Void> stage;
        synchronized (this) {
            if (closed != null) {
                return closed;
            }
            stage = new CompletableFuture<>();
            closed = stage;
            pending = operation;
            operation = null;
        }
        if (pending != null) {
            pending.completeExceptionally(new CancellationException("The elements of the body were closed"));
        }
        try {
            Runnable callback = close;
            if (callback != null) {
                callback.run();
            }
            stage.complete(null);
        } catch (Throwable e) {
            stage.completeExceptionally(e);
        }
        return stage;
    }

    @Override
    public void close() {
        CompletableFuture<@Nullable Void> stage = closeAsync().toCompletableFuture();
        if (stage.isCompletedExceptionally()) {
            // the failure of the callback, to whoever closes the elements
            try {
                stage.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                if (cause instanceof RuntimeException runtime) {
                    throw runtime;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw e;
            }
        }
    }

    private synchronized void start(CompletableFuture<?> result) {
        if (closed != null) {
            throw new IllegalStateException("The elements of the body were closed");
        }
        if (operation != null) {
            throw new IllegalStateException("Another operation on the elements of the body is in progress");
        }
        operation = result;
    }

    private synchronized void end(CompletableFuture<?> result) {
        if (operation == result) {
            operation = null;
        }
    }

    @Override
    public String toString() {
        return next.toString();
    }
}
