/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

final class BlockingGraalPyContextCustomizer implements GraalPyContextCustomizer {
    private static final AtomicReference<Gate> NEXT_CONTEXT = new AtomicReference<>();

    static Gate blockNextContext() {
        return blockNextContextOn(null);
    }

    /**
     * Block the next context built on the named thread; contexts other threads build go ahead.
     */
    static Gate blockNextContextOn(@Nullable String threadName) {
        Gate gate = new Gate(threadName);
        if (!NEXT_CONTEXT.compareAndSet(null, gate)) {
            throw new IllegalStateException("A context build is already blocked");
        }
        return gate;
    }

    @Override
    public void customize(Context.Builder builder) {
        Gate gate = NEXT_CONTEXT.get();
        if (gate == null
            || (gate.threadName != null && !gate.threadName.equals(Thread.currentThread().getName()))
            || !NEXT_CONTEXT.compareAndSet(gate, null)) {
            return;
        }
        gate.entered.countDown();
        boolean interrupted = false;
        while (true) {
            try {
                gate.proceed.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static final class Gate {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch proceed = new CountDownLatch(1);
        final @Nullable String threadName;

        Gate(@Nullable String threadName) {
            this.threadName = threadName;
        }
    }
}
