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

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * Wraps any {@link InvocationInstrumenter} to protect against multiple chained invocations.
 *
 * @author lgathy
 * @since 2.0
 */
public final class RunOnceInvocationInstrumenter implements InvocationInstrumenter {

    /**
     * TODO
     *
     * @param factory
     * @return
     */
    public static @NonNull InvocationInstrumenter create(@NonNull RxInstrumenterFactory factory) {
        return wrap(factory.create());
    }

    /**
     * TODO
     *
     * @param instrumenter
     * @return
     */
    public static @NonNull InvocationInstrumenter wrap(@Nullable InvocationInstrumenter instrumenter) {
        if (instrumenter == null) {
            return InvocationInstrumenter.NOOP;
        }
        if (instrumenter instanceof RunOnceInvocationInstrumenter) {
            return instrumenter;
        }
        return new RunOnceInvocationInstrumenter(instrumenter);
    }

    private final InvocationInstrumenter instrumenter;
    private Instrumentation activeInstrumentation;

    /**
     * Default constructor. Can accept {@code null} as argument.
     *
     * @param instrumenter The instrumenter to wrap
     */
    private RunOnceInvocationInstrumenter(@NonNull InvocationInstrumenter instrumenter) {
        this.instrumenter = instrumenter;
        this.activeInstrumentation = null;
    }

    /**
     * TODO
     *
     * @return
     */
    public boolean isInstrumentationActive() {
        return activeInstrumentation != null;
    }

    @Override
    public @NonNull Instrumentation newInstrumentation() {
        if (isInstrumentationActive()) {
            return Instrumentation.noop();
        }
        Instrumentation instrumentation = instrumenter.newInstrumentation();
        if (!instrumentation.isActive()) {
            return instrumentation;
        }
        class RunOnceInstrumentation implements Instrumentation {

            @Override
            public boolean isActive() {
                return instrumentation.isActive();
            }

            @Override
            public void close(boolean cleanup) {
                instrumentation.close(cleanup);
                activeInstrumentation = null;
            }
        }
        return activeInstrumentation = new RunOnceInstrumentation();
    }

    @Override
    public void beforeInvocation() {
        if (isInstrumentationActive()) {
            return;
        }
        Instrumentation instrumentation = instrumenter.newInstrumentation();
        if (instrumentation.isActive()) {
            activeInstrumentation = instrumentation;
        }
    }

    @Override
    public void afterInvocation(boolean cleanup) {
        if (isInstrumentationActive()) {
            try {
                activeInstrumentation.close(cleanup);
            } finally {
                activeInstrumentation = null;
            }
        }
    }
}
