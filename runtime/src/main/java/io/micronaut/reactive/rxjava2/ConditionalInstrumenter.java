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
package io.micronaut.reactive.rxjava2;

import io.micronaut.scheduling.instrument.Instrumenters;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Interface of a special group of instrumenters where the instrumentation is omitted if the condition defined by the
 * {@link #isActive()} method is evaluated to {@code false} before each invocation. Therefore, conditional instrumenter
 * instances can safely be reused multiple times inside the same reactive call chain.
 * <p/>
 * Execute instrumentation on-demand via the built-in default methods: {@link #call(Callable)},
 * {@link #invoke(Supplier)} and {@link #run(Runnable)}.
 *
 * @author lgathy
 * @since 2.0
 */
public interface ConditionalInstrumenter extends InvocationInstrumenter {

    /**
     * Determines if the instrumenter is active at the current invocation context. Instrumentation should only be
     * applied when this method returns {@code false}.
     *
     * @return {@code true} if the instrumenter is already active
     */
    boolean isActive();

    /**
     * Instruments the given {@link Runnable}, Instrumenter methods {@link #beforeInvocation()} and
     * {@link #afterInvocation()} are only executed if {@link #isActive()} returns {@code false}. Otherwise
     * {@code action.run();} will be called without instrumentation.
     *
     * @param action the {@link Runnable} to execute
     */
    default void run(Runnable action) {
        if (isActive()) {
            action.run();
            return;
        }
        Instrumenters.runWith(action, this);
    }

    /**
     * Instruments the given {@link Callable}, Instrumenter methods {@link #beforeInvocation()} and
     * {@link #afterInvocation()} are only executed if {@link #isActive()} returns {@code false}. Otherwise
     * {@code callable.call();} will be called without instrumentation.
     * <p/>
     * Use {@link #invoke(Supplier)} instead whenever the callable does not declare to throw checked exceptions.
     *
     * @param callable the {@link Callable} to execute
     * @return the result of {@code callable.call();}
     */
    default <T> T call(Callable<T> callable) throws Exception {
        if (isActive()) {
            return callable.call();
        }
        return Instrumenters.callWith(callable, this);
    }

    /**
     * Instruments the given {@link Supplier}, Instrumenter methods {@link #beforeInvocation()} and
     * {@link #afterInvocation()} are only executed if {@link #isActive()} returns {@code false}. Otherwise
     * {@code supplier.get();} will be called without instrumentation.
     * <p/>
     * Use {@link #call(Callable)} instead if the instrumented code declares to throw checked exceptions and you
     * want it to propagate outside of this invocation.
     *
     * @param supplier the {@link Supplier} to execute
     * @return the result of {@code supplier.get();}
     */
    default <T> T invoke(Supplier<T> supplier) {
        if (isActive()) {
            return supplier.get();
        }
        return Instrumenters.supplyWith(supplier, this);
    }
}
