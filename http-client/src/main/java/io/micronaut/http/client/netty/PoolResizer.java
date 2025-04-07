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
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.PlatformDependent;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

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

    private final Queue<PendingRequest> pendingRequests = PlatformDependent.newMpscQueue();
    private final Queue<Throwable> pendingRequestFailures = PlatformDependent.newMpscQueue();
    private final ConnectionList http1Connections = new ConnectionList();
    private final ConnectionList http2Connections = new ConnectionList();

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

    private Iterable<ResizerConnection> sort(PendingRequest request, ConnectionList connections) {
        ConnectionListState state = connections.state;
        HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality locality = connectionPoolConfiguration.getConnectionLocality();
        if (locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.PREFERRED) {
            EventExecutor[] options = state.connections.keySet().toArray(new EventExecutor[0]);
            if (request.localityHelper != null) {
                int preferredIndex = Arrays.asList(options).indexOf(request.localityHelper.loop());
                if (preferredIndex > 0) {
                    EventExecutor swap = options[0];
                    options[0] = options[preferredIndex];
                    options[preferredIndex] = swap;
                }
            }
            return () -> new ConnectionListIterator(state, Arrays.asList(options).iterator());
        } else if (locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_IF_SAME_GROUP ||
            locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS) {
            EventExecutor loop = request.localityHelper == null ? null : request.localityHelper.loop();
            List<ResizerConnection> list = state.connections.get(loop);
            if (list != null) {
                return list;
            }
            if (locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS ||
                containsThread(request.localityHelper)) {
                return Collections.emptyList();
            }

            // escape hatch: in ENFORCED_IF_SAME_GROUP, we can use any connection if the
            // requesting thread is *not* in the same event loop group.
        }
        return () -> new ConnectionListIterator(state, state.connections.keySet().iterator());
    }

    private void doSomeWork() {
        BlockHint blockedPendingRequests = null;
        while (true) {
            PendingRequest toDispatch = pendingRequests.peek();
            if (toDispatch == null) {
                break;
            }
            Throwable failure = pendingRequestFailures.poll();
            boolean dispatched = false;
            if (failure != null) {
                toDispatch.tryCompleteExceptionally(failure);
                dispatched = true;
            }
            if (!dispatched) {
                for (ResizerConnection c : sort(toDispatch, http2Connections)) {
                    if (dispatchSafe(c, toDispatch)) {
                        dispatched = true;
                        break;
                    }
                }
            }
            if (!dispatched) {
                for (ResizerConnection c : sort(toDispatch, http1Connections)) {
                    if (dispatchSafe(c, toDispatch)) {
                        dispatched = true;
                        break;
                    }
                }
            }
            if (dispatched) {
                pendingRequests.poll();
            } else {
                blockedPendingRequests =
                    BlockHint.combine(blockedPendingRequests, toDispatch.blockHint);
                break;
            }
        }

        // snapshot our fields
        int pendingRequestCount = this.pendingRequests.size();
        int pendingConnectionCount = this.pendingConnectionCount.get();
        int http1ConnectionCount = this.http1Connections.state.connectionCount;
        int http2ConnectionCount = this.http2Connections.state.connectionCount;

        if (pendingRequestCount == 0) {
            // if there are no pending requests, there is nothing to do.
            return;
        }
        int connectionsToOpen = pendingRequestCount - pendingConnectionCount;
        // make sure we won't exceed our config setting for pending connections
        connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxPendingConnections() - pendingConnectionCount);
        // limit the connection count to the protocol-specific settings, but only if that protocol was seen for this pool.
        // if there's no connections at all, conservatively use the lesser of both limits
        if (http1ConnectionCount > 0 || http2ConnectionCount == 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp1Connections() - http1ConnectionCount);
        }
        if (http2ConnectionCount > 0 || http1ConnectionCount == 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp2Connections() - http2ConnectionCount);
        }

        if (connectionsToOpen > 0) {
            Iterator<PendingRequest> pendingRequestIterator = this.pendingRequests.iterator();
            if (!pendingRequestIterator.hasNext()) {
                // no pending requests now
                return;
            }
            // we need to pass a preferred thread to openNewConnection. This is the best we can do
            LocalityHelper preferredThread = pendingRequestIterator.next().localityHelper;
            this.pendingConnectionCount.addAndGet(connectionsToOpen);
            for (int i = 0; i < connectionsToOpen; i++) {
                try {
                    openNewConnection(blockedPendingRequests, preferredThread);
                } catch (Exception e) {
                    try {
                        onNewConnectionFailure(e);
                    } catch (Exception f) {
                        log.error("Internal error", f);
                    }
                }
                if (pendingRequestIterator.hasNext()) {
                    preferredThread = pendingRequestIterator.next().localityHelper;
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

    abstract void openNewConnection(@Nullable BlockHint blockedPendingRequests, @Nullable LocalityHelper localityHelper) throws Exception;

    abstract boolean containsThread(@Nullable LocalityHelper localityHelper);

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
        pendingRequests.add(sink);
        dirty();
    }

    final void failOnePendingRequest(Throwable t) {
        pendingRequestFailures.add(t);
        dirty();
    }

    final void markConnectionAvailable() {
        dirty();
    }

    final void forEachConnection(Consumer<ResizerConnection> c) {
        http1Connections.forEach(c);
        http2Connections.forEach(c);
    }

    /**
     * This is a concurrent list implementation that is similar to
     * {@link java.util.concurrent.CopyOnWriteArrayList}, but with some extra optimization for
     * {@link #doSomeWork()}.
     */
    private static final class ConnectionList {
        private final Lock lock = new ReentrantLock();

        private volatile ConnectionListState state = new ConnectionListState(new HashMap<>(), 0);

        void forEach(Consumer<ResizerConnection> c) {
            Map<EventExecutor, List<ResizerConnection>> items;
            lock.lock();
            try {
                items = state.connections;
            } finally {
                lock.unlock();
            }
            for (List<ResizerConnection> list : items.values()) {
                for (ResizerConnection connection : list) {
                    c.accept(connection);
                }
            }
        }

        void add(ResizerConnection connection) {
            lock.lock();
            try {
                state = state.add(connection);
            } finally {
                lock.unlock();
            }
        }

        void remove(ResizerConnection connection) {
            lock.lock();
            try {
                state = state.remove(connection);
            } finally {
                lock.unlock();
            }
        }
    }

    private record ConnectionListState(Map<EventExecutor, List<ResizerConnection>> connections,
                                       int connectionCount) {
        private ConnectionListState update(EventExecutor loop, Function<List<ResizerConnection>, List<ResizerConnection>> function) {
            Map<EventExecutor, List<ResizerConnection>> newMap = new HashMap<>(this.connections);
            List<ResizerConnection> oldList = newMap.get(loop);
            List<ResizerConnection> newList = function.apply(oldList);
            int newCount = connectionCount;
            if (oldList != null) {
                newCount -= oldList.size();
            }
            if (newList == null) {
                newMap.remove(loop);
            } else {
                newMap.put(loop, newList);
                newCount += newList.size();
            }
            return new ConnectionListState(newMap, newCount);
        }

        ConnectionListState add(ResizerConnection connection) {
            return update(connection.eventLoop, oldList -> {
                List<ResizerConnection> newList;
                if (oldList == null) {
                    newList = new ArrayList<>(1);
                    newList.add(connection);
                } else {
                    newList = new ArrayList<>(oldList.size() + 1);
                    newList.add(connection);
                    newList.addAll(oldList);
                }
                return newList;
            });
        }

        ConnectionListState remove(ResizerConnection connection) {
            return update(connection.eventLoop, oldList -> {
                if (oldList == null) {
                    return null;
                } else {
                    int i = oldList.indexOf(connection);
                    if (i == -1) {
                        return oldList;
                    }
                    List<ResizerConnection> newList = new ArrayList<>(oldList);
                    newList.remove(i);
                    return newList;
                }
            });
        }
    }

    private static final class ConnectionListIterator implements Iterator<ResizerConnection> {
        final ConnectionListState state;
        final Iterator<EventExecutor> loops;
        Iterator<ResizerConnection> loopIterator;

        ConnectionListIterator(ConnectionListState state, Iterator<EventExecutor> loops) {
            this.state = state;
            this.loops = loops;
        }

        @Override
        public boolean hasNext() {
            while (loopIterator == null || !loopIterator.hasNext()) {
                if (!loops.hasNext()) {
                    return false;
                }
                EventExecutor loop = loops.next();
                List<ResizerConnection> list = state.connections.get(loop);
                if (list == null) {
                    loopIterator = null;
                } else {
                    loopIterator = list.iterator();
                }
            }
            return true;
        }

        @Override
        public ResizerConnection next() {
            return loopIterator.next();
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
        private final EventExecutor eventLoop;

        ResizerConnection(EventExecutor eventLoop) {
            this.eventLoop = eventLoop;
        }

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
        final @Nullable LocalityHelper localityHelper;
        final @Nullable BlockHint blockHint;
        private final DelayedExecutionFlow<ConnectionManager.PoolHandle> sink = DelayedExecutionFlow.create();

        PendingRequest(@Nullable BlockHint blockHint, @Nullable LocalityHelper localityHelper) {
            this.blockHint = blockHint;
            this.localityHelper = localityHelper;
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
