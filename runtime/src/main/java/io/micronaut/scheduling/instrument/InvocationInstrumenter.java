/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.scheduling.instrument;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.util.CollectionUtils;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

import static io.micronaut.core.util.ArgumentUtils.requireNonNull;

/**
 * An interface for invocation instrumentation.
 *
 * @author Denis Stepanov
 * @author graemerocher
 * @since 1.3
 */
@Experimental
public interface InvocationInstrumenter {

    /**
     * Noop implementation if {@link InvocationInstrumenter}.
     */
    InvocationInstrumenter NOOP = Instrumentation::noop;

    /**
     * @return a one-time {@link Instrumentation} instance which to be used in a try-with-resources to do the
     * instrumentation. To force cleanup invoke {@link Instrumentation#forceCleanup()} on the retuned instance.
     * @since 2.0
     */
    @NonNull Instrumentation newInstrumentation();

    /**
     * Combines multiple instrumenters into one.
     *
     * @param invocationInstrumenters instrumenters to combine
     * @return new instrumenter
     */
    static @NonNull InvocationInstrumenter combine(Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return NOOP;
        }
        if (invocationInstrumenters.size() == 1) {
            return invocationInstrumenters.iterator().next();
        }
        return new MultipleInvocationInstrumenter(invocationInstrumenters);
    }

    /**
     * Wraps {@link Runnable} with instrumentation invocations.
     *
     * @param runnable                {@link Runnable} to be wrapped
     * @param invocationInstrumenters instrumenters to be used
     * @return wrapper
     */
    static @NonNull Runnable instrument(@NonNull Runnable runnable, Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return runnable;
        }
        return instrument(runnable, combine(invocationInstrumenters));
    }


    /**
     * Wraps {@link Callable} with instrumentation invocations.
     *
     * @param callable                {@link Callable} to be wrapped
     * @param invocationInstrumenters instrumenters to be used
     * @param <V>                     callable generic param
     * @return wrapper
     */
    static @NonNull <V> Callable<V> instrument(@NonNull Callable<V> callable, Collection<InvocationInstrumenter> invocationInstrumenters) {
        if (CollectionUtils.isEmpty(invocationInstrumenters)) {
            return callable;
        }
        return instrument(callable, combine(invocationInstrumenters));
    }

    /**
     * Wraps {@link Runnable} with instrumentation invocations.
     *
     * @param runnable               {@link Runnable} to be wrapped
     * @param invocationInstrumenter instrumenter to be used
     * @return wrapper
     */
    static @NonNull Runnable instrument(@NonNull Runnable runnable, InvocationInstrumenter invocationInstrumenter) {
        if (runnable instanceof InvocationInstrumenterWrappedRunnable) {
            return runnable;
        }
        return new InvocationInstrumenterWrappedRunnable(invocationInstrumenter, runnable);
    }

    /**
     * Wraps {@link Callable} with instrumentation invocations.
     *
     * @param callable               {@link Callable} to be wrapped
     * @param invocationInstrumenter instrumenter to be used
     * @param <V>                    callable generic param
     * @return wrapper
     */
    static @NonNull <V> Callable<V> instrument(@NonNull Callable<V> callable, InvocationInstrumenter invocationInstrumenter) {
        if (callable instanceof InvocationInstrumenterWrappedCallable) {
            return callable;
        }
        return new InvocationInstrumenterWrappedCallable<>(invocationInstrumenter, callable);
    }

    /**
     * Wraps the {@code executor} so that every tasks submitted to it will be executed instrumented with the given
     * {@code invocationInstrumenter}. Execution itself will be delegated to the underlying {@code executor}, but it has
     * to be considered that all instrumentation will be done with this very same {@code invocationInstrumenter}
     * instance. This is especially useful when follow-up actions of a given task need to be registered, where a new
     * instrumenter, thus a new wrapped executor instance belongs to each task.
     * <p/>
     * The returned wrapped executor be of subtype {@link ExecutorService} or {@link ScheduledExecutorService} if the
     * input executor instance implemented those interfaces.
     *
     * @param executor               the executor to wrap
     * @param invocationInstrumenter the instrumenter to be used upon task executions with the returned executor
     * @return the wrapped executor
     */
    static Executor instrument(@NonNull Executor executor, @NonNull InvocationInstrumenter invocationInstrumenter) {
        requireNonNull("executor", executor);
        requireNonNull("invocationInstrumenter", invocationInstrumenter);
        if (executor instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {
                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executor;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> callable) {
                    return InvocationInstrumenter.instrument(callable, invocationInstrumenter);
                }

                @Override
                public Runnable instrument(Runnable runnable) {
                    return InvocationInstrumenter.instrument(runnable, invocationInstrumenter);
                }
            };
        } else if (executor instanceof ExecutorService) {
            return new InstrumentedExecutorService() {
                @Override
                public ExecutorService getTarget() {
                    return (ExecutorService) executor;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> callable) {
                    return InvocationInstrumenter.instrument(callable, invocationInstrumenter);
                }

                @Override
                public Runnable instrument(Runnable runnable) {
                    return InvocationInstrumenter.instrument(runnable, invocationInstrumenter);
                }
            };
        } else {
            return new InstrumentedExecutor() {
                @Override
                public Executor getTarget() {
                    return executor;
                }

                @Override
                public Runnable instrument(Runnable runnable) {
                    return InvocationInstrumenter.instrument(runnable, invocationInstrumenter);
                }
            };
        }
    }
}
