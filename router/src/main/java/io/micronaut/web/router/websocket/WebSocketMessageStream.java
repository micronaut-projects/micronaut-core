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
package io.micronaut.web.router.websocket;

import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * The messages of a connection to a WebSocket route with a {@link WebSocketMessagesHandler}: each
 * message is offered once it is read, in order, and its stage completes when the subscriber
 * received it, which is when the connection handles the next. A subscriber that requests no more
 * holds the messages back; one that cancels discards the messages that follow. The subscriber is
 * signaled on the executor of the route.
 *
 * @param <T> The type of the messages
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
final class WebSocketMessageStream<T> implements Publisher<T>, Subscription {

    private final Executor executor;
    private final Consumer<Throwable> errors;
    private final Queue<Pending<T>> queue = new ConcurrentLinkedQueue<>();
    private final AtomicLong requested = new AtomicLong();
    /**
     * The signals to the subscriber run in one thread at a time, see {@link #drain()}.
     */
    private final AtomicInteger work = new AtomicInteger();
    private final AtomicBoolean subscribed = new AtomicBoolean();
    private final AtomicReference<@Nullable Subscriber<? super T>> subscriber = new AtomicReference<>();
    private final AtomicReference<@Nullable Throwable> invalidRequest = new AtomicReference<>();
    /**
     * Whether {@link Subscriber#onSubscribe(Subscription)} returned: no other signal comes before.
     */
    private volatile boolean ready;
    private volatile boolean completed;
    private volatile boolean cancelled;
    /**
     * Whether the subscriber was signaled its completion or an error: read and written by {@link #drain()} only.
     */
    private boolean terminated;

    /**
     * @param executor Signals the subscriber
     * @param errors   Handles a subscriber that fails a signal, like the error of a handler
     */
    WebSocketMessageStream(Executor executor, Consumer<Throwable> errors) {
        this.executor = executor;
        this.errors = errors;
    }

    /**
     * Offer a message the connection read.
     *
     * @param message The message
     * @return A stage that completes when the subscriber received the message, or it was discarded
     */
    CompletionStage<?> offer(T message) {
        if (cancelled || completed) {
            return WebSocketRouteMethod.DONE;
        }
        Pending<T> pending = new Pending<>(message, new CompletableFuture<>());
        queue.add(pending);
        signal();
        return pending.delivered();
    }

    /**
     * The connection closed: the subscriber completes once it received the messages that were read.
     */
    void complete() {
        completed = true;
        signal();
    }

    /**
     * Discard the messages, e.g. because the handler failed or is done without a subscriber, so
     * that the connection handles the next.
     */
    void discard() {
        cancel();
    }

    /**
     * @return Whether the stream has a subscriber
     */
    boolean hasSubscriber() {
        return subscribed.get();
    }

    @Override
    public void subscribe(Subscriber<? super T> s) {
        Objects.requireNonNull(s, "subscriber");
        if (!subscribed.compareAndSet(false, true)) {
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    // the subscriber fails at once, there is nothing to deliver
                }

                @Override
                public void cancel() {
                    // the subscriber fails at once, there is nothing to cancel
                }
            });
            s.onError(new IllegalStateException("The messages of a WebSocket connection have a single subscriber"));
            return;
        }
        subscriber.set(s);
        s.onSubscribe(this);
        ready = true;
        signal();
    }

    @Override
    public void request(long n) {
        if (n <= 0) {
            invalidRequest.compareAndSet(null, new IllegalArgumentException("Rule 3.9: the number of messages requested must be positive: " + n));
            cancelled = true;
        } else {
            requested.getAndUpdate(current -> current + n < 0 ? Long.MAX_VALUE : current + n);
        }
        signal();
    }

    @Override
    public void cancel() {
        cancelled = true;
        signal();
    }

    private void signal() {
        try {
            executor.execute(this::drain);
        } catch (RejectedExecutionException e) {
            // e.g. the application stops: signal here
            drain();
        }
    }

    private void drain() {
        if (work.getAndIncrement() != 0) {
            return;
        }
        int missed = 1;
        do {
            Subscriber<? super T> s = ready ? subscriber.get() : null;
            try {
                if (cancelled) {
                    discardAll();
                    Throwable error = invalidRequest.get();
                    if (error != null && s != null && !terminated) {
                        terminated = true;
                        s.onError(error);
                    }
                } else if (s != null && !terminated) {
                    deliver(s);
                }
            } catch (Throwable e) {
                // rule 2.13: a subscriber that fails a signal is cancelled
                cancelled = true;
                terminated = true;
                discardAll();
                errors.accept(e);
            }
            missed = work.addAndGet(-missed);
        } while (missed != 0);
    }

    private void deliver(Subscriber<? super T> s) {
        long demand = requested.get();
        long delivered = 0;
        while (delivered != demand && !cancelled) {
            Pending<T> pending = queue.poll();
            if (pending == null) {
                break;
            }
            delivered++;
            try {
                s.onNext(pending.message());
            } finally {
                pending.delivered().complete(null);
            }
        }
        if (delivered != 0 && demand != Long.MAX_VALUE) {
            requested.addAndGet(-delivered);
        }
        if (completed && !cancelled && queue.isEmpty()) {
            terminated = true;
            s.onComplete();
        }
    }

    /**
     * Discard the messages that were not received, so that the connection handles the next.
     */
    private void discardAll() {
        Pending<T> pending;
        while ((pending = queue.poll()) != null) {
            pending.delivered().complete(null);
        }
    }

    /**
     * A message read, and whether the subscriber received it.
     *
     * @param message   The message
     * @param delivered Completes when the subscriber received it, or it was discarded
     * @param <T>       The type of the message
     */
    private record Pending<T>(T message, CompletableFuture<Void> delivered) {
    }
}
