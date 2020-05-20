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

import static java.util.Objects.requireNonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

/**
 * Public utility methods to work with {@link InvocationInstrumenter}-s.
 * <p/>
 * Invoke functions instrumented with a given {@link InvocationInstrumenter} using methods:
 * {@link #callWith(Callable, InvocationInstrumenter)}, {@link #runWith(Runnable, InvocationInstrumenter)} and
 * {@link #supplyWith(Supplier, InvocationInstrumenter)}.
 * <p/>
 * The following methods support delaying instrumented invocations by instantiating wrapped instances:
 * {@link #wrapCallable(Callable, InvocationInstrumenter)}, {@link #wrapRunnable(Runnable, InvocationInstrumenter)} and
 * {@link #wrapSupplier(Supplier, InvocationInstrumenter)}.
 * <p/>
 * {@link #instrumentExecutor(Executor, InvocationInstrumenter)} wraps an {@link Executor} to instrument all submitted
 * tasks with a given instrumenter.
 *
 * @author lgathy
 * @since 2.0
 */
public interface Instrumenters {

    /**
     * Wraps the {@code executor} so that every tasks submitted to it will be executed instrumented with the given
     * {@code instrumenter}. Execution itself will be delegated to the underlying {@code executor}, but it has to be
     * considered that all instrumentation will be done with this very same {@code instrumenter} instance. This is
     * especially useful when follow-up actions of a given task need to be registered, where a new instrumenter, thus a
     * new wrapped executor instance belongs to each task.
     * <p/>
     * The returned wrapped executor be of subtype {@link ExecutorService} or {@link ScheduledExecutorService} if the
     * input executor instance implemented those interfaces.
     *
     * @param executor     the executor to wrap
     * @param instrumenter the instrumenter to be used upon task executions with the returned executor
     * @return the wrapped executor
     */
    static Executor instrumentExecutor(Executor executor, InvocationInstrumenter instrumenter) {
        requireNonNull(executor, "executor");
        requireNonNull(instrumenter, "instrumenter");
        if (executor instanceof ScheduledExecutorService) {
            return new InstrumentedScheduledExecutorService() {
                @Override
                public ScheduledExecutorService getTarget() {
                    return (ScheduledExecutorService) executor;
                }

                @Override
                public <T> Callable<T> instrument(Callable<T> callable) {
                    return wrapCallable(callable, instrumenter);
                }

                @Override
                public Runnable instrument(Runnable runnable) {
                    return wrapRunnable(runnable, instrumenter);
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
                    return wrapCallable(callable, instrumenter);
                }

                @Override
                public Runnable instrument(Runnable runnable) {
                    return wrapRunnable(runnable, instrumenter);
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
                    return wrapRunnable(runnable, instrumenter);
                }
            };
        }
    }

    /**
     * Instruments {@code callable} with {@code instrumenter}. Use {@link #supplyWith(Supplier, InvocationInstrumenter)}
     * instead whenever the callable does not declare to throw checked exceptions.
     *
     * @param callable     the callable to execute
     * @param instrumenter the instrumenter to instrument with
     * @param <T>          the return type of the callable
     * @return the result of {@code callable.call();}
     * @throws Exception if thrown by {@code callable.call()}
     */
    static <T> T callWith(Callable<T> callable, InvocationInstrumenter instrumenter) throws Exception {
        try {
            instrumenter.beforeInvocation();
            return callable.call();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    /**
     * Instruments {@code runnable} with {@code instrumenter}.
     *
     * @param runnable     the runnable to execute
     * @param instrumenter the instrumenter to instrument with
     */
    static void runWith(Runnable runnable, InvocationInstrumenter instrumenter) {
        try {
            instrumenter.beforeInvocation();
            runnable.run();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    /**
     * Instruments {@code supplier} with {@code instrumenter}. Use {@link #callWith(Callable, InvocationInstrumenter)}
     * instead if the instrumented code declares to throw checked exceptions and you want it to propagate outside of
     * this invocation.
     *
     * @param supplier     the supplier to execute
     * @param instrumenter the instrumenter to instrument with
     * @param <T>          the return type of the supplier
     * @return the result of {@code supplier.get();}
     */
    static <T> T supplyWith(Supplier<T> supplier, InvocationInstrumenter instrumenter) {
        try {
            instrumenter.beforeInvocation();
            return supplier.get();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    /**
     * Creates a wrapped {@link Callable} which will execute as {@code callWith(callable, instrumenter)}.
     *
     * @param callable     the callable to execute
     * @param instrumenter the instrumenter to instrument with
     * @param <T>          the return type of the callable
     * @return the wrapped {@link Callable}
     * @see #callWith(Callable, InvocationInstrumenter)
     */
    static <T> Callable<T> wrapCallable(Callable<T> callable, InvocationInstrumenter instrumenter) {
        return () -> callWith(callable, instrumenter);
    }

    /**
     * Creates a wrapped {@link Runnable} which will execute as {@code runWith(runnable, instrumenter)}.
     *
     * @param runnable     the runnable to execute
     * @param instrumenter the instrumenter to instrument with
     * @return the wrapped {@link Runnable}
     * @see #runWith(Runnable, InvocationInstrumenter)
     */
    static Runnable wrapRunnable(Runnable runnable, InvocationInstrumenter instrumenter) {
        return () -> runWith(runnable, instrumenter);
    }

    /**
     * Creates a wrapped {@link Supplier} which will execute as {@code supplyWith(callable, supplier)}.
     *
     * @param supplier     the supplier to execute
     * @param instrumenter the instrumenter to instrument with
     * @param <T>          the return type of the supplier
     * @return the wrapped {@link Supplier}
     * @see #supplyWith(Supplier, InvocationInstrumenter)
     */
    static <T> Supplier<T> wrapSupplier(Supplier<T> supplier, InvocationInstrumenter instrumenter) {
        return () -> supplyWith(supplier, instrumenter);
    }
}
