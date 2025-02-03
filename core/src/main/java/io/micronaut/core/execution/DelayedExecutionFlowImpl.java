/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
final class DelayedExecutionFlowImpl<T> implements DelayedExecutionFlow<T> {
    private static final Logger LOG = LoggerFactory.getLogger(DelayedExecutionFlowImpl.class);

    /**
     * The head of the linked list of steps in this flow.
     */
    private Head head = new Head();
    /**
     * The tail of the linked list of steps in this flow.
     */
    private Step tail = head;
    private Runnable onCancel;
    private volatile boolean cancelled;

    /**
     * Perform the given step with the given item. Continue on until there is either no more steps,
     * either because onComplete was hit or because the consumer is not finished adding all the
     * steps, or until a step does not finish immediately, e.g. flatMap returning a non-immediate
     * flow.
     *
     * @param step The step to execute first
     * @param executionFlow The previous execution flow
     */
    private static void work(Step step, ExecutionFlow<Object> executionFlow) {
        do {
            executionFlow = step.apply(executionFlow);
            step = step.atomicSetOutput(executionFlow);
        } while (step != null);
    }

    @Override
    public void completeFrom(@NonNull ExecutionFlow<T> flow) {
        flow.onComplete(this::complete);
    }

    /**
     * Complete with initial execution flow.
     *
     * @param executionFlow The execution flow
     */
    private void complete0(@NonNull ExecutionFlow<Object> executionFlow) {
        if (head == null) {
            throw new IllegalStateException("Delayed flow has been completed");
        }
        Step immediateStep = head.atomicSetOutput(executionFlow);
        if (immediateStep != null) {
            work(immediateStep, executionFlow);
        }
        head = null;
    }

    @Override
    public void complete(T result) {
        complete0(result == null ? ExecutionFlow.empty() : ExecutionFlow.just(result));
    }

    @Override
    public void completeExceptionally(Throwable exc) {
        complete0(ExecutionFlow.error(exc));
    }

    /**
     * Add a new step to this flow.
     *
     * @param next The new step
     * @param <R> The return type of the flow for generics support
     * @return This flow
     */
    @SuppressWarnings("unchecked")
    private <R> ExecutionFlow<R> next(Step next) {
        Step oldTail = tail;
        if (oldTail instanceof DelayedExecutionFlowImpl.Cancel<?>) {
            // because the Cancel step can only cancel flows upstream of it, we can't allow adding
            // further downstream steps.
            throw new IllegalStateException("Cannot add more ExecutionFlow steps after cancellation");
        }
        tail = next;
        ExecutionFlow output = oldTail.atomicSetNext(next);
        if (output != null) {
            work(next, output);
        }
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super T, ? extends R> transformer) {
        return next(new Map<>(transformer));
    }

    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super T, ? extends ExecutionFlow<? extends R>> transformer) {
        return next(new FlatMap<>(transformer));
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        return next(new Then<>(supplier));
    }

    @Override
    public ExecutionFlow<T> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<? extends T>> fallback) {
        return next(new OnErrorResume<>(fallback));
    }

    @Override
    public ExecutionFlow<T> putInContext(String key, Object value) {
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super T, Throwable> fn) {
        next(new OnComplete<>(fn));
    }

    @Override
    public void completeTo(CompletableFuture<T> completableFuture) {
        next(new OnCompleteToFuture<>(completableFuture));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public ImperativeExecutionFlow<T> tryComplete() {
        ExecutionFlow tailOutput = tail.output;
        if (tailOutput != null) {
            return tailOutput.tryComplete();
        } else {
            return null;
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        if (cancelled) {
            return;
        }
        next(new Cancel());
        cancelled = true;
        Runnable hook = this.onCancel;
        if (hook != null) {
            hook.run();
        }
    }

    @Override
    public void onCancel(Runnable hook) {
        Runnable prev = this.onCancel;
        if (prev != null) {
            this.onCancel = () -> {
                prev.run();
                hook.run();
            };
        } else {
            this.onCancel = hook;
        }
    }

    private abstract static sealed class Step<I, O> {
        /**
         * The next step to take, or {@code null} if there is no next step yet.
         */
        private volatile Step next;
        /**
         * The output of this step, or {@code null} if this step has not completed yet.
         */
        private volatile ExecutionFlow<Object> output;

        /**
         * Apply this step.
         *
         * @param input The input for the step
         * @return The return value of the {@code return*} method called
         */
        abstract ExecutionFlow<O> apply(ExecutionFlow<I> input);

        /**
         * Atomically set the output of this step. If this returns non-null, the caller must call
         * {@link #work(Step, ExecutionFlow)} with the returned step.
         *
         * @param output The output of this step
         * @return The next step to execute using {@link #work(Step, ExecutionFlow)}, or {@code null} if
         * the next step will be executed later
         */
        @Nullable
        final Step atomicSetOutput(ExecutionFlow<Object> output) {
            if (this.output != null) {
                // this is a best-effort check, the output field isn't always set
                throw new IllegalStateException("Already completed");
            }
            Step next = this.next;
            if (next != null) {
                return next;
            }
            this.output = output;
            next = this.next;
            if (next != null) {
                // another thread completed at the same time! one or both threads hit this sync
                // block.
                synchronized (this) {
                    // deconfliction path
                    next = this.next;
                    if (next != null) {
                        // our sync block was executed first, unset output so the other thread aborts
                        this.output = null;
                        return next;
                    }
                }
            }
            // no next step yet.
            return null;
        }

        /**
         * Atomically set the next step. If this returns non-null, the caller must call
         * {@link #work(Step, ExecutionFlow)} with the returned output value.
         *
         * @param next The next step to execute
         * @return The output flow value of this step, to be passed to {@link #work(Step, ExecutionFlow)}
         */
        @Nullable
        final ExecutionFlow<Object> atomicSetNext(Step next) {
            if (this.next != null) {
                // this is a best-effort check, the next field isn't always set
                throw new IllegalStateException("Already added a next step");
            }
            ExecutionFlow<Object> output = this.output;
            if (output != null) {
                return output;
            }
            this.next = next;
            output = this.output;
            if (output != null) {
                // another thread completed at the same time! one or both threads hit this sync
                // block.
                synchronized (this) {
                    // deconfliction path
                    output = this.output;
                    if (output != null) {
                        // our sync block was executed first, unset next so the other thread aborts
                        this.next = null;
                        return output;
                    }
                }
            }
            // no output yet.
            return null;
        }

        /**
         * Return an immediate failed value from this step (e.g. from map).
         *
         * @param e The exception to return
         * @return The value to return from {@link #work}
         */
        final <O> ExecutionFlow<O> returnError(Throwable e) {
            return ExecutionFlow.error(e);
        }
    }

    /**
     * Mock step used as the head of the linked list of steps.
     */
    private static final class Head extends Step<Object, Object> {

        @Override
        ExecutionFlow<Object> apply(ExecutionFlow<Object> input) {
            throw new UnsupportedOperationException();
        }

    }

    private static final class Map<I, O> extends Step<I, O> {
        private final Function<? super I, ? extends O> transformer;

        private Map(Function<? super I, ? extends O> transformer) {
            this.transformer = transformer;
        }

        @Override
        ExecutionFlow<O> apply(ExecutionFlow<I> executionFlow) {
            try {
                return executionFlow.map(transformer);
            } catch (Exception e) {
                return returnError(e);
            }
        }
    }

    private static final class FlatMap<I, O>  extends Step<I, O> {
        private final Function<? super I, ? extends ExecutionFlow<? extends O>> transformer;

        private FlatMap(Function<? super I, ? extends ExecutionFlow<? extends O>> transformer) {
            this.transformer = transformer;
        }

        @Override
        ExecutionFlow<O> apply(ExecutionFlow<I> executionFlow) {
            try {
                return executionFlow.flatMap(transformer);
            } catch (Exception e) {
                return returnError(e);
            }
        }
    }

    private static final class Then<I, O> extends Step<I, O> {
        private final Supplier<? extends ExecutionFlow<? extends O>> transformer;

        private Then(Supplier<? extends ExecutionFlow<? extends O>> transformer) {
            this.transformer = transformer;
        }

        @Override
        ExecutionFlow<O> apply(ExecutionFlow<I> executionFlow) {
            try {
                return executionFlow.then(transformer);
            } catch (Exception e) {
                return returnError(e);
            }
        }
    }

    private static final class OnErrorResume<I> extends Step<I, I> {
        private final Function<? super Throwable, ? extends ExecutionFlow<? extends I>> fallback;

        private OnErrorResume(Function<? super Throwable, ? extends ExecutionFlow<? extends I>> fallback) {
            this.fallback = fallback;
        }

        @Override
        ExecutionFlow<I> apply(ExecutionFlow<I> executionFlow) {
            try {
                return executionFlow.onErrorResume(fallback);
            } catch (Exception e) {
                return returnError(e);
            }
        }
    }

    private static final class OnComplete<E> extends Step<E, E> {
        private final BiConsumer<? super E, Throwable> consumer;

        public OnComplete(BiConsumer<? super E, Throwable> consumer) {
            this.consumer = consumer;
        }

        @Override
        ExecutionFlow<E> apply(ExecutionFlow<E> executionFlow) {
            try {
                executionFlow.onComplete(consumer);
            } catch (Exception e) {
                LOG.error("Failed to execute onComplete", e);
            }
            return executionFlow;
        }
    }

    private static final class OnCompleteToFuture<E> extends Step<E, E> {
        private final CompletableFuture<E> future;

        public OnCompleteToFuture(CompletableFuture<E> future) {
            this.future = future;
        }

        @Override
        ExecutionFlow<E> apply(ExecutionFlow<E> executionFlow) {
            try {
                executionFlow.completeTo(future);
            } catch (Exception e) {
                LOG.error("Failed to execute onComplete", e);
            }
            return executionFlow;
        }
    }

    private static final class Cancel<E> extends Step<E, E> {
        private static final ExecutionFlow ERR = ExecutionFlow.error(new AssertionError("Should never be hit, no further steps are allowed after cancel"));

        @Override
        ExecutionFlow<E> apply(ExecutionFlow<E> input) {
            input.cancel();
            return ERR;
        }
    }
}
