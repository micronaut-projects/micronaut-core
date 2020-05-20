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

import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.scheduling.instrument.InvocationInstrumenter;

/**
 * Wraps any {@link InvocationInstrumenter} to protect against multiple chained invocations.
 *
 * @author lgathy
 * @since 2.0
 */
public final class RunOnceInvocationInstrumenter implements ConditionalInstrumenter {

    private final InvocationInstrumenter instrumenter;
    private boolean active;

    /**
     * Default constructor. Can accept {@code null} as argument.
     *
     * @param instrumenter The instrumenter to wrap
     */
    public RunOnceInvocationInstrumenter(@Nullable InvocationInstrumenter instrumenter) {
        this.instrumenter = instrumenter;
        this.active = instrumenter == null;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void beforeInvocation() {
        if (active) {
            return;
        }
        active = true;
        //noinspection ConstantConditions - instrumenter cannot be null at this point since active == false
        instrumenter.beforeInvocation();
    }

    @Override
    public void afterInvocation(boolean cleanup) {
        if (instrumenter == null) {
            return;
        }
        try {
            instrumenter.afterInvocation(cleanup);
        } finally {
            active = false;
        }
    }
}
