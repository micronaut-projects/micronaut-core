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
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;
import io.micronaut.core.execution.DelayedExecutionFlow;
import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.propagation.PropagatedContext;
import io.micronaut.http.client.HttpClientConfiguration;
import io.micronaut.http.client.exceptions.HttpClientException;
import io.micronaut.http.netty.channel.loom.EventLoopVirtualThreadScheduler;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadIoEventLoop;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.internal.ThreadExecutorMap;
import org.slf4j.Logger;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * This class handles the concurrent aspects of pooling for {@link ConnectionManager}.
 */
@NullUnmarked
@Internal
final class Pool49 implements Pool {
    private final Listener listener;
    private final Logger log;
    private final HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration;

    /**
     * There is one pool for each event loop this client runs on.
     */
    private final Map<EventExecutor, LocalPoolPair> localPoolsByLoop;
    /**
     * Ordered version of {@link #localPoolsByLoop} for faster access.
     */
    private final List<LocalPoolPair> localPools;

    /**
     * Number of pending acquire permits held by {@link PendingRequest}s. This is used to enforce
     * {@link HttpClientConfiguration.ConnectionPoolConfiguration#getMaxPendingAcquires()}. If
     * there is no limit, this field is {@code null} to save on atomic operations.
     */
    @Nullable
    private final AtomicInteger globalPending;
    /**
     * Connection statistics shared between all local pools, e.g. number of open HTTP/2
     * connections. These are used to enforce most limits from the
     * {@link #connectionPoolConfiguration}.
     */
    private final AtomicReference<GlobalStats> globalStats = new AtomicReference<>(GlobalStats.EMPTY);
    /**
     * Pending requests that are not associated with a particular event loop / local pool. These
     * requests can be picked up by any idle connection on any event loop.
     */
    private final PendingRequestQueue globalPendingRequests = new PendingRequestQueue();
    private volatile State state = State.OPEN;

    // for testing
    @SuppressWarnings("checkstyle:DeclarationOrder")
    Function<List<LocalPoolPair>, LocalPoolPair> pickPreferredPoolOverride;

    Pool49(Listener listener, Logger log, HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration, Iterable<? extends EventExecutor> group) {
        this.log = log;
        this.connectionPoolConfiguration = connectionPoolConfiguration;
        this.listener = listener;
        this.localPoolsByLoop = new LinkedHashMap<>();
        for (EventExecutor loop : group) {
            localPoolsByLoop.put(loop, new LocalPoolPair(loop));
        }
        this.localPools = List.copyOf(localPoolsByLoop.values());
        if (connectionPoolConfiguration.getMaxPendingAcquires() != Integer.MAX_VALUE) {
            globalPending = new AtomicInteger();
        } else {
            globalPending = null;
        }
    }

    /**
     * Call {@link ResizerConnection#dispatch} with exception handling.
     *
     * @param connection The connection to run the request on
     * @param toDispatch The request to run on the connection
     */
    private void dispatchSafe(ResizerConnection connection, PendingRequest toDispatch) {
        try {
            connection.dispatch(toDispatch);
        } catch (Exception e) {
            if (!toDispatch.tryCompleteExceptionally(e)) {
                // this is probably fine, log it anyway
                log.debug("Failure during connection dispatch operation, but dispatch request was already complete.", e);
            }
        }
    }

    /**
     * Called when an {@link Listener#openNewConnection(EventLoop)} operation fails asynchronously.
     *
     * @param eventLoop The event loop where the connection attempt was made
     * @param error The optional error
     */
    @Override
    public void onNewConnectionFailure(EventLoop eventLoop, @Nullable Throwable error) throws Exception {
        // todo: implement a circuit breaker here? right now, we just fail one connection in the
        //  subclass implementation, but maybe we should do more.
        LocalPoolPair poolPair = localPoolsByLoop.get(eventLoop);
        assert poolPair != null;
        if (eventLoop.inEventLoop()) {
            poolPair.onNewConnectionFailure(listener.wrapError(error));
        } else {
            eventLoop.execute(() -> poolPair.onNewConnectionFailure(listener.wrapError(error)));
        }
    }

    @Override
    public Pool.PendingRequest createPendingRequest(@Nullable BlockHint blockHint) {
        return new PendingRequest(blockHint);
    }

    @Override
    public Pool.Http1PoolEntry createHttp1PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
        return new Http1PoolEntry(eventLoop, connection);
    }

    @Override
    public Pool.Http2PoolEntry createHttp2PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
        return new Http2PoolEntry(eventLoop, connection);
    }

    /**
     * Run a task for each connection. This is only best effort. Used for housekeeping.
     *
     * @param c The consumer to run for each open connection
     */
    @Override
    public void forEachConnection(Consumer<ResizerConnection> c) {
        for (LocalPoolPair localPool : localPools) {
            localPool.http1.connections.forEach(e -> c.accept(e.connection));
            localPool.http2.connections.forEach(e -> c.accept(e.connection));
        }
    }

    @Override
    public void close() {
        state = State.CLOSED;
        failPendingRequests(globalPendingRequests);
        for (LocalPoolPair pool : localPools) {
            if (pool.loop.inEventLoop()) {
                pool.close();
            } else {
                try {
                    pool.loop.execute(pool::close);
                } catch (RejectedExecutionException e) {
                    // the loop is already shut down (e.g. a group the application supplied):
                    // the waiter queue is locked and channel close is safe from any thread
                    failPendingRequests(pool.localPendingRequests);
                    pool.http1.connections.forEach(entry -> entry.connection.close());
                    pool.http2.connections.forEach(entry -> entry.connection.close());
                }
            }
        }
    }

    private void failPendingRequests(PendingRequestQueue queue) {
        PendingRequest request;
        while ((request = queue.poll()) != null) {
            request.tryCompleteExceptionally(new HttpClientException("Connection pool is closed"));
        }
    }

    private enum State {
        OPEN, CLOSED
    }

    /**
     * Pick a preferred local pool for the current thread. This considers the
     * {@link HttpClientConfiguration.ConnectionPoolConfiguration#getConnectionLocality()} to try
     * to assign requests to connections on the same event loop as the request originates from.
     * This can reduce latency.
     *
     * @return The preferred pool to run on, or {@code null} if there is no preference
     * @throws HttpClientException For
     * {@link io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality#ENFORCED_ALWAYS},
     * if the current thread does not belong to an event loop
     */
    @Nullable
    LocalPoolPair pickPreferredPool() throws HttpClientException {
        if (pickPreferredPoolOverride != null) {
            return pickPreferredPoolOverride.apply(localPools);
        }

        LocalPoolPair poolPair = null;
        var configLocality = connectionPoolConfiguration.getConnectionLocality();
        if (configLocality != HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.IGNORE) {

            EventExecutor currentExecutor = null;

            EventLoopVirtualThreadScheduler elvts = EventLoopVirtualThreadScheduler.current();
            if (elvts != null) {
                currentExecutor = elvts.eventLoop();
            }

            if (currentExecutor == null) {
                currentExecutor = ThreadExecutorMap.currentExecutor();
            }

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
            if (poolPair == null && configLocality == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.ENFORCED_ALWAYS) {
                throw new HttpClientException("Attempted to open a HTTP connection from thread " +
                    Thread.currentThread() + " which is not part of the client event loop group, but configured the pool in locality mode ENFORCED_ALWAYS, which disallows " +
                    "requesting from outside this group");
            }
        }
        return poolPair;
    }

    /**
     * First step of opening a new connection. If this step is run (and returns {@code true}), the
     * other steps <i>must</i> also be run, but they can be delayed or on a different thread.
     *
     * <p>The first step does the global housekeeping to determine whether we can open a new
     * connection at all, and reserves a spot. It's not necessary to pick a particular event loop
     * at this stage.
     *
     * @return {@code true} if a new connection may be opened without exceeding configured limits
     * @see LocalPoolPair#openConnectionStep2()
     * @see LocalPoolPair#openConnectionStep3()
     */
    private boolean openConnectionStep1() {
        while (true) {
            GlobalStats oldStats = globalStats.get();
            if (state == State.CLOSED || limitsHit(oldStats)) {
                // just add to the pending request queue
                return false;
            }
            if (!globalStats.compareAndSet(oldStats, oldStats.addPendingConnectionCount(1))) {
                continue;
            }
            return true;
        }
    }

    /**
     * Check whether we have hit the connection limit.
     *
     * @param oldStats The current value of {@link #globalStats}
     * @return {@code true} if the limits were hit and no more connections may be opened
     */
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

    /**
     * Open a connection on any event loop, if necessary. This can be called e.g. when another
     * connection closes.
     */
    private void openGlobalConnectionIfNecessary() {
        while (true) {
            if (globalPendingRequests.isEmpty()) {
                // best-effort check
                break;
            }

            // try to open a connection for a request in the global queue
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

        // open connections for any requests in local queues
        if (!limitsHit(globalStats.get())) {
            // randomize so that there is no bias to opening connections on the first event loop,
            // when there's high contention
            for (LocalPoolPair pool : RandomOffsetIterator.iterable(localPools)) {
                if (pool.needPendingConnection) {
                    pool.loop.execute(pool::openLocalConnectionIfNecessary);
                }
            }
        }
    }

    /**
     * Iterator that cycles through a {@link List} from a random starting offset.
     *
     * @param <E> The element type
     */
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

    /**
     * A local pool pair. It's local because it's associated with a fixed event loop, and most
     * operations are restricted to that loop. It's a pair because there's actually two pools, one
     * for HTTP/1 and one for HTTP/2 (also includes HTTP/3), since HTTP/2 can serve many requests
     * over the same connection.
     */
    final class LocalPoolPair {
        final EventExecutor loop;
        final LocalPool<Http1PoolEntry> http1;
        final LocalPool<Http2PoolEntry> http2;
        /**
         * Number of connections that have been requested for this loop but not yet been
         * established.
         */
        int localPendingConnections = 0;
        final AtomicBoolean dispatchPendingRequestsQueued = new AtomicBoolean(false);

        /**
         * Pending requests that will definitely run on this event loop.
         */
        final PendingRequestQueue localPendingRequests = new PendingRequestQueue();
        /**
         * Volatile flag to check whether we need more connections to serve the pending requests of
         * this loop. Basically {@code localPendingConnections < localPendingRequests.size()},
         * except this is volatile so other threads can also read it.
         */
        volatile boolean needPendingConnection = false;

        LocalPoolPair(EventExecutor loop) {
            this.loop = loop;
            http1 = new LocalPool<>();
            http2 = new LocalPool<>();
        }

        /**
         * Notify us that a request has been queued in {@link #globalPendingRequests} and we may
         * want to pick it up with one of our idle connections.
         */
        void notifyGlobalPendingRequestQueued() {
            if (!dispatchPendingRequestsQueued.compareAndSet(false, true)) {
                return;
            }
            loop.execute(() -> {
                dispatchPendingRequestsQueued.set(false);
                dispatchPendingRequests();
            });
        }

        /**
         * Try to find an already available pool entry to serve a request.
         *
         * @return The pool entry
         */
        @Nullable
        PoolEntry findAvailablePoolEntry() {
            assert loop.inEventLoop();
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

        /**
         * Enqueue a request to be picked up by a local connection.
         *
         * @param request The request
         */
        private void addLocalPendingRequest(PendingRequest request) {
            assert loop.inEventLoop();
            localPendingRequests.add(request);
            needPendingConnection = localPendingRequests.size() > localPendingConnections;
            if (state == State.CLOSED) {
                failPendingRequests(localPendingRequests);
            }
        }

        void close() {
            assert loop.inEventLoop();
            failPendingRequests(localPendingRequests);
            needPendingConnection = false;
            http1.connections.forEach(e -> e.connection.close());
            http2.connections.forEach(e -> e.connection.close());
        }

        /**
         * Assign any pending requests (local or global) to available connections.
         */
        void dispatchPendingRequests() {
            assert loop.inEventLoop();
            if (state == State.CLOSED) {
                close();
                return;
            }
            // local requests first
            while (!localPendingRequests.isEmpty()) {
                PoolEntry poolEntry = findAvailablePoolEntry();
                if (poolEntry == null) {
                    return;
                }
                PendingRequest request = localPendingRequests.poll();
                if (request != null) {
                    request.dispatchTo(poolEntry);
                }
            }
            needPendingConnection = false;
            // then global requests
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

        /**
         * Second step of opening a connection. Like {@link #openConnectionStep1()}, this is
         * housekeeping, but this time it's specific to a particular loop.
         *
         * @see #openConnectionStep1()
         * @see #openConnectionStep2()
         */
        void openConnectionStep2() {
            assert loop.inEventLoop();
            localPendingConnections++;
            needPendingConnection = localPendingRequests.size() > localPendingConnections;
        }

        /**
         * Final step of opening a connection. This actually triggers opening a connection. We want
         * to enqueue any request <i>before</i> calling this method, in case it's successful
         * immediately.
         *
         * @see #openConnectionStep1()
         * @see #openConnectionStep2()
         */
        void openConnectionStep3() {
            assert loop.inEventLoop();
            if (state == State.CLOSED) {
                onNewConnectionFailure(new HttpClientException("Connection pool is closed"));
                return;
            }
            try {
                listener.openNewConnection((EventLoop) loop);
            } catch (Exception e) {
                onNewConnectionFailure(e);
            }
        }

        /**
         * Open a local connection if limits permit and we have unassigned requests.
         */
        void openLocalConnectionIfNecessary() {
            assert loop.inEventLoop();
            while (localPendingRequests.size() > localPendingConnections) {
                if (!openConnectionStep1()) {
                    break;
                }
                openConnectionStep2();
                openConnectionStep3();
            }
        }

        /**
         * Called when there's a connection failure for this pool. Will notify one pending request,
         * and undo housekeeping from {@link #openConnectionStep1()} and
         * {@link #openConnectionStep2()}.
         *
         * @param error The error to send to the pending request
         */
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
                } else if (state != State.CLOSED) {
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
    }

    /**
     * A local, event loop specific pool. This wraps a list of open connections, and a list of
     * connections that are available to serve requests.
     *
     * @param <E> The connection type
     */
    private final class LocalPool<E extends PoolEntry> {
        /**
         * All open connections for this pool. Thread-safe for {@link #forEachConnection}.
         */
        final Set<E> connections = ConcurrentHashMap.newKeySet();

        /**
         * First available connection. Doubly linked list. This is volatile so that other threads
         * can safely check whether we have an available connection or not.
         *
         * @see #lastAvailable
         * @see PoolEntry#prevAvailable
         * @see PoolEntry#nextAvailable
         */
        volatile E firstAvailable;
        /**
         * Last available connection.
         *
         * @see #firstAvailable
         * @see PoolEntry#prevAvailable
         * @see PoolEntry#nextAvailable
         */
        E lastAvailable;

        LocalPool() {
        }

        /**
         * Get an available connection if possible.
         *
         * @return The available connection
         */
        @Nullable
        PoolEntry peekAvailable() {
            return firstAvailable;
        }

        /**
         * Mark a connection as available.
         *
         * @param entry The connection pool entry
         * @return {@code true} if this connection is newly available, {@code false} if it already
         * was and there is no change.
         */
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
            return true;
        }

        /**
         * Mark a connection as unavailable.
         *
         * @param entry The connection pool entry
         * @return {@code true} if this connection was available, {@code false} if it already
         * wasn't and there is no change.
         */
        boolean removeAvailable(E entry) {
            PoolEntry next = entry.nextAvailable;
            PoolEntry prev = entry.prevAvailable;
            if (next == null) {
                if (prev == null) {
                    if (lastAvailable == entry) {
                        assert firstAvailable == entry;
                        lastAvailable = null;
                        firstAvailable = null;
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
                    return true;
                }
            } else {
                entry.nextAvailable = null;
                if (prev == null) {
                    assert firstAvailable == entry;
                    //noinspection unchecked
                    firstAvailable = (E) next;
                    next.prevAvailable = null;
                    return true;
                } else {
                    entry.prevAvailable = null;
                    next.prevAvailable = prev;
                    prev.nextAvailable = next;
                    return true;
                }
            }
        }
    }

    /**
     * Wrapper for a connection in this pool.
     */
    private abstract sealed class PoolEntry {
        private static final AtomicInteger NEXT_DEBUG_ID = new AtomicInteger(1);

        /**
         * The pool this connection is part of.
         */
        final LocalPoolPair poolPair;
        /**
         * The connection implementation.
         */
        final ResizerConnection connection;
        /**
         * Human-readable ID for logging. Only generated on demand.
         */
        int debugId;

        /**
         * Reference for the availability list.
         *
         * @see LocalPool#firstAvailable
         * @see LocalPool#lastAvailable
         * @see #nextAvailable
         */
        PoolEntry prevAvailable;
        /**
         * Reference for the availability list.
         *
         * @see LocalPool#firstAvailable
         * @see LocalPool#lastAvailable
         * @see #prevAvailable
         */
        PoolEntry nextAvailable;

        PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
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

        /**
         * Called when the connection is opened. Undoes the housekeeping from
         * {@link #openConnectionStep1()} and {@link LocalPoolPair#openConnectionStep2()}.
         */
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

            // since we decreased the pending connection count, another pool may have an
            // opportunity to open a connection.
            // Deliberately DO NOT prefer opening a connection on the same pool, here. That could
            // lead to one pool monopolizing all the connections.
            openGlobalConnectionIfNecessary();
        }

        /**
         * Called prior to {@link ResizerConnection#dispatch}. The purpose is to mark this
         * connection as unavailable.
         *
         * @param request The request that will be dispatched
         */
        abstract void preDispatch(PendingRequest request);
    }

    final class Http1PoolEntry extends PoolEntry implements Pool.Http1PoolEntry {
        Http1PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            super(eventLoop, connection);
        }

        @Override
        public void onConnectionEstablished() {
            checkInEventLoop();
            if (poolPair.http1.connections.add(this)) {
                markAvailable();
                onOpenConnection();
            }
        }

        @Override
        public void onConnectionInactive() {
            checkInEventLoop();
            poolPair.http1.removeAvailable(this);
            if (poolPair.http1.connections.remove(this)) {
                globalStats.updateAndGet(s -> s.addHttp1ConnectionCount(-1));
                openGlobalConnectionIfNecessary();
            }
        }

        @Override
        public void markAvailable() {
            checkInEventLoop();
            if (state == State.CLOSED) {
                connection.close();
                return;
            }
            if (poolPair.http1.addAvailable(this)) {
                if (log.isTraceEnabled()) {
                    log.trace("{} became available", this);
                }
                poolPair.dispatchPendingRequests();
            }
        }

        @Override
        public void markUnavailable() {
            checkInEventLoop();
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

    final class Http2PoolEntry extends PoolEntry implements Pool.Http2PoolEntry {
        private int available = 0;

        Http2PoolEntry(EventLoop eventLoop, ResizerConnection connection) {
            super(eventLoop, connection);
        }

        @Override
        public void onConnectionEstablished(int maxStreamCount) {
            checkInEventLoop();
            if (poolPair.http2.connections.add(this)) {
                markAvailable0(maxStreamCount);
                onOpenConnection();
            }
        }

        @Override
        public void onConnectionInactive() {
            checkInEventLoop();
            if (available > 0) {
                available = 0;
                poolPair.http2.removeAvailable(this);
            }
            if (poolPair.http2.connections.remove(this)) {
                globalStats.updateAndGet(s -> s.addHttp2ConnectionCount(-1));
                openGlobalConnectionIfNecessary();
            }
        }

        @Override
        public void markAvailable() {
            markAvailable0(1);
        }

        private void markAvailable0(int n) {
            checkInEventLoop();
            if (state == State.CLOSED) {
                connection.close();
                return;
            }
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

        @Override
        public void markUnavailable() {
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

    /**
     * Statistics shared between all local pools so that configured connection limits can be met.
     *
     * @param http1ConnectionCount   Number of HTTP/1 connections
     * @param http2ConnectionCount   Number of HTTP/2 connections
     * @param pendingConnectionCount Number of pending connections
     * @param seenHttp1              If {@code true}, we've seen an HTTP/1 connection through the
     *                               lifetime of this pool, meaning we should follow configured
     *                               HTTP/1 connection count limits
     * @param seenHttp2              If {@code true}, we've seen an HTTP/2 connection through the
     *                               lifetime of this pool, meaning we should follow configured
     *                               HTTP/2 connection count limits
     */
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

    /**
     * Intrusive removable waiters. The global queue is shared between loops; local queues use
     * the same lock to allow cancellation to unlink directly from any thread.
     */
    private final class PendingRequestQueue {
        private PendingRequest first;
        private PendingRequest last;
        // written under the lock, read without it: the hot-path emptiness checks take no lock
        private volatile int size;

        synchronized void add(PendingRequest request) {
            if (request.get()) {
                return;
            }
            assert request.queuedIn == null;
            request.prev = last;
            request.next = null;
            if (last == null) {
                first = request;
            } else {
                last.next = request;
            }
            last = request;
            size++;
            request.queuedIn = this;
            if (request.get()) {
                remove(request);
            }
        }

        synchronized PendingRequest peek() {
            return first;
        }

        synchronized PendingRequest poll() {
            PendingRequest request = first;
            if (request != null) {
                remove(request);
            }
            return request;
        }

        synchronized void remove(PendingRequest request) {
            if (request.queuedIn != this) {
                return;
            }
            if (request.prev == null) {
                first = request.next;
            } else {
                request.prev.next = request.next;
            }
            if (request.next == null) {
                last = request.prev;
            } else {
                request.next.prev = request.prev;
            }
            request.prev = null;
            request.next = null;
            size--;
            request.queuedIn = null;
        }

        int size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
        }
    }

    /**
     * An HTTP request that is waiting for a connection to run on.
     */
    final class PendingRequest extends AtomicBoolean implements Pool.PendingRequest, Runnable {
        private static final AtomicInteger NEXT_DEBUG_ID = new AtomicInteger(1);
        private static final AtomicIntegerFieldUpdater<PendingRequest> PERMIT_HELD =
            AtomicIntegerFieldUpdater.newUpdater(PendingRequest.class, "permitHeld");

        /**
         * Hint for which thread is blocked waiting for this connection.
         */
        final @Nullable BlockHint blockHint;
        /**
         * {@link ExecutionFlow} that completes with the connection.
         */
        private final DelayedExecutionFlow<ConnectionManager.PoolHandle> sink = DelayedExecutionFlow.create();
        /**
         * Context of the caller, the flow continuations must run in it rather than in the context
         * of whoever hands over the connection.
         */
        private final transient PropagatedContext propagatedContext = PropagatedContext.getOrEmpty();
        /**
         * Pool that we prefer based on the caller.
         */
        private final LocalPoolPair preferredPool;
        /**
         * Pools other than {@link #preferredPool} may pick up this connection if and only if this
         * field is {@code true}.
         */
        private final boolean permitStealing;
        /**
         * The pool this request is currently assigned to. May change throughout the lifetime of
         * the request.
         */
        private volatile LocalPoolPair destPool;
        /**
         * {@code 1} while this request holds a permit of {@link #globalPending}.
         */
        private volatile int permitHeld;
        private int debugId;
        private volatile PendingRequestQueue queuedIn;
        private PendingRequest prev;
        private PendingRequest next;

        PendingRequest(@Nullable BlockHint blockHint) {
            this.blockHint = blockHint;

            preferredPool = pickPreferredPool();
            permitStealing = preferredPool == null ||
                connectionPoolConfiguration.getConnectionLocality() == HttpClientConfiguration.ConnectionPoolConfiguration.ConnectionLocality.PREFERRED;
            sink.onCancel(this);
        }

        @Override
        public void run() {
            if (compareAndSet(false, true)) {
                unlink();
                releasePermit();
            }
        }

        private void unlink() {
            PendingRequestQueue queue;
            while ((queue = queuedIn) != null) {
                queue.remove(this);
            }
        }

        private synchronized int debugId() {
            if (debugId == 0) {
                debugId = NEXT_DEBUG_ID.getAndIncrement();
            }
            return debugId;
        }

        /**
         * Flow that completes when this request is assigned to a connection.
         *
         * @return The flow
         */
        @Override
        public ExecutionFlow<ConnectionManager.PoolHandle> flow() {
            return sink;
        }

        /**
         * Kick off dispatching this request to a connection. Note that this must be called exactly
         * once.
         */
        @Override
        public void dispatch() {
            if (get()) {
                return;
            }
            if (globalPending != null && !acquirePermit(globalPending)) {
                tryCompleteExceptionally(new HttpClientException("Cannot acquire connection, exceeded max pending acquires configuration"));
                return;
            }
            if (log.isTraceEnabled()) {
                log.trace("{}: Starting dispatch, preferred pool {}", this, preferredPool);
            }

            redispatch();
        }

        /**
         * Reserve a permit of {@link #globalPending} for this request.
         *
         * @param counter The {@link #globalPending} counter
         * @return {@code false} if the limit is reached
         */
        private boolean acquirePermit(AtomicInteger counter) {
            int limit = connectionPoolConfiguration.getMaxPendingAcquires();
            while (true) {
                int current = counter.get();
                if (current >= limit) {
                    return false;
                }
                if (counter.compareAndSet(current, current + 1)) {
                    break;
                }
            }
            permitHeld = 1;
            if (get() || sink.isCancelled()) {
                // completed or cancelled before the permit was recorded
                releasePermit();
            }
            return true;
        }

        /**
         * Release the permit of {@link #globalPending} held by this request, if any. Only the
         * first call after the permit was acquired has an effect.
         */
        private void releasePermit() {
            AtomicInteger counter = globalPending;
            if (counter != null && PERMIT_HELD.compareAndSet(this, 1, 0)) {
                counter.decrementAndGet();
            }
        }

        /**
         * Attempt to redispatch this connection. Unlike {@link #dispatch()}, can be called
         * multiple times, because it doesn't acquire a permit of {@link #globalPending}.
         */
        @Override
        public void redispatch() {
            if (get()) {
                return;
            }
            if (preferredPool == null) {
                destPool = localPools.get(ThreadLocalRandom.current().nextInt(localPools.size()));
                if (log.isTraceEnabled()) {
                    log.trace("{}: Scheduling dispatch on {}", this, destPool);
                }
            } else {
                destPool = preferredPool;
            }
            if (destPool.loop.inEventLoop()) {
                dispatchLocal();
            } else {
                destPool.loop.execute(this::dispatchLocal);
            }
        }

        /**
         * Dispatch this request to the current {@link #destPool}.
         */
        private void dispatchLocal() {
            assert destPool.loop.inEventLoop();
            if (get()) {
                return;
            }
            if (state == State.CLOSED) {
                tryCompleteExceptionally(new HttpClientException("Connection pool is closed"));
                return;
            }
            boolean traceEnabled = log.isTraceEnabled();
            if (traceEnabled) {
                log.trace("{}: Attempting dispatch on {}", this, destPool);
            }
            // is there a connection already? use it.
            PoolEntry available = destPool.findAvailablePoolEntry();
            if (available != null) {
                dispatchTo(available);
                return;
            }
            if (permitStealing) {
                // does another pool have an available connection? Move to that pool.
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
                if (traceEnabled) {
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
                if (traceEnabled) {
                    log.trace("{}: Adding to local pending requests", this);
                }
                destPool.addLocalPendingRequest(this);
            } else {
                // Limits are hit, can't open a new connection. Move this request to
                // globalPendingRequests as fallback.
                if (traceEnabled) {
                    log.trace("{}: Adding to global pending requests", this);
                }
                destPool = null;
                globalPendingRequests.add(this);
                if (state == State.CLOSED) {
                    failPendingRequests(globalPendingRequests);
                }
                for (LocalPoolPair pool : localPools) {
                    pool.notifyGlobalPendingRequestQueued();
                }
            }

            if (open) {
                if (traceEnabled) {
                    log.trace("{}: Opening a new connection", this);
                }

                destPool.openConnectionStep3();
            }
        }

        /**
         * Assign this request to the given connection.
         *
         * @param entry The connection
         */
        private void dispatchTo(PoolEntry entry) {
            entry.checkInEventLoop();
            if (get()) {
                return;
            }
            if (state == State.CLOSED) {
                tryCompleteExceptionally(new HttpClientException("Connection pool is closed"));
                return;
            }
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
            BlockHint blockHint = this.blockHint;
            if (blockHint != null && blockHint.blocks(entry.poolPair.loop)) {
                tryCompleteExceptionally(BlockHint.createException());
                return;
            }
            entry.preDispatch(this);
            dispatchSafe(entry.connection, this);
        }

        /**
         * The event loop this request will <i>likely</i> run on. This is only best effort.
         *
         * @return The event loop, or {@code null} if unknown
         */
        @Override
        public @Nullable EventExecutor likelyEventLoop() {
            LocalPoolPair pool = destPool;
            return pool == null ? null : pool.loop;
        }

        // DelayedExecutionFlow does not allow concurrent completes, so this is a simple guard

        boolean tryCompleteExceptionally(Throwable t) {
            if (compareAndSet(false, true)) {
                unlink();
                releasePermit();
                // a client event loop, not the single JVM-wide GlobalEventExecutor thread
                EventExecutor loop = likelyEventLoop();
                WaiterCompletion.complete(propagatedContext, loop == null ? localPools.get(0).loop : loop, () -> sink.completeExceptionally(t));
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean tryComplete(ConnectionManager.PoolHandle value) {
            if (compareAndSet(false, true)) {
                unlink();
                releasePermit();
                if (sink.isCancelled()) {
                    return false;
                }
                if (state == State.CLOSED) {
                    WaiterCompletion.complete(propagatedContext, value.channel().eventLoop(),
                        () -> sink.completeExceptionally(new HttpClientException("Connection pool is closed")));
                    return false;
                }
                WaiterCompletion.complete(propagatedContext, value.channel().eventLoop(), () -> sink.complete(value));
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
