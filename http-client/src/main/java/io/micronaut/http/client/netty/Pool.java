/*
 * Copyright 2017-2025 original authors
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
import io.micronaut.core.execution.ExecutionFlow;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutor;

import java.util.function.Consumer;

/**
 * API for connection pool implementations. The pool is responsible for event loop management,
 * connection lifecycle etc, while {@link ConnectionManager} handles the protocol side
 * (HTTP, ALPN, ...).
 *
 * @author Jonas Konrad
 * @since 4.9.0
 */
@Internal
sealed interface Pool permits Pool49, Pool40 {
    /**
     * Called when a {@link Listener#openNewConnection(EventLoop)} operation fails.
     *
     * @param eventLoop The event loop this failure happened on
     * @param error     The failure
     */
    void onNewConnectionFailure(@NonNull EventLoop eventLoop, @Nullable Throwable error) throws Exception;

    /**
     * Create a new {@link PendingRequest} that can be used to claim a connection from this pool.
     *
     * @param blockHint The thread that is blocked waiting for this request
     * @return The request handle
     */
    @NonNull
    PendingRequest createPendingRequest(@Nullable BlockHint blockHint);

    /**
     * Register a new HTTP/1.x connection with this pool.
     *
     * @param eventLoop The event loop this
     * @param connection The connection implementation
     * @return The pool entry
     */
    @NonNull
    Http1PoolEntry createHttp1PoolEntry(@NonNull EventLoop eventLoop, @NonNull ResizerConnection connection);

    /**
     * Register a new HTTP/2 connection with this pool.
     *
     * @param eventLoop The event loop this
     * @param connection The connection implementation
     * @return The pool entry
     */
    @NonNull
    Http2PoolEntry createHttp2PoolEntry(@NonNull EventLoop eventLoop, @NonNull ResizerConnection connection);

    /**
     * Iterate over all open connections.
     *
     * @param c The lambda to run
     */
    void forEachConnection(@NonNull Consumer<ResizerConnection> c);

    /**
     * Hooks called by this pool.
     */
    sealed interface Listener permits ConnectionManager.PoolHolder {
        /**
         * Wrap a connection failure.
         *
         * @param error The failure
         * @return The wrapped failure
         */
        @NonNull
        Throwable wrapError(@Nullable Throwable error);

        /**
         * Open a new connection.
         *
         * @param eventLoop The event loop the connection should live on
         */
        void openNewConnection(@NonNull EventLoop eventLoop);
    }

    /**
     * This is the protocol-side connection implementation.
     */
    sealed interface ResizerConnection permits ConnectionManager.PoolHolder.ConnectionHolder {
        /**
         * Dispatch a stream on this connection. If this fails for some reason, call
         * {@link PendingRequest#redispatch()} to retry with another connection.
         *
         * @param sink The pending request that wants to acquire this connection
         */
        void dispatch(PendingRequest sink) throws Exception;
    }

    /**
     * A queued request that should be dispatched onto a connection in this pool.
     */
    sealed interface PendingRequest permits Pool49.PendingRequest, Pool40.PendingRequest {
        /**
         * The flow that will complete when this request is assigned to a connection, or when the
         * assignment fails.
         *
         * @return The flow
         */
        @NonNull
        ExecutionFlow<ConnectionManager.PoolHandle> flow();

        /**
         * Trigger the initial dispatch of this request onto a connection in this pool.
         */
        void dispatch();

        /**
         * Trigger a redispatch in case a {@link ResizerConnection#dispatch} operation failed
         * asynchronously.
         */
        void redispatch();

        /**
         * The event loop that this request will hopefully run on.
         *
         * @return The event loop
         */
        @Nullable
        EventExecutor likelyEventLoop();

        /**
         * Complete the {@link #flow()} with the given connection.
         *
         * @param value The connection
         * @return {@code true} if the request was assigned, {@code false} if the assignment failed
         * e.g. because of a previous error
         */
        boolean tryComplete(ConnectionManager.PoolHandle value);
    }

    sealed interface Http1PoolEntry permits Pool49.Http1PoolEntry, Pool40.Http1 {
        /**
         * Connection was established.
         */
        void onConnectionEstablished();

        /**
         * Connection becomes inactive.
         */
        void onConnectionInactive();

        /**
         * The previous request has finished and this connection is available again.
         */
        void markAvailable();

        /**
         * This connection becomes unavailable and will remain so.
         */
        void markUnavailable();
    }

    sealed interface Http2PoolEntry permits Pool49.Http2PoolEntry, Pool40.Http2 {
        /**
         * Connection was established.
         *
         * @param maxStreamCount Maximum HTTP/2 stream count
         */
        void onConnectionEstablished(int maxStreamCount);

        /**
         * Connection becomes inactive.
         */
        void onConnectionInactive();

        /**
         * A previous request finished and its stream on this connection becomes available.
         */
        void markAvailable();

        /**
         * This connection becomes unavailable and will remain so.
         */
        void markUnavailable();
    }
}
