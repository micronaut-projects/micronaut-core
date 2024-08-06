/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.scheduling.instrument;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;

/**
 * An {@link Executor} that has been instrumented to allow for propagation of thread state
 * and other instrumentation related tasks.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Experimental
public interface InstrumentedExecutor extends Executor, RunnableInstrumenter {

    /**
     * Unwrap instrumented executor if needed.
     *
     * @param executor The executor
     * @return The unwrapped value or the original one
     * @since 4.0.0
     */
    static Executor unwrap(Executor executor) {
        if (executor instanceof InstrumentedExecutor instrumentedExecutor) {
            return instrumentedExecutor.getTarget();
        }
        return executor;
    }

    /**
     * Unwrap instrumented executor if needed.
     *
     * @param executor The executor
     * @return The unwrapped value or the original one
     * @since 4.0.0
     */
    static ExecutorService unwrap(ExecutorService executor) {
        if (executor instanceof InstrumentedExecutor instrumentedExecutor) {
            return (ExecutorService) instrumentedExecutor.getTarget();
        }
        return executor;
    }

    /**
     * Implementors can override to specify the target {@link Executor}.
     *
     * @return The target {@link Executor}
     */
    Executor getTarget();

    @Override
    default void execute(@NonNull Runnable command) {
        getTarget().execute(instrument(command));
    }

}
