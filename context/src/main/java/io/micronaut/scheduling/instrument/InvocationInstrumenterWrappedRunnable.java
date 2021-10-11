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

import io.micronaut.core.annotation.Internal;

/**
 * Wraps {@link Runnable} and invokes {@link InvocationInstrumenter}.
 *
 * @author Denis Stepanov
 * @since 1.3
 */
@Internal
final class InvocationInstrumenterWrappedRunnable implements Runnable {

    private final InvocationInstrumenter invocationInstrumenter;
    private final Runnable runnable;

    /**
     * @param invocationInstrumenter instrumenter to be invoked
     * @param runnable               original runnable
     */
    InvocationInstrumenterWrappedRunnable(InvocationInstrumenter invocationInstrumenter, Runnable runnable) {
        this.invocationInstrumenter = invocationInstrumenter;
        this.runnable = runnable;
    }

    /**
     * Wrapped call.
     */
    @Override
    public void run() {
        try (Instrumentation ignore = invocationInstrumenter.newInstrumentation().forceCleanup()) {
            runnable.run();
        }
    }
}
