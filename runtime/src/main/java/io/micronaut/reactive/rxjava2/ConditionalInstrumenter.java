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
import io.micronaut.scheduling.instrument.DefaultInstrumentation;
import io.micronaut.scheduling.instrument.Instrumentation;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * Interface of a special group of instrumenters where the instrumentation is omitted if the condition defined by the
 * {@link #testInstrumentationNeeded()} method is evaluated to {@code false} before each invocation. Therefore,
 * conditional instrumenter instances can safely be reused multiple times inside the same reactive call chain.
 *
 * @author lgathy
 * @since 2.0
 */
public interface ConditionalInstrumenter extends InvocationInstrumenter {

    /**
     * Determines if instrumentation is needed at the current invocation context. {@link #newInstrumentation()} will
     * return {@link Instrumentation#noop()} whenever this method returns {@code false}, thus it's safe to do the
     * instrumentation in a try-with-resource block with {@link InvocationInstrumenter#newInstrumentation()}.
     *
     * @return {@code true} if instrumentation is needed
     */
    boolean testInstrumentationNeeded();

    @Override
    default @NonNull Instrumentation newInstrumentation() {
        return testInstrumentationNeeded() ? new DefaultInstrumentation(this) : Instrumentation.noop();
    }
}
