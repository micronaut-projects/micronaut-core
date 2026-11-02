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

import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.core.propagation.PropagatedContextElement;
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import io.netty.util.concurrent.EventExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Runs the continuation of a pooled request that waited for a connection. The connection is
 * usually handed over by another exchange, while its context is current. The continuation
 * propagates the context of the waiting caller, which does not undo the thread-bound state (e.g.
 * MDC) of that other exchange, so in that case it runs from a fresh task instead.
 */
final class WaiterCompletion {
    private WaiterCompletion() {
    }

    /**
     * Run the completion without the thread-bound state of the current context.
     *
     * @param freshExecutor An executor whose tasks start without thread-bound state, e.g. an
     *                      event loop of the client, or {@code null} if none is known
     * @param completion    The completion
     */
    static void complete(@Nullable Executor freshExecutor, Runnable completion) {
        complete(freshExecutor, null, completion);
    }

    /**
     * Run the completion without the thread-bound state of the current context.
     *
     * @param freshExecutor An executor whose tasks start without thread-bound state, e.g. an
     *                      event loop of the client, or {@code null} if none is known
     * @param blockHint     The threads blocked waiting for the completion, or {@code null}. A
     *                      task queued on an executor running on one of these threads would never
     *                      run, so the completion runs inline instead
     * @param completion    The completion
     */
    static void complete(@Nullable Executor freshExecutor, @Nullable BlockHint blockHint, Runnable completion) {
        PropagatedContext current = PropagatedContext.find().orElse(null);
        if (freshExecutor == null || current == null || !hasThreadElements(current)
            || blockHint != null && freshExecutor instanceof EventExecutor loop && blockHint.blocks(loop)) {
            completion.run();
            return;
        }
        try {
            freshExecutor.execute(completion);
        } catch (RejectedExecutionException e) {
            // e.g. a loop that shuts down, the waiter must not be stranded
            completion.run();
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
