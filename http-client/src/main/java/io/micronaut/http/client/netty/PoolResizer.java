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
import io.micronaut.http.client.HttpClientConfiguration;
import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * This class handles the sizing of a connection pool to conform to the configuration in
 * {@link io.micronaut.http.client.HttpClientConfiguration.ConnectionPoolConfiguration}.
 */
@Internal
abstract class PoolResizer {
    private final Logger log;
    private final HttpClientConfiguration.ConnectionPoolConfiguration connectionPoolConfiguration;

    private final AtomicReference<WorkState> state = new AtomicReference<>(WorkState.IDLE);

    private final AtomicInteger pendingRequests = new AtomicInteger(0);
    private final AtomicInteger pendingConnectionCount = new AtomicInteger(0);
    private final AtomicInteger http1ConnectionCount = new AtomicInteger(0);
    private final AtomicInteger http2ConnectionCount = new AtomicInteger(0);

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
            doSomeWork();

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
        // snapshot our fields
        int pendingRequests = this.pendingRequests.get();
        int pendingConnectionCount = this.pendingConnectionCount.get();
        int http1ConnectionCount = this.http1ConnectionCount.get();
        int http2ConnectionCount = this.http2ConnectionCount.get();

        if (pendingRequests == 0) {
            // if there are no pending requests, there is nothing to do.
            return;
        }
        int connectionsToOpen = pendingRequests - pendingConnectionCount;
        // make sure we won't exceed our config setting for pending connections
        connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxPendingConnections() - pendingConnectionCount);
        // limit the connection count to the protocol-specific settings, but only if that protocol was seen for this pool.
        if (http1ConnectionCount > 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp2Connections() - http1ConnectionCount);
        }
        if (http2ConnectionCount > 0) {
            connectionsToOpen = Math.min(connectionsToOpen, connectionPoolConfiguration.getMaxConcurrentHttp2Connections() - http2ConnectionCount);
        }

        if (connectionsToOpen > 0) {
            this.pendingConnectionCount.addAndGet(connectionsToOpen);
            for (int i = 0; i < connectionsToOpen; i++) {
                try {
                    openNewConnection();
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

    abstract void openNewConnection() throws Exception;

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

    final void onPendingRequestChange(int delta) {
        if (pendingRequests.addAndGet(delta) < 0) {
            throw new IllegalStateException("Negative pending requests");
        }
        dirty();
    }

    // can be overridden, so `throws Exception` ensures we handle any errors
    void onNewConnectionFailure(@Nullable Throwable error) throws Exception {
        // todo: circuit breaker?
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onNewConnectionEstablished1() {
        http1ConnectionCount.incrementAndGet();
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onNewConnectionEstablished2() {
        http2ConnectionCount.incrementAndGet();
        pendingConnectionCount.decrementAndGet();
        dirty();
    }

    final void onConnectionInactive1() {
        http1ConnectionCount.decrementAndGet();
        dirty();
    }

    final void onConnectionInactive2() {
        http2ConnectionCount.decrementAndGet();
        dirty();
    }

    private enum WorkState {
        IDLE,
        ACTIVE_WITH_PENDING_WORK,
        ACTIVE_WITHOUT_PENDING_WORK,
    }
}
