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
package io.micronaut.http.body.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import io.micronaut.http.body.BodyElementsLoop;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * {@link BodyElements} fed by a source that produces elements when asked: a read with nothing
 * available {@link #demand() asks the source}, which answers with {@link #push}, {@link #end} or
 * {@link #fail}, at once or later, on any thread. Elements the source produced beyond the read
 * that asked for them are queued for the next reads, and a source that holds elements it can
 * produce at once hands them out with {@link #pollSource()}.
 *
 * <p>An element that is available at once costs one lock and no future: {@link #next()} returns
 * a completed stage, and {@link #forEach} consumes it in its loop.</p>
 * <b>Internal API.</b>
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public abstract class PulledBodyElements<T> implements BodyElements<T> {

    private static final String CLOSED_MESSAGE = "The elements of the body were closed";
    private static final CompletionStage<?> END = CompletableFuture.completedStage(Optional.empty());
    private static final CompletionStage<@Nullable Void> CLOSED_STAGE = CompletableFuture.completedStage(null);

    // guarded by this
    private final ArrayDeque<T> queue = new ArrayDeque<>();
    /**
     * The read that waits for the source: of {@link #next()}, or of the loop of {@link #forEach}.
     */
    private @Nullable CompletableFuture<Optional<T>> pending;
    /**
     * The result of the {@link #forEach} in progress.
     */
    private @Nullable CompletableFuture<@Nullable Void> walking;
    private boolean ended;
    private @Nullable Throwable failure;
    private boolean closed;
    private @Nullable CancellationException cancellation;

    /**
     * Ask the source for more elements. Called by a read that found nothing available, outside
     * the lock. The source answers with {@link #push}, {@link #end} or {@link #fail}; a source
     * that produced nothing for the read must ask itself again while {@link #isWaiting()}.
     */
    protected abstract void demand();

    /**
     * Release the source: the elements were closed. Called once, outside the lock, also after
     * the source ended.
     */
    protected abstract void release();

    /**
     * Take an element that the source can produce at once, without waiting, e.g. the next element
     * of a piece of the body it already received. Called under the lock of these elements, only
     * while no read waits for the source. A failure is reported with {@link #fail}.
     *
     * @return The element, or {@code null}
     */
    protected @Nullable T pollSource() {
        return null;
    }

    /**
     * Whether a read waits for an element.
     *
     * @return Whether a read waits for an element
     */
    protected final synchronized boolean isWaiting() {
        return pending != null;
    }

    /**
     * Take over the waiting read, to answer it with an element that was polled under the lock
     * of these elements, while holding that lock: another thread then no longer finds the read
     * waiting, e.g. to answer it with the end of the elements.
     *
     * @return The waiting read, or {@code null}
     */
    protected final synchronized @Nullable CompletableFuture<Optional<T>> takeWaiting() {
        CompletableFuture<Optional<T>> p = pending;
        pending = null;
        return p;
    }

    /**
     * Hand an element to the waiting read, or queue it. An element that arrives after the
     * elements were closed, ended or failed is released.
     *
     * @param element The element
     */
    protected final void push(T element) {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            p = null;
            if (!closed && !ended && failure == null) {
                p = pending;
                pending = null;
                if (p == null) {
                    queue.add(element);
                    return;
                }
            }
        }
        if (p == null) {
            BodyElementsLoop.discard(element);
        } else {
            p.complete(Optional.of(element));
        }
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
        CompletableFuture<Optional<T>> read;
        synchronized (this) {
            checkStart();
            T element = take();
            if (element != null) {
                return CompletableFuture.completedStage(Optional.of(element));
            }
            Throwable error = failure;
            if (error != null) {
                return CompletableFuture.failedStage(error);
            }
            if (ended) {
                return endStage();
            }
            read = new CompletableFuture<>();
            pending = read;
        }
        demandSafely();
        // a view: the caller cannot complete or cancel the read
        return read.minimalCompletionStage();
    }

    @Override
    public final synchronized @Nullable T poll() {
        checkStart();
        return take();
    }

    @Override
    public final synchronized State state() {
        if (!queue.isEmpty()) {
            return State.AVAILABLE;
        }
        if (closed || failure != null) {
            return State.FAILED;
        }
        if (ended) {
            return State.COMPLETED;
        }
        if (pending == null && walking == null) {
            // asks the source for an element it holds: a waiting read would be answered after it
            T element = pollSource();
            if (element != null) {
                queue.add(element);
                return State.AVAILABLE;
            }
            if (failure != null) {
                return State.FAILED;
            }
            if (ended) {
                return State.COMPLETED;
            }
        }
        return State.PENDING;
    }

    @Override
    public final synchronized @Nullable Throwable failure() {
        if (!queue.isEmpty()) {
            return null;
        }
        return closed ? cancellation() : failure;
    }

    @Override
    public final CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
        Objects.requireNonNull(consumer, "consumer");
        CompletableFuture<@Nullable Void> result = new CompletableFuture<>();
        synchronized (this) {
            checkStart();
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
        List<T> queued;
        @Nullable CancellationException cancelled;
        synchronized (this) {
            if (closed) {
                return CLOSED_STAGE;
            }
            closed = true;
            cancelled = pending != null || walking != null ? cancellation() : null;
            queued = queue.isEmpty() ? List.of() : List.copyOf(queue);
            queue.clear();
            p = pending;
            pending = null;
            w = walking;
            walking = null;
        }
        if (w != null) {
            w.completeExceptionally(Objects.requireNonNull(cancelled));
        }
        if (p != null) {
            p.completeExceptionally(Objects.requireNonNull(cancelled));
        }
        // nobody takes them
        queued.forEach(BodyElementsLoop::discard);
        release();
        return CLOSED_STAGE;
    }

    @Override
    public final void close() {
        closeAsync();
    }

    @SuppressWarnings("unchecked")
    private static <T> CompletionStage<Optional<T>> endStage() {
        return (CompletionStage<Optional<T>>) END;
    }

    /**
     * Create the cancellation reason only when observed. Called under the lock.
     */
    private CancellationException cancellation() {
        if (cancellation == null) {
            cancellation = new CancellationException(CLOSED_MESSAGE);
        }
        return cancellation;
    }

    /**
     * Start an operation: one at a time, and not after closing. Called under the lock.
     */
    private void checkStart() {
        if (closed) {
            throw new IllegalStateException(CLOSED_MESSAGE);
        }
        if (pending != null || walking != null) {
            throw new IllegalStateException("Another operation on the elements of the body is in progress");
        }
    }

    /**
     * Take the next element that is available at once. Called under the lock, while no read
     * waits.
     */
    private @Nullable T take() {
        T element = queue.poll();
        if (element == null && failure == null && !ended) {
            element = pollSource();
        }
        return element;
    }

    private void demandSafely() {
        try {
            demand();
        } catch (Exception | Error e) {
            fail(e);
        }
    }

    /**
     * Consume the elements in order, one at a time: the next element is read when the stage of
     * the consumer completed. Elements that are available at once, and stages that complete at
     * once, are consumed in a loop.
     */
    private void walk(Function<? super T, ? extends CompletionStage<?>> consumer, CompletableFuture<@Nullable Void> result) {
        while (true) {
            T element;
            Throwable error = null;
            CompletableFuture<Optional<T>> read = null;
            synchronized (this) {
                if (walking != result) {
                    // closed meanwhile
                    return;
                }
                element = take();
                if (element == null) {
                    error = failure;
                    if (error == null && !ended) {
                        read = new CompletableFuture<>();
                        pending = read;
                    }
                }
            }
            if (element == null) {
                if (read == null) {
                    // the end, or the failure
                    finish(result, error);
                    return;
                }
                demandSafely();
                if (!read.isDone()) {
                    read.whenComplete((value, readError) -> {
                        if (readError != null) {
                            finish(result, readError);
                        } else if (value.isEmpty()) {
                            finish(result, null);
                        } else if (consume(consumer, result, value.get())) {
                            walk(consumer, result);
                        }
                    });
                    return;
                }
                Optional<T> value;
                try {
                    value = read.join();
                } catch (CompletionException | CancellationException e) {
                    finish(result, e);
                    return;
                }
                if (value.isEmpty()) {
                    finish(result, null);
                    return;
                }
                element = value.get();
            }
            if (!consume(consumer, result, element)) {
                return;
            }
        }
    }

    /**
     * Consume an element.
     *
     * @return Whether the next element can be read at once: the consumer completed at once
     */
    private boolean consume(Function<? super T, ? extends CompletionStage<?>> consumer,
                            CompletableFuture<@Nullable Void> result,
                            T element) {
        CompletionStage<?> stage;
        try {
            stage = Objects.requireNonNull(consumer.apply(element), "The consumer returned no stage");
        } catch (Exception | Error e) {
            finish(result, e);
            return false;
        }
        CompletableFuture<?> future = null;
        try {
            future = stage.toCompletableFuture();
        } catch (UnsupportedOperationException ignored) {
            // a stage that is not a future: continued when it completes
        }
        if (future != null && future.isDone() && !future.isCompletedExceptionally()) {
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

    /**
     * Complete the result of {@link #forEach}. A failure closes the elements.
     */
    private void finish(CompletableFuture<@Nullable Void> result, @Nullable Throwable error) {
        synchronized (this) {
            if (walking == result) {
                walking = null;
            }
        }
        if (error != null) {
            result.completeExceptionally(unwrap(error));
            closeAsync();
        } else {
            result.complete(null);
        }
    }

    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
