package io.micronaut.reactive.rxjava2;

import io.micronaut.scheduling.instrument.Instrumenters;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public interface ConditionalInstrumenter extends InvocationInstrumenter {

    boolean isActive();

    default void run(Runnable action) {
        if (isActive()) {
            action.run();
            return;
        }
        Instrumenters.runWith(action, this);
    }

    default <T> T call(Callable<T> callable) throws Exception {
        if (isActive()) {
            return callable.call();
        }
        return Instrumenters.callWith(callable, this);
    }

    default <T> T invoke(Supplier<T> supplier) {
        if (isActive()) {
            return supplier.get();
        }
        return Instrumenters.supplyWith(supplier, this);
    }
}
