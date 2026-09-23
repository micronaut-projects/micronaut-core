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
package io.micronaut.http.server.binding;

import io.micronaut.core.annotation.Internal;
import io.micronaut.http.body.BodyElements;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * {@link BodyElements} over the publisher of the decoded elements of a body: a pull cursor that
 * subscribes on the first read and requests one element per read, so nothing is decoded before
 * it is asked for.
 *
 * @param <T> The type of an element
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class PublisherBodyElements<T> implements BodyElements<T>, Subscriber<T> {

    private final Supplier<? extends Publisher<? extends T>> source;
    private final Runnable discard;

    // guarded by this
    private boolean started;
    private @Nullable Subscription subscription;
    private @Nullable CompletableFuture<Optional<T>> pending;
    private boolean ended;
    private @Nullable Throwable failure;
    private boolean busy;
    private @Nullable CompletableFuture<@Nullable Void> walking;
    private @Nullable CompletionStage<Void> closed;

    /**
     * @param source  Creates the publisher of the elements, on the first read. It may fail
     * @param discard Discards the body when the elements are closed before they were read
     */
    PublisherBodyElements(Supplier<? extends Publisher<? extends T>> source, Runnable discard) {
        this.source = source;
        this.discard = discard;
    }

    @Override
    public CompletionStage<Optional<T>> next() {
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
    public CompletionStage<Void> forEach(Function<? super T, ? extends CompletionStage<?>> consumer) {
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
    public CompletionStage<Void> closeAsync() {
        Subscription s;
        CompletableFuture<Optional<T>> p;
        CompletableFuture<@Nullable Void> w;
        boolean neverStarted;
        synchronized (this) {
            if (closed != null) {
                return closed;
            }
            closed = CompletableFuture.completedStage(null);
            s = subscription;
            p = pending;
            pending = null;
            w = walking;
            walking = null;
            neverStarted = !started;
            started = true;
        }
        CancellationException cancelled = new CancellationException("The elements of the body were closed");
        if (w != null) {
            w.completeExceptionally(cancelled);
        }
        if (p != null) {
            p.completeExceptionally(cancelled);
        }
        if (s != null) {
            // discards the rest of the body
            s.cancel();
        } else if (neverStarted) {
            discard.run();
        }
        synchronized (this) {
            return Objects.requireNonNull(closed);
        }
    }

    @Override
    public void close() {
        closeAsync();
    }

    @Override
    public void onSubscribe(Subscription s) {
        boolean cancel;
        synchronized (this) {
            cancel = closed != null;
            subscription = s;
        }
        if (cancel) {
            s.cancel();
        } else {
            // the first read subscribed
            s.request(1);
        }
    }

    @Override
    public void onNext(T element) {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.complete(Optional.of(element));
        }
    }

    @Override
    public void onError(Throwable t) {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            if (ended || failure != null) {
                return;
            }
            failure = t;
            p = pending;
            pending = null;
        }
        if (p != null) {
            p.completeExceptionally(t);
        }
    }

    @Override
    public void onComplete() {
        CompletableFuture<Optional<T>> p;
        synchronized (this) {
            if (ended || failure != null) {
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
     * Start an operation: one at a time, and not after closing.
     */
    private synchronized void start() {
        if (closed != null) {
            throw new IllegalStateException("The elements of the body were closed");
        }
        if (busy) {
            throw new IllegalStateException("Another operation on the elements of the body is in progress");
        }
        busy = true;
    }

    /**
     * Read one element: subscribe on the first read, and request one element per read.
     *
     * @return Completes with the element, or empty at the end of the body
     */
    private CompletableFuture<Optional<T>> read() {
        CompletableFuture<Optional<T>> result = new CompletableFuture<>();
        Throwable error = null;
        boolean end = false;
        boolean subscribe = false;
        Subscription s = null;
        synchronized (this) {
            if (closed != null) {
                error = new CancellationException("The elements of the body were closed");
            } else if (failure != null) {
                error = failure;
            } else if (ended) {
                end = true;
            } else {
                pending = result;
                if (started) {
                    s = subscription;
                } else {
                    started = true;
                    subscribe = true;
                }
            }
        }
        if (error != null) {
            result.completeExceptionally(error);
        } else if (end) {
            result.complete(Optional.empty());
        } else if (subscribe) {
            Publisher<? extends T> publisher;
            try {
                publisher = source.get();
            } catch (Throwable e) {
                discard.run();
                onError(e);
                return result;
            }
            publisher.subscribe(this);
        } else if (s != null) {
            s.request(1);
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
                    if (consume(consumer, result, element, error)) {
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
            if (!consume(consumer, result, element, null)) {
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
                            @Nullable Optional<T> element,
                            @Nullable Throwable error) {
        if (error != null) {
            finish(result, error);
            return false;
        }
        if (element == null || element.isEmpty()) {
            finish(result, null);
            return false;
        }
        CompletableFuture<?> stage;
        try {
            stage = Objects.requireNonNull(consumer.apply(element.get()), "The consumer returned no stage").toCompletableFuture();
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
