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
package io.micronaut.http.client.sse;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * {@link BodyElements} fed by a source that produces elements when asked: a read with nothing
 * queued {@link #demand() asks the source}, which answers with {@link #push}, {@link #end} or
 * {@link #fail}, at once or later, on any thread. Elements the source produced beyond the read
 * that asked for them are queued for the next reads.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
abstract class PulledBodyElements<T> implements BodyElements<T> {

    private static final String CLOSED = "The elements of the body were closed";

    // guarded by this
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    private @Nullable CompletableFuture<Optional<T>> pending;
    private boolean ended;
    private @Nullable Throwable failure;
    private boolean busy;
    private @Nullable CompletableFuture<@Nullable Void> walking;
    private boolean closed;

    /**
     * Ask the source for more elements. Called by a read that found nothing queued, outside the
     * lock. The source answers with {@link #push}, {@link #end} or {@link #fail}; a source that
     * produced nothing for the read must ask itself again while {@link #isWaiting()}.
     */
    protected abstract void demand();

    /**
     * Release the source: the elements were closed. Called once, outside the lock, also after
     * the source ended.
     */
    protected abstract void release();

    /**
     * Whether a read waits for an element.
     *
     * @return Whether a read waits for an element
     */
    protected final synchronized boolean isWaiting() {
        return pending != null;
    }

    /**
     * Hand an element to the waiting read, or queue it.
     *
     * @param element The element
     */
    protected final void push(T element) {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            if (closed || ended || failure != null) {
                return;
            }
            p = pending;
            pending = null;
            if (p == null) {
                queue.add(element);
                return;
            }
        }
        p.complete(Optional.of(element));
    }

    /**
     * The source has no more elements. The queued elements are still read.
     */
    protected final void end() {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            if (closed || ended || failure != null) {
                return;
            }
            ended = true;
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.complete(Optional.empty());
        }
    }

    /**
     * The source failed. The queued elements are still read, then the reads fail.
     *
     * @param error The failure
     */
    protected final void fail(Throwable error) {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            if (closed || ended || failure != null) {
                return;
            }
            failure = error;
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.completeExceptionally(error);
        }
    }

    @Override
    public final CompletionStage<Optional<T>> next() {
        start();
        CompletableFuture<Optional<T>> result = new CompletableFuture<>();
        read().whenComplete((element, error) -> {
            synchronized (this) {
                busy = false;
            }
            if (error != null) {
                result.completeExceptionally(unwrap(error));
            } else {
                result.complete(element);
            }
        });
        // a view: the caller cannot complete or cancel the read
        return result.minimalCompletionStage();
    }

    @Override
    public final CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        start();
        synchronized (this) {
            walking = result;
        }
        walk(consumer, result);
        // a view: the caller cannot complete or cancel the operation, which would leave it in progress
        return result.minimalCompletionStage();
    }

    @Override
    public final CompletionStage<Void> closeAsync() {
        CompletableFuture<Optional<T>> p;
        CompletableFuture<@Nullable Void> w;
        synchronized (this) {
            if (closed) {
                return CompletableFuture.completedStage(null);
            }
            closed = true;
            queue.clear();
            p = pending;
            pending = null;
            w = walking;
            walking = null;
        }
        CancellationException cancelled = new CancellationException(CLOSED);
        if (w != null) {
            w.completeExceptionally(cancelled);
        }
        if (p != null) {
            p.completeExceptionally(cancelled);
        }
        release();
        return CompletableFuture.completedStage(null);
    }

    @Override
    public final void close() {
        closeAsync();
    }

    /**
     * Start an operation: one at a time, and not after closing.
     */
    private synchronized void start() {
        if (closed) {
            throw new IllegalStateException(CLOSED);
        }
        if (busy) {
            throw new IllegalStateException("Another operation on the elements of the body is in progress");
        }
        busy = true;
    }

    /**
     * Read one element: from the queue, or ask the source.
     *
     * @return Completes with the element, or empty at the end of the elements
     */
    private CompletableFuture<Optional<T>> read() {
        CompletableFuture<Optional<T>> result = new CompletableFuture<>();
        T element;
        Throwable error = null;
        boolean end = false;
        synchronized (this) {
            element = queue.poll();
            if (element == null) {
                if (closed) {
                    error = new CancellationException(CLOSED);
                } else if (failure != null) {
                    error = failure;
                } else if (ended) {
                    end = true;
                } else {
                    pending = result;
                }
            }
        }
        if (element != null) {
            result.complete(Optional.of(element));
        } else if (error != null) {
            result.completeExceptionally(error);
        } else if (end) {
            result.complete(Optional.empty());
        } else {
            try {
                demand();
            } catch (Throwable e) {
                fail(e);
            }
        }
        return result;
    }

    /**
     * Consume the elements in order, one at a time: the next element is read when the stage of
     * the consumer completed. Elements that are available at once are consumed in a loop.
     */
    private void walk(Function<? super T, ? extends CompletionStage<?>> consumer, CompletableFuture<@Nullable Void> result) {
        while (!result.isDone()) {
            CompletableFuture<Optional<T>> next = read();
            if (!next.isDone()) {
                next.whenComplete((element, error) -> {
                    if (error != null) {
                        finish(result, error);
                    } else if (consume(consumer, result, element.orElse(null))) {
                        walk(consumer, result);
                    }
                });
                return;
            }
            Optional<T> element;
            try {
                element = next.join();
            } catch (Throwable e) {
                finish(result, e);
                return;
            }
            if (!consume(consumer, result, element.orElse(null))) {
                return;
            }
        }
    }

    /**
     * Consume an element.
     *
     * @param element The element, or {@code null} at the end of the elements
     * @return Whether the next element can be read at once: the consumer completed at once
     */
    private boolean consume(Function<? super T, ? extends CompletionStage<?>> consumer,
                            CompletableFuture<@Nullable Void> result,
                            @Nullable T element) {
        if (element == null) {
            finish(result, null);
            return false;
        }
        CompletableFuture<?> stage;
        try {
            stage = Objects.requireNonNull(consumer.apply(element), "The consumer returned no stage").toCompletableFuture();
        } catch (Throwable e) {
            finish(result, e);
            return false;
        }
        if (stage.isDone()) {
            if (stage.isCompletedExceptionally()) {
                stage.whenComplete((ignored, e) -> finish(result, e));
                return false;
            }
            return true;
        }
        stage.whenComplete((ignored, e) -> {
            if (e != null) {
                finish(result, e);
            } else {
                walk(consumer, result);
            }
        });
        return false;
    }

    private void finish(CompletableFuture<@Nullable Void> result, @Nullable Throwable error) {
        synchronized (this) {
            busy = false;
            if (walking == result) {
                walking = null;
            }
        }
        if (error != null) {
            result.completeExceptionally(unwrap(error));
        } else {
            result.complete(null);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
