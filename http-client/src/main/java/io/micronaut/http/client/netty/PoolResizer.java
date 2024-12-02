/*
 * Copyright 2017-2022 original authors
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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import org.slf4j.Logger;

import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * This class handles the sizing of a connection pool to conform to the configuration in
 * {@link io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration}.
 * <p>
 * This class consists of various mutator methods (e.g. {@link #addPendingRequest}) that
 * may be called concurrently and in a reentrant fashion (e.g. inside {@link #openNewConnection}).
 * These mutator methods update their respective fields and then mark this class as
 * {@link #dirty()}. The state management logic ensures that {@link #doSomeWork()} is called in a
 * serialized fashion (no concurrency or reentrancy) at least once after each {@link #dirty()}
 * call.
 */
@Internal
abstract class PoolResizer {
    private final Logger log;
    private final HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration;

    private final AtomicReference<WorkState> state = new AtomicReference<>(WorkState.IDLE);

    private final AtomicInteger pendingConnectionCount = new AtomicInteger(0);

    private final Deque<PendingRequest> pendingRequests = new ConcurrentLinkedDeque<>();
    private final List<ResizerConnection> http1Connections = new CopyOnWriteArrayList<>();
    private final List<ResizerConnection> http2Connections = new CopyOnWriteArrayList<>();

    PoolResizer(Logger log, HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration) {
        this.log = log;
        this.connectionPoolConfiguration = connectionPoolConfiguration;
    }

    private void dirty() {
        WorkState before = state.getAndUpdate(ws -> {
            if (ws == WorkState.IDLE) {
                return WorkState.ACTIVE_WITHOUT_PENDING_WORK;
            } else {
                return WorkState.ACTIVE_WITH_PENDING_WORK;
            }
        });
        if (before != WorkState.IDLE) {
            // already in one of the active states, another thread will take care of our changes
            return;
        }
        // we were in idle state, this thread will handle the changes.
        while (true) {
            try {
                doSomeWork();
            } catch (Throwable t) {
                // this is probably an irrecoverable failure, we need to bail immediately, but
                // avoid locking up the state. Another thread might be able to continue work.
                state.set(WorkState.IDLE);
                throw t;
            }

            WorkState endState = state.updateAndGet(ws -> {
                if (ws == WorkState.ACTIVE_WITH_PENDING_WORK) {
                    return WorkState.ACTIVE_WITHOUT_PENDING_WORK;
                } else {
                    return WorkState.IDLE;
                }
            });
            if (endState == WorkState.IDLE) {
                // nothing else to do \o/
                break;
            }
        }
    }

    private void doSomeWork() {
        BlockHint blockedPendingRequests = null;
        while (true) {
            PendingRequest toDispatch = pendingRequests.pollFirst();
            if (toDispatch == null) {
                break;
            }
            boolean dispatched = false;
            for (ResizerConnection c : http2Connections) {
                if (dispatchSafe(c, toDispatch)) {
                    dispatched = true;
                    break;
                }
            }
            if (!dispatched) {
                for (ResizerConnection c : http1Connections) {
                    if (dispatchSafe(c, toDispatch)) {
                        dispatched = true;
                        break;
                    }
                }
            }
            if (!dispatched) {
                pendingRequests.addFirst(toDispatch);
                blockedPendingRequests =
                    BlockHint.combine(blockedPendingRequests, toDispatch.blockHint);
                break;
            }
        }

        // snapshot our fields
        int pendingRequestCount = this.pendingRequests.size();
        int pendingConnectionCount = this.pendingConnectionCount.get();
        int http1ConnectionCount = this.http1Connections.size();
        int http2ConnectionCount = this.http2Connections.size();

        if (pendingRequestCount == 0) {
            // if there are no pending requests, there is nothing to do.
            return;
        }
        int connectionsToOpen = pendingRequestCount - pendingConnectionCount;
        // make sure we won't exceed our config setting for pending connections
        connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxPendingConnections() - pendingConnectionCount);
        // limit the connection count to the protocol-specific settings, but only if that protocol was seen for this pool.
        if (http1ConnectionCount > 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp1Connections() - http1ConnectionCount);
        }
        if (http2ConnectionCount > 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp2Connections() - http2ConnectionCount);
        }

        if (connectionsToOpen > 0) {
            this.pendingConnectionCount.addAndGet(connectionsToOpen);
            for (int i = 0; i < connectionsToOpen; i++) {
                try {
                    openNewConnection(blockedPendingRequests);
                } catch (Exception e) {
                    try {
                        onNewConnectionFailure(e);
                    } catch (Exception f) {
                        log.error("Internal error", f);
                    }
                }
            }
            dirty();
        }
    }

    private boolean dispatchSafe(ResizerConnection connection, PendingRequest toDispatch) {
        try {
            return connection.dispatch(toDispatch);
        } catch (Exception e) {
            try {
                if (!toDispatch.tryCompleteExceptionally(e)) {
                    // this is probably fine, log it anyway
                    log.debug("Failure during connection dispatch operation, but dispatch request was already complete.", e);
                }
            } catch (Exception f) {
                log.error("Internal error", f);
            }
            return true;
        }
    }

    abstract void openNewConnection(@Nullable BlockHint blockedPendingRequests) throws Exception;

    static boolean incrementWithLimit(AtomicInteger variable, int limit) {
        while (true) {
            int old = variable.get();
            if (old >= limit) {
                return false;
            }
            if (variable.compareAndSet(old, old + 1)) {
                return true;
            }
        }
    }

    // can be overridden, so `throws Exception` ensures we handle any errors
    void onNewConnectionFailure(@Nullable Throwable error) throws Exception {
        // todo: implement a circuit breaker here? right now, we just fail one connection in the
        //  subclass implementation, but maybe we should do more.
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onNewConnectionEstablished1(ResizerConnection connection) {
        http1Connections.add(connection);
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onNewConnectionEstablished2(ResizerConnection connection) {
        http2Connections.add(connection);
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onConnectionInactive1(ResizerConnection connection) {
        http1Connections.remove(connection);
        dirty();
    }

    final void onConnectionInactive2(ResizerConnection connection) {
        http2Connections.remove(connection);
        dirty();
    }

    final void addPendingRequest(PendingRequest sink) {
        int maxPendingAcquires = connectionPoolConfiguration.getMaxPendingAcquires();
        if (maxPendingAcquires != Integer.MAX_VALUE && pendingRequests.size() >= maxPendingAcquires) {
            sink.tryCompleteExceptionally(new HttpClientException("Cannot acquire connection, exceeded max pending acquires configuration"));
            return;
        }
        pendingRequests.addLast(sink);
        dirty();
    }

    final PendingRequest pollPendingRequest() {
        PendingRequest req = pendingRequests.pollFirst();
        if (req != null) {
            dirty();
        }
        return req;
    }

    final void markConnectionAvailable() {
        dirty();
    }

    final void forEachConnection(Consumer<ResizerConnection> c) {
        for (ResizerConnection http1Connection : http1Connections) {
            c.accept(http1Connection);
        }
        for (ResizerConnection http2Connection : http2Connections) {
            c.accept(http2Connection);
        }
    }

    private enum WorkState {
        /**
         * There are no pending changes, and nobody is currently executing {@link #doSomeWork()}.
         */
        IDLE,
        /**
         * Someone is currently executing {@link #doSomeWork()}, but there were further changes
         * after {@link #doSomeWork()} was called, so it needs to be called again.
         */
        ACTIVE_WITH_PENDING_WORK,
        /**
         * Someone is currently executing {@link #doSomeWork()}, and there were no other changes
         * since then.
         */
        ACTIVE_WITHOUT_PENDING_WORK,
    }

    abstract static class ResizerConnection {
        /**
         * Attempt to dispatch a stream on this connection.
         *
         * @param sink The pending request that wants to acquire this connection
         * @return {@code true} if the acquisition may succeed (if it fails later, the pending
         * request must be readded), or {@code false} if it fails immediately
         */
        abstract boolean dispatch(PendingRequest sink) throws Exception;
    }

    static final class PendingRequest extends AtomicBoolean {
        final @Nullable BlockHint blockHint;
        private final DelayedExecutionFlow<ConnectionManager.PoolHandle> sink = DelayedExecutionFlow.create();

        PendingRequest(@Nullable BlockHint blockHint) {
            this.blockHint = blockHint;
        }

        ExecutionFlow<ConnectionManager.PoolHandle> flow() {
            return sink;
        }

        // DelayedExecutionFlow does not allow concurrent completes, so this is a simple guard

        boolean tryCompleteExceptionally(Throwable t) {
            if (compareAndSet(false, true)) {
                sink.completeExceptionally(t);
                return true;
            } else {
                return false;
            }
        }

        boolean tryComplete(ConnectionManager.PoolHandle value) {
            if (compareAndSet(false, true)) {
                if (sink.isCancelled()) {
                    return false;
                }
                sink.complete(value);
                return true;
            } else {
                return false;
            }
        }
    }
}
