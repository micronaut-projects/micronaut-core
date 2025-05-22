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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

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
final class Pool40 implements Pool {
    private final Pool.Listener listener;
    private final Logger log;
    private final HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration;
    private final EventLoopGroup group;

    private final AtomicReference<WorkState> state = new AtomicReference<>(WorkState.IDLE);

    private final AtomicInteger pendingConnectionCount = new AtomicInteger(0);

    private final Deque<PendingRequest> pendingRequests = new ConcurrentLinkedDeque<>();
    private final ConnectionList http1Connections = new ConnectionList();
    private final ConnectionList http2Connections = new ConnectionList();

    Pool40(Pool.Listener listener, Logger log, HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration, EventLoopGroup group) {
        this.listener = listener;
        this.log = log;
        this.connectionPoolConfiguration = connectionPoolConfiguration;
        this.group = group;
    }

    @Override
    public Pool.PendingRequest createPendingRequest(@Nullable BlockHint blockHint) {
        return new PendingRequest(blockHint);
    }

    @Override
    public Http1PoolEntry createHttp1PoolEntry(@NonNull EventLoop eventLoop, @NonNull ResizerConnection connection) {
        return new Http1(eventLoop, connection);
    }

    @Override
    public Http2PoolEntry createHttp2PoolEntry(@NonNull EventLoop eventLoop, @NonNull ResizerConnection connection) {
        return new Http2(eventLoop, connection);
    }

    @Override
    public void onNewConnectionFailure(@NonNull EventLoop eventLoop, @Nullable Throwable error) throws Exception {
        onNewConnectionFailure(error);
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

    private PoolEntry[] sort(PendingRequest request, ConnectionList connections) {
        PoolEntry[] items = connections.unsafeItems;
        if (items.length == 0) {
            return items;
        }
        HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality locality = connectionPoolConfiguration.getConnectionLocality();
        if (locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.PREFERRED) {
            // this is a very simple selection sort. There's usually only one or two connections on
            // the same thread
            int copies = 0;
            for (int i = 1; i < items.length; i++) {
                PoolEntry connection = items[i];
                if (connection.eventLoop.inEventLoop(request.requestingThread)) {
                    // place that connection at the front
                    System.arraycopy(items, 0, items, 1, i);
                    items[0] = connection;
                    if (copies++ > 4) {
                        // prevent n² worst-case performance
                        break;
                    }
                }
            }
        } else if (locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_IF_SAME_GROUP ||
            locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS) {

            List<PoolEntry> options = new ArrayList<>();
            for (PoolEntry item : items) {
                if (item.eventLoop.inEventLoop(request.requestingThread)) {
                    options.add(item);
                }
            }
            if (!options.isEmpty() ||
                locality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS ||
                containsThread(request.requestingThread)) {

                return options.toArray(new PoolEntry[0]);
            }
            // escape hatch: in ENFORCED_IF_SAME_GROUP, we can use any connection if the
            // requesting thread is *not* in the same event loop group.
        }
        return items;
    }

    private void doSomeWork() {
        BlockHint blockedPendingRequests = null;
        while (true) {
            PendingRequest toDispatch = pendingRequests.pollFirst();
            if (toDispatch == null) {
                break;
            }
            boolean dispatched = false;
            for (PoolEntry c : sort(toDispatch, http2Connections)) {
                if (dispatchSafe(c, toDispatch)) {
                    dispatched = true;
                    break;
                }
            }
            if (!dispatched) {
                for (PoolEntry c : sort(toDispatch, http1Connections)) {
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
        int http1ConnectionCount = this.http1Connections.unsafeItems.length;
        int http2ConnectionCount = this.http2Connections.unsafeItems.length;

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
            Thread preferredThread = pendingRequestIterator.next().requestingThread;
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
                    preferredThread = pendingRequestIterator.next().requestingThread;
                }
            }
            dirty();
        }
    }

    private boolean dispatchSafe(PoolEntry connection, PendingRequest toDispatch) {
        try {
            BlockHint blockHint = toDispatch.blockHint;
            if (blockHint != null && blockHint.blocks(connection.eventLoop)) {
                toDispatch.tryCompleteExceptionally(BlockHint.createException());
                return true;
            }
            if (!connection.tryEarmarkForRequest()) {
                return false;
            }
            connection.connection.dispatch(toDispatch);
            return true;
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

    void openNewConnection(@Nullable BlockHint blockedPendingRequests, @NonNull Thread requestingThread) throws Exception {
        EventLoop target = null;
        for (EventExecutor executor : group) {
            if (executor.inEventLoop(requestingThread)) {
                target = (EventLoop) executor;
                break;
            }
        }
        if (target == null) {
            target = group.next();
        }
        if (blockedPendingRequests != null && blockedPendingRequests.blocks(target)) {
            onNewConnectionFailure(BlockHint.createException());
            return;
        }
        listener.openNewConnection(target);
    }

    boolean containsThread(@NonNull Thread thread) {
        for (EventExecutor executor : group) {
            if (executor.inEventLoop(thread)) {
                return true;
            }
        }
        return false;
    }

    void onNewConnectionFailure(@Nullable Throwable error) throws Exception {
        // todo: implement a circuit breaker here? right now, we just fail one connection in the
        //  subclass implementation, but maybe we should do more.
        pendingConnectionCount.decrementAndGet();
        dirty();

        PendingRequest pending = pollPendingRequest();
        if (pending != null) {
            if (pending.tryCompleteExceptionally(listener.wrapError(error))) {
                return;
            }
        }
        log.error("Failed to connect to remote", error);
    }

    void onNewConnectionEstablished1(Http1 connection) {
        http1Connections.add(connection);
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    void onNewConnectionEstablished2(Http2 connection) {
        http2Connections.add(connection);
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    void onConnectionInactive1(Http1 connection) {
        http1Connections.remove(connection);
        dirty();
    }

    void onConnectionInactive2(Http2 connection) {
        http2Connections.remove(connection);
        dirty();
    }

    void addPendingRequest(PendingRequest sink) {
        int maxPendingAcquires = connectionPoolConfiguration.getMaxPendingAcquires();
        if (maxPendingAcquires != Integer.MAX_VALUE && pendingRequests.size() >= maxPendingAcquires) {
            sink.tryCompleteExceptionally(new HttpClientException("Cannot acquire connection, exceeded max pending acquires configuration"));
            return;
        }
        pendingRequests.addLast(sink);
        dirty();
    }

    PendingRequest pollPendingRequest() {
        PendingRequest req = pendingRequests.pollFirst();
        if (req != null) {
            dirty();
        }
        return req;
    }

    void markConnectionAvailable() {
        dirty();
    }

    @Override
    public void forEachConnection(Consumer<Pool.ResizerConnection> c) {
        http1Connections.forEach(c);
        http2Connections.forEach(c);
    }

    /**
     * This is a concurrent list implementation that is similar to
     * {@link java.util.concurrent.CopyOnWriteArrayList}, but with some extra optimization for
     * {@link #doSomeWork()}.
     */
    private static final class ConnectionList {
        private static final PoolEntry[] EMPTY = new PoolEntry[0];

        private final Lock lock = new ReentrantLock();

        /**
         * Copy of {@link #safeItems} <i>only</i> for use in {@link #doSomeWork()}, without lock.
         * {@link #doSomeWork()} may shuffle and reorder this array in-place as needed.
         */
        private volatile PoolEntry[] unsafeItems = EMPTY;
        /**
         * Items for concurrent access, guarded by {@link #lock}.
         */
        private PoolEntry[] safeItems = EMPTY;

        void forEach(Consumer<ResizerConnection> c) {
            PoolEntry[] items;
            lock.lock();
            try {
                items = safeItems;
            } finally {
                lock.unlock();
            }
            for (PoolEntry item : items) {
                c.accept(item.connection);
            }
        }

        void add(PoolEntry connection) {
            lock.lock();
            try {
                PoolEntry[] prev = safeItems;
                PoolEntry[] next = Arrays.copyOf(prev, prev.length + 1);
                next[prev.length] = connection;
                this.safeItems = next;
                this.unsafeItems = next.clone();
            } finally {
                lock.unlock();
            }
        }

        void remove(PoolEntry connection) {
            lock.lock();
            try {
                PoolEntry[] prev = safeItems;
                int index = Arrays.asList(prev).indexOf(connection);
                if (index == -1) {
                    return;
                }
                PoolEntry[] next = Arrays.copyOf(prev, prev.length - 1);
                System.arraycopy(prev, index + 1, next, index, prev.length - index - 1);

                this.safeItems = next;
                this.unsafeItems = next.clone();
            } finally {
                lock.unlock();
            }
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

    final class PendingRequest extends AtomicBoolean implements Pool.PendingRequest {
        final Thread requestingThread = Thread.currentThread();
        final @Nullable BlockHint blockHint;
        private final DelayedExecutionFlow<ConnectionManager.PoolHandle> sink = DelayedExecutionFlow.create();

        PendingRequest(@Nullable BlockHint blockHint) {
            this.blockHint = blockHint;
        }

        @Override
        public ExecutionFlow<ConnectionManager.PoolHandle> flow() {
            return sink;
        }

        @Override
        public void dispatch() {
            addPendingRequest(this);
        }

        @Override
        public void redispatch() {
            dispatch();
        }

        @Override
        public @Nullable EventExecutor likelyEventLoop() {
            return null;
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

        @Override
        public boolean tryComplete(ConnectionManager.PoolHandle value) {
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

    private abstract static sealed class PoolEntry {
        final EventLoop eventLoop;
        final ResizerConnection connection;

        private PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            this.eventLoop = eventLoop;
            this.connection = connection;
        }

        abstract boolean tryEarmarkForRequest();
    }

    final class Http1 extends PoolEntry implements Http1PoolEntry {
        private final AtomicBoolean earmarkedOrLive = new AtomicBoolean(false);

        public Http1(EventLoop eventLoop, @NonNull ResizerConnection connection) {
            super(eventLoop, connection);
        }

        @Override
        public void onConnectionEstablished() {
            onNewConnectionEstablished1(this);
        }

        @Override
        public void onConnectionInactive() {
            onConnectionInactive1(this);
        }

        @Override
        boolean tryEarmarkForRequest() {
            return earmarkedOrLive.compareAndSet(false, true);
        }

        @Override
        public void markAvailable() {
            earmarkedOrLive.set(false);
            markConnectionAvailable();
        }

        @Override
        public void markUnavailable() {
            earmarkedOrLive.set(true);
        }
    }

    final class Http2 extends PoolEntry implements Http2PoolEntry {
        private final AtomicInteger earmarkedOrLiveRequests = new AtomicInteger(0);
        private int maxStreamCount;

        public Http2(EventLoop eventLoop, @NonNull ResizerConnection connection) {
            super(eventLoop, connection);
        }

        @Override
        boolean tryEarmarkForRequest() {
            IntUnaryOperator upd = old -> {
                if (old >= Math.min(connectionPoolConfiguration.getMaxConcurrentRequestsPerHttp2Connection(), maxStreamCount)) {
                    return old;
                } else {
                    return old + 1;
                }
            };
            int old = earmarkedOrLiveRequests.updateAndGet(upd);
            return upd.applyAsInt(old) != old;
        }

        @Override
        public void onConnectionEstablished(int maxStreamCount) {
            this.maxStreamCount = maxStreamCount;
            onNewConnectionEstablished2(this);
        }

        @Override
        public void onConnectionInactive() {
            onConnectionInactive2(this);
        }

        @Override
        public void markAvailable() {
            earmarkedOrLiveRequests.decrementAndGet();
            markConnectionAvailable();
        }

        @Override
        public void markUnavailable() {
            earmarkedOrLiveRequests.set(Integer.MAX_VALUE);
        }
    }
}
