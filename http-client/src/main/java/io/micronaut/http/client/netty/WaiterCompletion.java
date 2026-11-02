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
package io.micronaut.http.client.netty;

import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the continuation of a pooled request that waited for a connection in the context of the
 * waiting caller. The connection is usually handed over by another exchange, while its context is
 * current. Switching to the waiter's context does not undo the thread-bound state (e.g. MDC) of
 * that other exchange, so in that case the continuation runs from a fresh task instead.
 */
final class WaiterCompletion {
    private WaiterCompletion() {
    }

    /**
     * Run the completion in the waiter's context.
     *
     * @param waiterContext The context of the waiting caller
     * @param freshExecutor An executor whose tasks start without thread-bound state, e.g. an event
     *                      loop of the client. If it rejects the task (it shuts down), the
     *                      completion runs inline, so that the waiter is not stranded
     * @param completion    The completion
     */
    static void complete(PropagatedContext waiterContext, Executor freshExecutor, Runnable completion) {
        Runnable inWaiterContext = waiterContext.wrap(completion);
        PropagatedContext current = PropagatedContext.find().orElse(null);
        if (current == null || current == waiterContext || !hasThreadElements(current)) {
            inWaiterContext.run();
        } else {
            try {
                freshExecutor.execute(inWaiterContext);
            } catch (RejectedExecutionException e) {
                inWaiterContext.run();
            }
        }
    }

    private static boolean hasThreadElements(PropagatedContext context) {
        for (PropagatedContextElement element : context.getAllElements()) {
            if (element instanceof ThreadPropagatedContextElement<?>) {
                return true;
            }
        }
        return false;
    }
}
