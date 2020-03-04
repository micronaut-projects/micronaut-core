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

import io.micronaut.core.annotation.Internal;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Instrumenter that handles a single invocation ensuring double invocation doesn't occur.
 *
 * @author graemerocher
 * @since 1.3.3
 */
@Internal
final class SingleInvocationInstrumenter implements InvocationInstrumenter {

    private final InvocationInstrumenter target;
    private final AtomicBoolean beforeInvoked = new AtomicBoolean(false);
    private final AtomicBoolean afterInvoked = new AtomicBoolean(false);

    /**
     * Default constructor.
     * @param target The target
     */
    SingleInvocationInstrumenter(InvocationInstrumenter target) {
        this.target = target;
    }

    @Override
    public void beforeInvocation() {
        if (beforeInvoked.compareAndSet(false, true)) {
            target.beforeInvocation();
        }
    }

    @Override
    public void afterInvocation() {
        if (afterInvoked.compareAndSet(false, true)) {
            target.afterInvocation();
        }
    }
}
