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
package io.micronaut.core.async.publisher;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.propagation.ReactivePropagation;
import io.micronaut.core.propagation.PropagatedContext;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Adapts publishers to {@link CompletableFuture}s without a reactive library, for the
 * {@link CompletionStage} counterparts of the publisher based SPIs. Each future subscribes to its
 * publisher right away, and cancelling the future cancels the subscription.
 *
 * <p>A publisher is subscribed to in the {@link PropagatedContext} of the caller, as a thread-local
 * and, when Reactor is present, in the Reactor context, and its signals are handled in that context,
 * so that the continuations of the future run in it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class CompletionStagePublishers {

    private static final boolean REACTOR_PRESENT = isReactorPresent();

    private CompletionStagePublishers() {
    }

    /**
     * The first item of a publisher. The subscription is cancelled once the first item arrives.
     *
     * @param publisher  The publisher
     * @param emptyValue The value of the future when the publisher completes without an item
     * @param <T>        The item type
     * @return A future completed with the first item, the empty value, or the error of the publisher
     */
    public static <T> CompletableFuture<T> first(Publisher<T> publisher, @Nullable T emptyValue) {
        FirstSubscriber<T> subscriber = new FirstSubscriber<>(emptyValue);
        subscribe(publisher, subscriber, subscriber.future);
        return subscriber.future;
    }

    /**
     * All the items of a publisher.
     *
     * @param publisher The publisher
     * @param <T>       The item type
     * @return A future completed with the items in the order they arrived, or the error of the publisher
     */
    public static <T> CompletableFuture<List<T>> collect(Publisher<T> publisher) {
        CollectSubscriber<T> subscriber = new CollectSubscriber<>();
        subscribe(publisher, subscriber, subscriber.future);
        return subscriber.future;
    }

    /**
     * Concatenate the lists of several stages, in the order of the stages. A stage completed
     * with {@code null} adds nothing. The first stage that fails fails the result with its
     * error, and the other stages are cancelled. Cancelling the result cancels the stages.
     *
     * @param stages The stages
     * @param <T>    The item type
     * @return A future completed with the concatenated lists
     */
    public static <T> CompletableFuture<List<T>> concat(Collection<? extends CompletionStage<? extends @Nullable List<? extends T>>> stages) {
        List<CompletableFuture<? extends @Nullable List<? extends T>>> futures = new ArrayList<>(stages.size());
        for (CompletionStage<? extends @Nullable List<? extends T>> stage : stages) {
            futures.add(stage.toCompletableFuture());
        }
        CompletableFuture<List<T>> result = new CompletableFuture<>();
        if (futures.isEmpty()) {
            result.complete(new ArrayList<>());
            return result;
        }
        AtomicInteger remaining = new AtomicInteger(futures.size());
        for (CompletableFuture<? extends @Nullable List<? extends T>> future : futures) {
            future.whenComplete((list, throwable) -> {
                if (throwable != null) {
                    if (result.completeExceptionally(unwrap(throwable))) {
                        cancelAll(futures);
                    }
                } else if (remaining.decrementAndGet() == 0) {
                    List<T> all = new ArrayList<>();
                    for (CompletableFuture<? extends @Nullable List<? extends T>> done : futures) {
                        List<? extends T> items = done.join();
                        if (items != null) {
                            all.addAll(items);
                        }
                    }
                    result.complete(all);
                }
            });
        }
        result.whenComplete((list, throwable) -> {
            if (throwable instanceof CancellationException) {
                cancelAll(futures);
            }
        });
        return result;
    }

    /**
     * A publisher of the value of a stage, the reverse of {@link #first(Publisher, Object)} for
     * the publisher methods that delegate to their {@link CompletionStage} counterparts. The
     * stage is obtained once an item is requested, the publisher emits its value, or completes
     * without an item when the value is {@code null}, and fails with the error of the stage
     * without the {@link CompletionException} wrapper. Cancelling the subscription cancels the
     * stage.
     *
     * @param stageSupplier The supplier of the stage, called for each subscription
     * @param <T>           The value type
     * @return A publisher of the value of the stage
     */
    public static <T> Publisher<T> toPublisher(Supplier<? extends CompletionStage<? extends @Nullable T>> stageSupplier) {
        return Publishers.fromCompletableFuture(() -> {
            CompletableFuture<? extends @Nullable T> source = stageSupplier.get().toCompletableFuture();
            CompletableFuture<T> result = new CompletableFuture<>();
            source.whenComplete((value, throwable) -> {
                if (throwable != null) {
                    result.completeExceptionally(unwrap(throwable));
                } else {
                    result.complete(value);
                }
            });
            return cancelling(source, result);
        });
    }

    /**
     * Cancel a source future once the future derived from it is cancelled, since the futures
     * derived with {@code thenApply} and the like do not cancel their source.
     *
     * @param source  The source future
     * @param derived The derived future
     * @param <T>     The type of the derived future
     * @return The derived future
     */
    public static <T> CompletableFuture<T> cancelling(CompletionStage<?> source, CompletableFuture<T> derived) {
        derived.whenComplete((value, throwable) -> {
            if (throwable instanceof CancellationException) {
                source.toCompletableFuture().cancel(false);
            }
        });
        return derived;
    }

    /**
     * @param throwable The failure of a future
     * @return The failure without the {@link CompletionException} wrapper the future may have added
     */
    public static Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    @SuppressWarnings("ConstantValue")
    private static boolean isReactorPresent() {
        try {
            // resolving the class literal fails when Reactor, an optional dependency, is absent
            Class<?> type = CoreSubscriber.class;
            return type != null;
        } catch (LinkageError e) {
            return false;
        }
    }

    private static void cancelAll(List<? extends CompletableFuture<?>> futures) {
        for (CompletableFuture<?> future : futures) {
            future.cancel(false);
        }
    }

    private static <T> void subscribe(Publisher<T> publisher, AbstractSubscriber<T> subscriber, CompletableFuture<?> future) {
        future.whenComplete((value, throwable) -> {
            if (throwable instanceof CancellationException) {
                subscriber.cancel();
            }
        });
        try {
            PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
            if (propagatedContext.isEmpty()) {
                publisher.subscribe(subscriber);
            } else if (REACTOR_PRESENT) {
                // a Reactor publisher finds the context in its Reactor context, as it did when it
                // was subscribed to by a Reactor chain of the caller
                ReactivePropagation.propagate(propagatedContext, publisher).subscribe(subscriber);
            } else {
                publisher.subscribe(new PropagatingSubscriber<>(propagatedContext, subscriber));
            }
        } catch (Throwable e) {
            future.completeExceptionally(e);
        }
    }

    /**
     * Handles the signals of a publisher in a propagated context, without Reactor.
     *
     * @param propagatedContext The context
     * @param actual            The subscriber
     * @param <T>               The item type
     */
    private record PropagatingSubscriber<T>(PropagatedContext propagatedContext,
                                            Subscriber<T> actual) implements Subscriber<T> {

        @Override
        public void onSubscribe(Subscription s) {
            run(() -> actual.onSubscribe(s));
        }

        @Override
        public void onNext(T t) {
            run(() -> actual.onNext(t));
        }

        @Override
        public void onError(Throwable t) {
            run(() -> actual.onError(t));
        }

        @Override
        public void onComplete() {
            run(actual::onComplete);
        }

        private void run(Runnable signal) {
            if (propagatedContext.isBound()) {
                signal.run();
            } else {
                propagatedContext.propagate(signal);
            }
        }
    }

    /**
     * A subscriber that completes a future and that the future can cancel.
     *
     * @param <T> The item type
     */
    private abstract static class AbstractSubscriber<T> implements Subscriber<T> {
        @Nullable
        @SuppressWarnings("java:S3077") // the subscription is not modified, volatile only publishes it to cancel()
        private volatile Subscription subscription;
        private volatile boolean cancelled;
        private boolean done;

        @Override
        public final void onSubscribe(Subscription s) {
            if (subscription != null) {
                s.cancel();
                return;
            }
            subscription = s;
            if (cancelled) {
                s.cancel();
            } else {
                s.request(requested());
            }
        }

        @Override
        public final void onNext(T t) {
            Objects.requireNonNull(t, "Item cannot be null");
            if (!done) {
                next(t);
            }
        }

        @Override
        public final void onError(Throwable t) {
            if (!done) {
                done = true;
                error(t);
            }
        }

        @Override
        public final void onComplete() {
            if (!done) {
                done = true;
                complete();
            }
        }

        /**
         * Stop the subscription: no more items are wanted.
         */
        final void cancel() {
            cancelled = true;
            Subscription s = subscription;
            if (s != null) {
                s.cancel();
            }
        }

        /**
         * Mark the subscriber done and stop the subscription.
         */
        final void finish() {
            done = true;
            cancel();
        }

        abstract long requested();

        abstract void next(T item);

        abstract void error(Throwable t);

        abstract void complete();
    }

    /**
     * Completes its future with the first item.
     *
     * @param <T> The item type
     */
    private static final class FirstSubscriber<T> extends AbstractSubscriber<T> {
        final CompletableFuture<T> future = new CompletableFuture<>();
        @Nullable
        private final T emptyValue;

        FirstSubscriber(@Nullable T emptyValue) {
            this.emptyValue = emptyValue;
        }

        @Override
        long requested() {
            return 1;
        }

        @Override
        void next(T item) {
            finish();
            future.complete(item);
        }

        @Override
        void error(Throwable t) {
            future.completeExceptionally(t);
        }

        @Override
        @SuppressWarnings("NullAway") // the empty value of a single result is null for a nullable result
        void complete() {
            future.complete(emptyValue);
        }
    }

    /**
     * Completes its future with all the items.
     *
     * @param <T> The item type
     */
    private static final class CollectSubscriber<T> extends AbstractSubscriber<T> {
        final CompletableFuture<List<T>> future = new CompletableFuture<>();
        private final List<T> items = new ArrayList<>();

        @Override
        long requested() {
            return Long.MAX_VALUE;
        }

        @Override
        void next(T item) {
            items.add(item);
        }

        @Override
        void error(Throwable t) {
            future.completeExceptionally(t);
        }

        @Override
        void complete() {
            future.complete(items);
        }
    }
}
