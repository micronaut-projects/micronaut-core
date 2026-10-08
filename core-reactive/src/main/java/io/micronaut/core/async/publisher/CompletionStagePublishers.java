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
import reactor.core.CorePublisher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Adapts publishers to {@link CompletableFuture}s without a reactive library, for the
 * {@link CompletionStage} counterparts of the publisher based SPIs. Each future subscribes to its
 * publisher right away, and cancelling the future cancels the subscription.
 *
 * <p>A publisher is subscribed to in the {@link PropagatedContext} of the caller, as a thread-local
 * and, for a Reactor publisher, in the Reactor context, and its signals are handled in that
 * context, so that the continuations of the future run in it.</p>
 *
 * <p>The futures this class creates are owned by the framework: they are new for each call, and
 * {@link #cancel(CompletionStage)} cancels them. A stage that an implementation of an SPI returned
 * may be shared, a cached one for example, so the framework never cancels it, and ignores its
 * result once it is no longer needed.</p>
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
     * error. Once the result is complete, or cancelled, the futures of this class among the
     * stages are cancelled, and the results of the other stages are ignored.
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
        CompletableFuture<List<T>> result = future();
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
     * A future of the value of a stage transformed by a function. The future fails with the
     * error of the stage, without the {@link CompletionException} wrapper, or with the error the
     * function throws. Cancelling the future cancels the stage when it is a future of this class.
     *
     * @param stage    The stage
     * @param function The function
     * @param <T>      The type of the stage
     * @param <R>      The type of the result
     * @return The future
     */
    public static <T extends @Nullable Object, R extends @Nullable Object> CompletableFuture<R> map(CompletionStage<T> stage,
                                                                                                    Function<? super T, ? extends R> function) {
        CompletableFuture<R> result = future();
        stage.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrap(throwable));
                return;
            }
            R mapped;
            try {
                mapped = function.apply(value);
            } catch (Throwable e) {
                result.completeExceptionally(e);
                return;
            }
            result.complete(mapped);
        });
        cancelling(stage, result);
        return result;
    }

    /**
     * A future of the stage that a function returns for the value of a stage. The future fails
     * with the error of either stage, without the {@link CompletionException} wrapper, or with the
     * error the function throws. Cancelling the future cancels the stages that are futures of
     * this class.
     *
     * @param stage    The stage
     * @param function The function
     * @param <T>      The type of the stage
     * @param <R>      The type of the result
     * @return The future
     */
    public static <T extends @Nullable Object, R extends @Nullable Object> CompletableFuture<R> compose(CompletionStage<T> stage,
                                                                                                        Function<? super T, ? extends CompletionStage<R>> function) {
        CompletableFuture<R> result = future();
        stage.whenComplete((value, throwable) -> {
            if (throwable != null) {
                result.completeExceptionally(unwrap(throwable));
                return;
            }
            CompletionStage<R> next;
            try {
                next = Objects.requireNonNull(function.apply(value), "The function returned no stage");
            } catch (Throwable e) {
                result.completeExceptionally(e);
                return;
            }
            cancelling(next, result);
            next.whenComplete((r, error) -> {
                if (error != null) {
                    result.completeExceptionally(unwrap(error));
                } else {
                    result.complete(r);
                }
            });
        });
        cancelling(stage, result);
        return result;
    }

    /**
     * The stage of an SPI method, or the fallback when the method returned {@code null}: the
     * {@link CompletionStage} counterpart of a mocked bean, whose publisher method is stubbed.
     *
     * @param stage    The stage of the method
     * @param fallback The stage of the publisher method
     * @param <T>      The value type
     * @return The stage
     */
    public static <T extends @Nullable Object> CompletionStage<T> orElse(@Nullable CompletionStage<T> stage,
                                                                         Supplier<? extends CompletionStage<T>> fallback) {
        return stage != null ? stage : fallback.get();
    }

    /**
     * The stage of an SPI method whose value cannot be {@code null}, or the fallback when the
     * method returned {@code null}, or a stage completed with {@code null}: the
     * {@link CompletionStage} counterpart of a mocked bean, whose publisher method is stubbed.
     *
     * @param stage    The stage of the method
     * @param fallback The stage of the publisher method
     * @param <T>      The value type
     * @return The stage
     */
    public static <T> CompletionStage<T> orElseIfNull(@Nullable CompletionStage<@Nullable T> stage,
                                                      Supplier<? extends CompletionStage<T>> fallback) {
        if (stage == null) {
            return fallback.get();
        }
        CompletableFuture<@Nullable T> future = stage.toCompletableFuture();
        if (future.isDone() && !future.isCompletedExceptionally()) {
            T value = future.join();
            return value != null ? (CompletionStage<T>) stage : fallback.get();
        }
        return compose(stage, value -> value != null ? CompletableFuture.completedFuture(value) : fallback.get());
    }

    /**
     * A new future owned by the framework, which {@link #cancel(CompletionStage)} cancels.
     *
     * @param <T> The value type
     * @return The future
     */
    public static <T extends @Nullable Object> CompletableFuture<T> future() {
        return new OwnedFuture<>();
    }

    /**
     * Cancel a stage if it is a future of this class. Any other stage is left as it is, since it
     * may be shared by other callers of the SPI that returned it.
     *
     * @param stage The stage
     */
    public static void cancel(@Nullable CompletionStage<?> stage) {
        if (stage instanceof OwnedFuture<?> future) {
            future.cancel(false);
        }
    }

    /**
     * Cancel a source stage, if it is a future of this class, once the future derived from it is
     * cancelled, since the futures derived with {@code thenApply} and the like do not cancel their
     * source.
     *
     * @param source  The source stage
     * @param derived The derived future
     * @param <T>     The type of the derived future
     * @return The derived future
     */
    public static <T extends @Nullable Object> CompletableFuture<T> cancelling(CompletionStage<?> source, CompletableFuture<T> derived) {
        if (source instanceof OwnedFuture<?>) {
            derived.whenComplete((value, throwable) -> {
                if (throwable instanceof CancellationException) {
                    cancel(source);
                }
            });
        }
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
            Class<?> type = CorePublisher.class;
            return type != null;
        } catch (LinkageError e) {
            return false;
        }
    }

    private static void cancelAll(List<? extends CompletableFuture<?>> futures) {
        for (CompletableFuture<?> future : futures) {
            cancel(future);
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
            } else if (REACTOR_PRESENT && isReactorPublisher(publisher)) {
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

    private static boolean isReactorPublisher(Publisher<?> publisher) {
        return publisher instanceof CorePublisher<?>;
    }

    /**
     * A future created by this class, new for each call, so that the framework may cancel it.
     * The futures derived from it are not owned.
     *
     * @param <T> The value type
     */
    private static final class OwnedFuture<T extends @Nullable Object> extends CompletableFuture<T> {
        @Override
        public <U> CompletableFuture<U> newIncompleteFuture() {
            return new CompletableFuture<>();
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
        final CompletableFuture<T> future = new OwnedFuture<>();
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
        final CompletableFuture<List<T>> future = new OwnedFuture<>();
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
