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
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ThreadExecutorMap;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
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

    private final Map<EventExecutor, LocalPoolPair> localPoolsByLoop;
    final List<LocalPoolPair> localPools;

    @Nullable
    private final LongAdder globalPending;
    private final AtomicReference<GlobalStats> globalStats = new AtomicReference<>(GlobalStats.EMPTY);
    private final Queue<PendingRequest> globalPendingRequests = new LinkedBlockingQueue<>();

    PoolResizer(Logger log, HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration, Iterable<? extends EventExecutor> group) {
        this.log = log;
        this.connectionPoolConfiguration = connectionPoolConfiguration;
        this.localPoolsByLoop = new LinkedHashMap<>();
        for (EventExecutor loop : group) {
            localPoolsByLoop.put(loop, new LocalPoolPair(loop, localPoolsByLoop.size()));
        }
        this.localPools = List.copyOf(localPoolsByLoop.values());
        if (connectionPoolConfiguration.getMaxPendingAcquires() != Integer.MAX_VALUE) {
            globalPending = new LongAdder();
        } else {
            globalPending = null;
        }
    }

    private void dispatchSafe(ResizerConnection connection, PendingRequest toDispatch) {
        try {
            connection.dispatch(toDispatch);
        } catch (Exception e) {
            try {
                if (!toDispatch.tryCompleteExceptionally(e)) {
                    // this is probably fine, log it anyway
                    log.debug("Failure during connection dispatch operation, but dispatch request was already complete.", e);
                }
            } catch (Exception f) {
                log.error("Internal error", f);
            }
        }
    }

    abstract void openNewConnection(@NonNull EventLoop eventLoop) throws Exception;

    // can be overridden, so `throws Exception` ensures we handle any errors
    void onNewConnectionFailure(@NonNull EventLoop eventLoop, @Nullable Throwable error) throws Exception {
        // todo: implement a circuit breaker here? right now, we just fail one connection in the
        //  subclass implementation, but maybe we should do more.
        LocalPoolPair poolPair = localPoolsByLoop.get(eventLoop);
        assert poolPair != null;
        poolPair.onNewConnectionFailure(error);
    }

    final void forEachConnection(Consumer<ResizerConnection> c) {
        for (LocalPoolPair localPool : localPools) {
            localPool.http1.connections.forEach(e -> c.accept(e.connection));
            localPool.http2.connections.forEach(e -> c.accept(e.connection));
        }
    }

    @Nullable
    LocalPoolPair pickPreferredPool() {
        LocalPoolPair poolPair = null;
        var configLocality = connectionPoolConfiguration.getConnectionLocality();
        if (configLocality != HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.IGNORE) {

            if (!PrivateLoomSupport.isSupported() || !LoomSupport.isVirtual(Thread.currentThread())) {
                EventExecutor currentExecutor = ThreadExecutorMap.currentExecutor();
                if (currentExecutor == null) {
                    for (LocalPoolPair pool : localPools) {
                        if (pool.loop.inEventLoop()) {
                            poolPair = pool;
                            break;
                        }
                    }
                } else {
                    poolPair = localPoolsByLoop.get(currentExecutor);
                }
            } else {
                Thread carrier = PrivateLoomSupport.getCarrierThread(Thread.currentThread());
                if (carrier != null) {
                    for (LocalPoolPair pool : localPools) {
                        if (pool.loop.inEventLoop(carrier)) {
                            poolPair = pool;
                            break;
                        }
                    }
                }
            }
            if (poolPair == null && configLocality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS) {
                throw new HttpClientException("Attempted to open a HTTP connection from thread " +
                    Thread.currentThread() + " which is not part of the client event loop group, but configured the pool in locality mode ENFORCED_ALWAYS, which disallows " +
                    "requesting from outside this group");
            }
        }
        return poolPair;
    }

    private boolean openConnectionStep1() {
        while (true) {
            GlobalStats oldStats = globalStats.get();
            if (limitsHit(oldStats)) {
                // just add to the pending request queue
                return false;
            }
            if (!globalStats.compareAndSet(oldStats, oldStats.addPendingConnectionCount(1))) {
                continue;
            }
            return true;
        }
    }

    private boolean limitsHit(GlobalStats oldStats) {
        return oldStats.pendingConnectionCount >= connectionPoolConfiguration.getMaxPendingConnections() ||
            // limit the connection count to the protocol-specific settings, but only if that protocol was seen for this pool.
            // if there's no connections at all, conservatively use the lesser of both limits
            (oldStats.seenHttp1 && oldStats.http1ConnectionCount + oldStats.pendingConnectionCount >= connectionPoolConfiguration.getMaxConcurrentHttp1Connections()) ||
            (oldStats.seenHttp2 && oldStats.http2ConnectionCount + oldStats.pendingConnectionCount >= connectionPoolConfiguration.getMaxConcurrentHttp2Connections()) ||
            (!oldStats.seenHttp1 && !oldStats.seenHttp2 && (
                oldStats.pendingConnectionCount >= connectionPoolConfiguration.getMaxConcurrentHttp1Connections() ||
                    oldStats.pendingConnectionCount >= connectionPoolConfiguration.getMaxConcurrentHttp2Connections()
            ));
    }

    private void openGlobalConnectionIfNecessary() {
        while (true) {
            if (globalPendingRequests.isEmpty()) {
                // best-effort check
                break;
            }

            if (!openConnectionStep1()) {
                return;
            }
            PendingRequest request = globalPendingRequests.poll();
            LocalPoolPair pool;
            if (request == null || request.preferredPool == null) {
                pool = localPools.get(ThreadLocalRandom.current().nextInt(localPools.size()));
            } else {
                pool = request.preferredPool;
            }
            pool.loop.execute(() -> {
                pool.openConnectionStep2();

                if (request != null) {
                    request.destPool = pool;
                    pool.addLocalPendingRequest(request);
                }

                pool.openConnectionStep3();
            });
            if (request == null) {
                break;
            }
        }

        if (!limitsHit(globalStats.get())) {
            for (LocalPoolPair pool : RandomOffsetIterator.iterable(localPools)) {
                if (pool.needPendingConnection) {
                    pool.loop.execute(pool::openLocalConnectionIfNecessary);
                }
            }
        }
    }

    private static final class RandomOffsetIterator<E> implements Iterator<E> {
        final List<E> source;
        final int start;
        int i;

        private RandomOffsetIterator(List<E> source) {
            this.source = source;
            this.start = ThreadLocalRandom.current().nextInt(source.size());
            this.i = start;
        }

        static <E> Iterable<E> iterable(List<E> source) {
            return () -> new RandomOffsetIterator<>(source);
        }

        @Override
        public boolean hasNext() {
            return i != -1;
        }

        @Override
        public E next() {
            int pos = i;
            if (pos == -1) {
                throw new NoSuchElementException();
            }
            int next = pos + 1;
            if (next == source.size()) {
                next = 0;
            }
            if (next == start) {
                next = -1;
            }
            i = next;
            return source.get(pos);
        }
    }

    abstract static class ResizerConnection {
        /**
         * Dispatch a stream on this connection.
         *
         * @param sink The pending request that wants to acquire this connection
         */
        abstract void dispatch(PendingRequest sink) throws Exception;
    }

    final class LocalPoolPair {
        final int index;
        final EventExecutor loop;
        final LocalPool<Http1PoolEntry> http1;
        final LocalPool<Http2PoolEntry> http2;
        int localPendingConnections = 0;
        final AtomicBoolean dispatchPendingRequestsQueued = new AtomicBoolean(false);

        final Queue<PendingRequest> localPendingRequests = new ArrayDeque<>();
        volatile boolean needPendingConnection = false;

        LocalPoolPair(EventExecutor loop, int index) {
            this.index = index;
            this.loop = loop;
            http1 = new LocalPool<>();
            http2 = new LocalPool<>();
        }

        void notifyGlobalPendingRequestQueued() {
            if (!dispatchPendingRequestsQueued.compareAndSet(false, true)) {
                return;
            }
            loop.execute(() -> {
                dispatchPendingRequestsQueued.set(false);
                dispatchPendingRequests();
            });
        }

        @Nullable
        PoolEntry findAvailablePoolEntry() {
            PoolEntry http2 = this.http2.peekAvailable();
            if (http2 != null) {
                return http2;
            }
            PoolEntry http1 = this.http1.peekAvailable();
            if (http1 != null) {
                return http1;
            }
            return null;
        }

        private void addLocalPendingRequest(PendingRequest request) {
            localPendingRequests.add(request);
            needPendingConnection = true;
        }

        @Nullable
        private PendingRequest pollLocalPendingRequest() {
            return localPendingRequests.poll();
        }

        void dispatchPendingRequests() {
            while (!localPendingRequests.isEmpty()) {
                PoolEntry poolEntry = findAvailablePoolEntry();
                if (poolEntry == null) {
                    return;
                }
                PendingRequest request = pollLocalPendingRequest();
                assert request != null;
                request.dispatchTo(poolEntry);
            }
            if (globalPendingRequests.isEmpty()) {
                return;
            }
            while (true) {
                PoolEntry poolEntry = findAvailablePoolEntry();
                if (poolEntry == null) {
                    return;
                }
                PendingRequest request = globalPendingRequests.poll();
                if (request == null) {
                    return;
                }
                request.dispatchTo(poolEntry);
            }
        }

        void openConnectionStep2() {
            localPendingConnections++;
            needPendingConnection = localPendingRequests.size() < localPendingConnections;
        }

        void openConnectionStep3() {
            try {
                openNewConnection((EventLoop) loop);
            } catch (Exception e) {
                onNewConnectionFailure(e);
            }
        }

        void openLocalConnectionIfNecessary() {
            assert loop.inEventLoop();
            while (localPendingRequests.size() > localPendingConnections) {
                if (!openConnectionStep1()) {
                    break;
                }
                openConnectionStep3();
            }
        }

        void onNewConnectionFailure(Throwable error) {
            assert loop.inEventLoop();
            globalStats.updateAndGet(s -> s.addPendingConnectionCount(-1)); // TODO: is this called for websockets?
            localPendingConnections--;

            PendingRequest local = localPendingRequests.poll();
            if (local != null) {
                local.tryCompleteExceptionally(error);
            } else {
                PendingRequest global = globalPendingRequests.poll();
                if (global != null) {
                    global.tryCompleteExceptionally(error);
                } else {
                    log.error("Failed to connect to remote", error);
                }
            }
            openLocalConnectionIfNecessary();
            openGlobalConnectionIfNecessary();
        }

        @Override
        public String toString() {
            String s;
            if (loop instanceof SingleThreadIoEventLoop l) {
                s = l.threadProperties().name();
            } else {
                s = loop.toString();
            }
            return "Pool[" + s + "]";
        }

        void check() {
            // TODO
            log.info("Connection count: {}", http1.connections.size());
            int i = 0;
            PoolEntry e = http1.firstAvailable;
            while (e != null) {
                i++;
                e = e.nextAvailable;
            }
            log.info("Available count: {}", http1.connections.size());
        }
    }

    private final class LocalPool<E extends PoolEntry> {
        final Set<E> connections = ConcurrentHashMap.newKeySet();

        volatile E firstAvailable;
        E lastAvailable;

        LocalPool() {
        }

        @Nullable
        PoolEntry peekAvailable() {
            return firstAvailable;
        }

        boolean addAvailable(E entry) {
            E last = lastAvailable;
            if (entry.nextAvailable != null || last == entry) {
                return false;
            }
            if (last == null) {
                assert firstAvailable == null;
                firstAvailable = entry;
            } else {
                last.nextAvailable = entry;
            }
            entry.prevAvailable = last;
            lastAvailable = entry;
            checkQueue();
            return true;
        }

        boolean removeAvailable(E entry) {
            PoolEntry next = entry.nextAvailable;
            PoolEntry prev = entry.prevAvailable;
            if (next == null) {
                if (prev == null) {
                    if (lastAvailable == entry) {
                        assert firstAvailable == entry;
                        lastAvailable = null;
                        firstAvailable = null;
                        checkQueue();
                        return true;
                    } else {
                        return false;
                    }
                } else {
                    entry.prevAvailable = null;
                    assert lastAvailable == entry;
                    //noinspection unchecked
                    lastAvailable = (E) prev;
                    prev.nextAvailable = null;
                    checkQueue();
                    return true;
                }
            } else {
                entry.nextAvailable = null;
                if (prev == null) {
                    assert firstAvailable == entry;
                    //noinspection unchecked
                    firstAvailable = (E) next;
                    next.prevAvailable = null;
                    checkQueue();
                    return true;
                } else {
                    entry.prevAvailable = null;
                    next.prevAvailable = prev;
                    prev.nextAvailable = next;
                    checkQueue();
                    return true;
                }
            }
        }

        void checkQueue() {
            if (true) return;
            PoolEntry prev = null;
            PoolEntry entry = firstAvailable;
            if (entry == null) {
                return;
            }
            while (true) {
                PoolEntry next = entry.nextAvailable;
                if (prev != entry.prevAvailable) {
                    throw new IllegalStateException();
                }
                if (next == null) {
                    if (lastAvailable != entry) {
                        throw new IllegalStateException();
                    }
                    break;
                }
                prev = entry;
                entry = next;
            }
        }
    }

    private abstract sealed class PoolEntry {
        private static final AtomicInteger NEXT_DEBUG_ID = new AtomicInteger(1);

        final LocalPoolPair poolPair;
        final ResizerConnection connection;
        int debugId;

        PoolEntry prevAvailable;
        PoolEntry nextAvailable;

        private PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            this.poolPair = localPoolsByLoop.get(eventLoop);
            if (this.poolPair == null) {
                throw new IllegalArgumentException("Event loop not part of given group");
            }
            this.connection = connection;
        }

        private synchronized int debugId() {
            if (debugId == 0) {
                debugId = NEXT_DEBUG_ID.getAndIncrement();
            }
            return debugId;
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + debugId() + ", pool=" + poolPair + "]";
        }

        final void checkInEventLoop() {
            assert poolPair.loop.inEventLoop();
        }

        final void onOpenConnection() {
            checkInEventLoop();

            poolPair.localPendingConnections--;

            GlobalStats oldStats;
            while (true) {
                oldStats = globalStats.get();
                GlobalStats newStats = oldStats.addPendingConnectionCount(-1);
                if (this instanceof Http2PoolEntry) {
                    newStats = newStats.addHttp2ConnectionCount(1);
                } else {
                    newStats = newStats.addHttp1ConnectionCount(1);
                }
                if (globalStats.weakCompareAndSetPlain(oldStats, newStats)) {
                    break;
                }
            }

            //poolPair.openLocalConnectionIfNecessary();
            openGlobalConnectionIfNecessary();
        }

        abstract void preDispatch(PendingRequest request);
    }

    final class Http1PoolEntry extends PoolEntry {
        Http1PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            super(eventLoop, connection);
        }

        void onConnectionEstablished() {
            checkInEventLoop();
            if (poolPair.http1.connections.add(this)) {
                markAvailable();
                onOpenConnection();
            }
        }

        void onConnectionInactive() {
            checkInEventLoop();
            poolPair.http1.removeAvailable(this);
            if (poolPair.http1.connections.remove(this)) {
                globalStats.updateAndGet(s -> s.addHttp1ConnectionCount(-1));
            }
        }

        void markAvailable() {
            checkInEventLoop();
            if (poolPair.http1.addAvailable(this)) {
                if (log.isTraceEnabled()) {
                    log.trace("{} became available", this);
                }
                poolPair.dispatchPendingRequests();
            }
        }

        void markUnavailable() {
            if (poolPair.http1.removeAvailable(this)) {
                if (log.isTraceEnabled()) {
                    log.trace("{} became unavailable", this);
                }
            }
        }

        @Override
        void preDispatch(PendingRequest request) {
            checkInEventLoop();
            if (!poolPair.http1.removeAvailable(this)) {
                throw new IllegalStateException("Entry wasn't available " + poolPair.http1.firstAvailable + " " + poolPair.http1.lastAvailable + " " + this);
            }
        }
    }

    final class Http2PoolEntry extends PoolEntry {
        private int available = 0;

        Http2PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            super(eventLoop, connection);
        }

        void onConnectionEstablished(int maxStreamCount) {
            checkInEventLoop();
            if (poolPair.http2.connections.add(this)) {
                markAvailable0(maxStreamCount);
                onOpenConnection();
            }
        }

        void onConnectionInactive() {
            checkInEventLoop();
            if (available > 0) {
                available = 0;
                poolPair.http2.removeAvailable(this);
            }
            if (poolPair.http2.connections.remove(this)) {
                globalStats.updateAndGet(s -> s.addHttp2ConnectionCount(-1));
            }
        }

        void markAvailable() {
            markAvailable0(1);
        }

        private void markAvailable0(int n) {
            checkInEventLoop();
            if (log.isTraceEnabled()) {
                log.trace("{} became available x{}", this, n);
            }
            boolean newlyAvailable = available == 0;
            available += n;
            if (newlyAvailable) {
                poolPair.http2.addAvailable(this);
                poolPair.dispatchPendingRequests();
            }
        }

        void markUnavailable() {
            checkInEventLoop();
            if (log.isTraceEnabled()) {
                log.trace("{} became unavailable", this);
            }
            available = 0;
            poolPair.http2.removeAvailable(this);
        }

        @Override
        void preDispatch(PendingRequest request) {
            checkInEventLoop();
            assert available > 0;
            available--;
            if (available == 0) {
                poolPair.http2.removeAvailable(this);
            }
        }
    }

    private record GlobalStats(
        int http1ConnectionCount,
        int http2ConnectionCount,
        int pendingConnectionCount,
        boolean seenHttp1,
        boolean seenHttp2
    ) {
        static final GlobalStats EMPTY = new GlobalStats(0, 0, 0, false, false);

        GlobalStats addHttp1ConnectionCount(int n) {
            return new GlobalStats(http1ConnectionCount + n, http2ConnectionCount, pendingConnectionCount, true, seenHttp2);
        }

        GlobalStats addHttp2ConnectionCount(int n) {
            return new GlobalStats(http1ConnectionCount, http2ConnectionCount + n, pendingConnectionCount, seenHttp1, true);
        }

        GlobalStats addPendingConnectionCount(int n) {
            return new GlobalStats(http1ConnectionCount, http2ConnectionCount, pendingConnectionCount + n, seenHttp1, seenHttp2);
        }
    }

    final class PendingRequest extends AtomicBoolean {
        private static final AtomicInteger NEXT_DEBUG_ID = new AtomicInteger(1);

        final @Nullable BlockHint blockHint;
        private final DelayedExecutionFlow<ConnectionManager.PoolHandle> sink = DelayedExecutionFlow.create();
        private final LocalPoolPair preferredPool;
        private final boolean permitStealing;
        volatile LocalPoolPair destPool;
        private int debugId;

        PendingRequest(@Nullable BlockHint blockHint) {
            this.blockHint = blockHint;

            preferredPool = pickPreferredPool();
            permitStealing = preferredPool == null ||
                connectionPoolConfiguration.getConnectionLocality() == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.PREFERRED;
        }

        synchronized int debugId() {
            if (debugId == 0) {
                debugId = NEXT_DEBUG_ID.getAndIncrement();
            }
            return debugId;
        }

        ExecutionFlow<ConnectionManager.PoolHandle> flow() {
            return sink;
        }

        void dispatch() {
            if (globalPending != null && globalPending.sum() >= connectionPoolConfiguration.getMaxPendingAcquires()) {

                tryCompleteExceptionally(new HttpClientException("Cannot acquire connection, exceeded max pending acquires configuration"));
                return;
            }
            if (log.isTraceEnabled()) {
                log.trace("{}: Starting dispatch, preferred pool {}", this, preferredPool);
            }
            if (globalPending != null) {
                globalPending.increment();
            }

            redispatch();
        }

        void redispatch() {
            if (preferredPool == null) {
                dispatchElsewhere();
            } else {
                destPool = preferredPool;
                if (destPool.loop.inEventLoop()) {
                    dispatchLocal();
                } else {
                    destPool.loop.execute(this::dispatchLocal);
                }
            }
        }

        private void dispatchLocal() {
            assert destPool.loop.inEventLoop();
            if (log.isTraceEnabled()) {
                log.trace("{}: Attempting dispatch on {}", this, destPool);
            }
            PoolEntry available = destPool.findAvailablePoolEntry();
            if (available != null) {
                dispatchTo(available);
                return;
            }
            if (permitStealing) {
                for (LocalPoolPair pool : RandomOffsetIterator.iterable(localPools)) {
                    if (pool != destPool && (pool.http1.firstAvailable != null || pool.http2.firstAvailable != null)) {
                        destPool = pool;
                        pool.loop.execute(this::dispatchLocal);
                        return;
                    }
                }
            }

            // need to open a new connection.

            if (preferredPool != null && destPool != preferredPool) {
                if (log.isTraceEnabled()) {
                    log.trace("{}: Moving back to preferred pool to open a new connection", this);
                }
                // move back to preferred pool first
                destPool = preferredPool;
                destPool.loop.execute(this::dispatchLocal);
                return;
            }

            if (blockHint != null && blockHint.blocks((EventLoop) destPool.loop)) {
                tryCompleteExceptionally(BlockHint.createException());
                return;
            }

            boolean open = openConnectionStep1();
            if (open) {
                destPool.openConnectionStep2();
            }

            if (open || !permitStealing) {
                if (log.isTraceEnabled()) {
                    log.trace("{}: Adding to local pending requests", this);
                }
                destPool.addLocalPendingRequest(this);
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("{}: Adding to global pending requests", this);
                }
                destPool = null;
                globalPendingRequests.add(this);
                for (LocalPoolPair pool : localPools) {
                    pool.notifyGlobalPendingRequestQueued();
                }
            }

            if (open) {
                if (log.isTraceEnabled()) {
                    log.trace("{}: Opening a new connection", this);
                }

                destPool.openConnectionStep3();
            }
        }

        private void dispatchTo(PoolEntry entry) {
            if (log.isTraceEnabled()) {
                log.trace("{}: Dispatching to connection {}", this, entry);
            }
            if (destPool == null) {
                // from global pending request queue
                destPool = entry.poolPair;
            } else {
                assert destPool.loop.inEventLoop();
                assert destPool == entry.poolPair;
            }
            entry.preDispatch(this);
            dispatchSafe(entry.connection, this);
        }

        private void dispatchElsewhere() {
            destPool = localPools.get(ThreadLocalRandom.current().nextInt(localPools.size()));
            if (log.isTraceEnabled()) {
                log.trace("{}: Scheduling dispatch on {}", this, destPool);
            }
            destPool.loop.execute(this::dispatchLocal);
        }

        // DelayedExecutionFlow does not allow concurrent completes, so this is a simple guard

        boolean tryCompleteExceptionally(Throwable t) {
            if (compareAndSet(false, true)) {
                if (globalPending != null) {
                    globalPending.decrement();
                }
                sink.completeExceptionally(t);
                return true;
            } else {
                return false;
            }
        }

        boolean tryComplete(ConnectionManager.PoolHandle value) {
            if (compareAndSet(false, true)) {
                if (globalPending != null) {
                    globalPending.decrement();
                }
                if (sink.isCancelled()) {
                    return false;
                }
                sink.complete(value);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return "PendingRequest[" + debugId() + "]";
        }
    }
}
