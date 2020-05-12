package io.micronaut.scheduling.instrument;

import io.micronaut.scheduling.instrument.InstrumentedExecutor;
import io.micronaut.scheduling.instrument.InstrumentedExecutorService;
import io.micronaut.scheduling.instrument.InstrumentedScheduledExecutorService;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

public interface Instrumenters {

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

    static <T> T callWith(Callable<T> callable, InvocationInstrumenter instrumenter)
        throws Exception {
        try {
            instrumenter.beforeInvocation();
            return callable.call();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    static void runWith(Runnable runnable, InvocationInstrumenter instrumenter) {
        try {
            instrumenter.beforeInvocation();
            runnable.run();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    static <T> T supplyWith(Supplier<T> supplier, InvocationInstrumenter instrumenter) {
        try {
            instrumenter.beforeInvocation();
            return supplier.get();
        } finally {
            instrumenter.afterInvocation();
        }
    }

    static <T> Callable<T> wrapCallable(Callable<T> callable, InvocationInstrumenter instrumenter) {
        return () -> callWith(callable, instrumenter);
    }

    static Runnable wrapRunnable(Runnable runnable, InvocationInstrumenter instrumenter) {
        return () -> runWith(runnable, instrumenter);
    }

    static <T> Supplier<T> wrapSupplier(Supplier<T> supplier, InvocationInstrumenter instrumenter) {
        return () -> supplyWith(supplier, instrumenter);
    }
}
