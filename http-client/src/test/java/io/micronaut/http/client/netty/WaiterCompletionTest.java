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
        PropagatedContext waiter = PropagatedContext.empty();
        CompletableFuture<String> seen = new CompletableFuture<>();
        ExecutorService fresh = Executors.newSingleThreadExecutor();
        try {
            PropagatedContext.empty().plus(new Mdc("bob")).propagate(() ->
                WaiterCompletion.complete(waiter, fresh, () -> seen.complete(String.valueOf(USER.get()))));
            assertEquals("null", seen.get(5, TimeUnit.SECONDS));
        } finally {
            fresh.shutdown();
        }
        assertNull(USER.get());
    }

    @Test
    void waiterRunsWithItsOwnThreadState() throws Exception {
        PropagatedContext waiter = PropagatedContext.empty().plus(new Mdc("alice"));
        CompletableFuture<String> seen = new CompletableFuture<>();
        ExecutorService fresh = Executors.newSingleThreadExecutor();
        try {
            PropagatedContext.empty().plus(new Mdc("bob")).propagate(() ->
                WaiterCompletion.complete(waiter, fresh, () -> seen.complete(USER.get())));
            assertEquals("alice", seen.get(5, TimeUnit.SECONDS));
            // without a foreign thread-bound state the completion runs inline
            CompletableFuture<Thread> thread = new CompletableFuture<>();
            WaiterCompletion.complete(waiter, fresh, () -> thread.complete(Thread.currentThread()));
            assertEquals(Thread.currentThread(), thread.getNow(null));
        } finally {
            fresh.shutdown();
        }
    }

    @Test
    void aRejectedFreshTaskStillCompletesTheWaiter() {
        PropagatedContext waiter = PropagatedContext.empty().plus(new Mdc("alice"));
        CompletableFuture<String> seen = new CompletableFuture<>();
        ExecutorService fresh = Executors.newSingleThreadExecutor();
        fresh.shutdown();
        PropagatedContext.empty().plus(new Mdc("bob")).propagate(() ->
            WaiterCompletion.complete(waiter, fresh, () -> seen.complete(USER.get())));
        assertEquals("alice", seen.getNow(null));
    }

    @Test
    void withoutAFreshExecutorTheCompletionDoesNotRunOnTheGlobalEventExecutor() throws Exception {
        PropagatedContext waiter = PropagatedContext.empty();
        CompletableFuture<Boolean> seen = new CompletableFuture<>();
        PropagatedContext.empty().plus(new Mdc("bob")).propagate(() ->
            WaiterCompletion.complete(waiter, null, () -> seen.complete(io.netty.util.concurrent.GlobalEventExecutor.INSTANCE.inEventLoop())));
        assertEquals(false, seen.get(5, TimeUnit.SECONDS));
    }
}
