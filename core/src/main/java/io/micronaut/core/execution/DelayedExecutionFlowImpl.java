package io.micronaut.core.execution;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

@SuppressWarnings("rawtypes")
final class DelayedExecutionFlowImpl<T> implements DelayedExecutionFlow<T> {
    private static final Logger LOG = LoggerFactory.getLogger(DelayedExecutionFlowImpl.class);

    /**
     * Object used as a stand-in for a {@code null} completion to distinguish it from the
     * uncompleted state.
     */
    private static final Object NULL = new Object();

    /**
     * The head of the linked list of steps in this flow.
     */
    private final Head head = new Head();
    /**
     * The tail of the linked list of steps in this flow.
     */
    private Step tail = head;

    /**
     * Perform the given step with the given item. Continue on until there is either no more steps,
     * either because onComplete was hit or because the consumer is not finished adding all the
     * steps, or until a step does not finish immediately, e.g. flatMap returning a non-immediate
     * flow.
     *
     * @param step The step to execute first
     * @param item The input item for the step
     */
    private static void work(Step step, Object item) {
        while (true) {
            item = step.apply(item);
            if (item == null) {
                // step suspended
                break;
            }
            step = step.atomicSetOutput(item);
            if (step == null) {
                break;
            }
        }
    }

    /**
     * Complete this flow with the given result.
     *
     * @param result The result object. May be a {@link Failure}, {@link #NULL}, or any other
     *               successful value.
     */
    private void complete0(@NonNull Object result) {
        Step immediateStep = head.atomicSetOutput(result);
        if (immediateStep != null) {
            work(immediateStep, result);
        }
    }

    @Override
    public void complete(T result) {
        complete0(result == null ? NULL : result);
    }

    @Override
    public void completeExceptionally(Throwable exc) {
        complete0(new Failure(exc));
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
        tail = next;
        Object output = oldTail.atomicSetNext(next);
        if (output != null) {
            work(next, output);
        }
        return (ExecutionFlow<R>) this;
    }

    @Override
    public <R> ExecutionFlow<R> map(Function<? super T, ? extends R> transformer) {
        return next(new Map(transformer));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <R> ExecutionFlow<R> flatMap(Function<? super T, ? extends ExecutionFlow<? extends R>> transformer) {
        return next(new FlatMap((Function<Object, ? extends ExecutionFlow>) transformer));
    }

    @Override
    public <R> ExecutionFlow<R> then(Supplier<? extends ExecutionFlow<? extends R>> supplier) {
        return next(new Then<>(supplier));
    }

    @Override
    public ExecutionFlow<T> onErrorResume(Function<? super Throwable, ? extends ExecutionFlow<? extends T>> fallback) {
        return next(new OnErrorResume(fallback));
    }

    @Override
    public ExecutionFlow<T> putInContext(String key, Object value) {
        return this;
    }

    @Override
    public void onComplete(BiConsumer<? super T, Throwable> fn) {
        next(new OnComplete<>(fn));
    }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public ImperativeExecutionFlow<T> tryComplete() {
        Object tailOutput = tail.output;
        if (tailOutput != null) {
            if (tailOutput instanceof Failure failure) {
                return (ImperativeExecutionFlow<T>) new ImperativeExecutionFlowImpl(null, failure.t);
            } else if (tailOutput == NULL) {
                return (ImperativeExecutionFlow<T>) new ImperativeExecutionFlowImpl(null, null);
            } else {
                return (ImperativeExecutionFlow<T>) new ImperativeExecutionFlowImpl(tailOutput, null);
            }
        } else {
            return null;
        }
    }

    /**
     * Special wrapper for exception results.
     *
     * @param t The exception of the failure
     */
    private record Failure(Throwable t) {
    }

    private abstract static class Step {
        /**
         * The next step to take, or {@code null} if there is no next step yet.
         */
        private volatile Step next;
        /**
         * The output of this step, or {@code null} if this step has not completed yet.
         */
        private volatile Object output;

        /**
         * Apply this step. Must call one of {@link #returnImmediate}, {@link #returnFlow},
         * {@link #returnError} or {@link #returnUnchanged}.
         *
         * @param input The input for the step
         * @return The return value of the {@code return*} method called
         */
        abstract Object apply(Object input);

        /**
         * Atomically set the output of this step. If this returns non-null, the caller must call
         * {@link #work(Step, Object)} with the returned step.
         *
         * @param output The output of this step
         * @return The next step to execute using {@link #work(Step, Object)}, or {@code null} if
         * the next step will be executed later
         */
        @Nullable
        final Step atomicSetOutput(Object output) {
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
         * {@link #work(Step, Object)} with the returned output value.
         *
         * @param next The next step to execute
         * @return The output value of this step, to be passed to {@link #work(Step, Object)}, or
         * {@code null} if the output is not yet known and the given step will be executed later
         */
        @Nullable
        final Object atomicSetNext(Step next) {
            if (this.next != null) {
                // this is a best-effort check, the next field isn't always set
                throw new IllegalStateException("Already added a next step");
            }
            Object output = this.output;
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
         * Return a flow from this step (e.g. from flatMap).
         *
         * @param outputFlow The flow to return
         * @return The value to return from {@link #work}
         */
        final Object returnFlow(ExecutionFlow<?> outputFlow) {
            ImperativeExecutionFlow<?> complete = outputFlow.tryComplete();
            if (complete != null) {
                Throwable error = complete.getError();
                if (error == null) {
                    return returnImmediate(complete.getValue());
                } else {
                    return returnError(error);
                }
            }

            outputFlow.onComplete((v, t) -> {
                Object result;
                if (t == null) {
                    result = v == null ? NULL : v;
                } else {
                    result = new Failure(t);
                }
                Step step = atomicSetOutput(result);
                if (step != null) {
                    work(step, result);
                }
            });
            return null;
        }

        /**
         * Return an immediate successful value from this step (e.g. from map).
         *
         * @param o The value to return
         * @return The value to return from {@link #work}
         */
        final Object returnImmediate(@Nullable Object o) {
            return o == null ? NULL : o;
        }

        /**
         * Signal that this step made no change to the input (e.g. a {@code map} when the flow has
         * an error).
         *
         * @param input The input passed to {@link #apply}
         * @return The value to return from {@link #work}
         */
        final Object returnUnchanged(Object input) {
            return input;
        }

        /**
         * Return an immediate failed value from this step (e.g. from map).
         *
         * @param e The exception to return
         * @return The value to return from {@link #work}
         */
        final Object returnError(Throwable e) {
            return new Failure(e);
        }
    }

    /**
     * Mock step used as the head of the linked list of steps.
     */
    private static class Head extends Step {
        @Override
        Object apply(Object input) {
            throw new UnsupportedOperationException();
        }
    }

    private static class Map extends Step {
        private final Function transformer;

        private Map(Function transformer) {
            this.transformer = transformer;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object apply(Object input) {
            try {
                if (input instanceof Failure) {
                    return returnUnchanged(input);
                } else if (input == NULL) {
                    return returnImmediate(transformer.apply(null));
                } else {
                    return returnImmediate(transformer.apply(input));
                }
            } catch (Exception e) {
                return returnError(e);
            }
        }
    }

    private static class FlatMap extends Step {
        private final Function<Object, ? extends ExecutionFlow> transformer;

        private FlatMap(Function<Object, ? extends ExecutionFlow> transformer) {
            this.transformer = transformer;
        }

        @Override
        Object apply(Object input) {
            if (input instanceof Failure) {
                return returnUnchanged(input);
            } else {
                try {
                    if (input == NULL) {
                        return returnFlow(transformer.apply(null));
                    } else {
                        return returnFlow(transformer.apply(input));
                    }
                } catch (Exception e) {
                    return returnError(e);
                }
            }
        }
    }

    private static class Then<R> extends Step {
        private final Supplier<? extends ExecutionFlow<? extends R>> transformer;

        private Then(Supplier<? extends ExecutionFlow<? extends R>> transformer) {
            this.transformer = transformer;
        }

        @Override
        Object apply(Object input) {
            if (input instanceof Failure) {
                return returnUnchanged(input);
            } else {
                try {
                    return returnFlow(transformer.get());
                } catch (Exception e) {
                    return returnError(e);
                }
            }
        }
    }

    private static class OnErrorResume extends Step {
        private final Function<? super Throwable, ? extends ExecutionFlow<?>> fallback;

        private OnErrorResume(Function<? super Throwable, ? extends ExecutionFlow<?>> fallback) {
            this.fallback = fallback;
        }

        @Override
        Object apply(Object input) {
            if (input instanceof Failure failure) {
                try {
                    return returnFlow(fallback.apply(failure.t));
                } catch (Exception e) {
                    return returnError(e);
                }
            } else {
                return returnUnchanged(input);
            }
        }
    }

    private static class OnComplete<E> extends Step {
        private final BiConsumer<? super E, Throwable> consumer;

        public OnComplete(BiConsumer<? super E, Throwable> consumer) {
            this.consumer = consumer;
        }

        @SuppressWarnings("unchecked")
        @Override
        Object apply(Object input) {
            try {
                if (input instanceof Failure failure) {
                    consumer.accept(null, failure.t);
                } else if (input == NULL) {
                    consumer.accept(null, null);
                } else {
                    consumer.accept((E) input, null);
                }
            } catch (Exception e) {
                LOG.error("Failed to execute onComplete", e);
            }
            return null;
        }
    }
}
