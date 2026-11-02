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
import io.micronaut.core.propagation.ThreadPropagatedContextElement;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The continuation of a pooled request waiting for a connection must not see the thread-bound
 * state of the exchange that hands the connection over.
 */
class WaiterCompletionTest {
    static final ThreadLocal<String> USER = new ThreadLocal<>();

    record Mdc(String user) implements ThreadPropagatedContextElement<String> {
        @Override
        public String updateThreadContext() {
            String old = USER.get();
            USER.set(user);
            return old;
        }

        @Override
        public void restoreThreadContext(String oldState) {
            if (oldState == null) {
                USER.remove();
            } else {
                USER.set(oldState);
            }
        }
    }

    @Test
    void waiterDoesNotSeeTheReleasingExchangeThreadState() throws Exception {
        CompletableFuture<String> seen = new CompletableFuture<>();
        ExecutorService fresh = Executors.newSingleThreadExecutor();
        try {
            try (PropagatedContext.Scope ignored = PropagatedContext.empty().plus(new Mdc("bob")).propagate()) {
                // the waiter propagates its own (empty) context, as ReactivePropagation does
                WaiterCompletion.complete(fresh, () -> {
                    try (PropagatedContext.Scope ignored2 = PropagatedContext.empty().propagate()) {
                        seen.complete(String.valueOf(USER.get()));
                    }
                });
            }
            assertEquals("null", seen.get(5, TimeUnit.SECONDS));
        } finally {
            fresh.shutdown();
        }
        assertNull(USER.get());
    }

    @Test
    void withoutThreadStateTheCompletionRunsInline() {
        CompletableFuture<Thread> thread = new CompletableFuture<>();
        WaiterCompletion.complete(r -> {
            throw new AssertionError("not inline");
        }, () -> thread.complete(Thread.currentThread()));
        assertEquals(Thread.currentThread(), thread.getNow(null));
    }

    @Test
    void aRejectedTaskRunsInline() {
        CompletableFuture<Thread> thread = new CompletableFuture<>();
        try (PropagatedContext.Scope ignored = PropagatedContext.empty().plus(new Mdc("bob")).propagate()) {
            WaiterCompletion.complete(r -> {
                throw new RejectedExecutionException();
            }, () -> thread.complete(Thread.currentThread()));
        }
        assertEquals(Thread.currentThread(), thread.getNow(null));
    }
}
