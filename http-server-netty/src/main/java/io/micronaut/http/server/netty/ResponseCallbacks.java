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
package io.micronaut.http.server.netty;

import io.micronaut.http.exceptions.ConnectionClosedException;
import io.micronaut.http.server.netty.handler.OutboundAccess;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The callbacks that a request registered with
 * {@link io.micronaut.http.ServerHttpRequest#whenResponseCompleted} and
 * {@link io.micronaut.http.ServerHttpRequest#whenAbandoned}. Only created for a request that
 * registers one.
 */
final class ResponseCallbacks {
    private static final Logger LOG = LoggerFactory.getLogger(ResponseCallbacks.class);

    private final OutboundAccess outboundAccess;
    private @Nullable List<Consumer<@Nullable Throwable>> completionCallbacks;
    /**
     * Remove the abandon callbacks from the connection when the response is done.
     */
    private @Nullable List<Runnable> removals;
    private @Nullable Throwable failure;
    private boolean done;

    ResponseCallbacks(OutboundAccess outboundAccess) {
        this.outboundAccess = outboundAccess;
    }

    synchronized boolean whenCompleted(Consumer<@Nullable Throwable> callback) {
        if (done) {
            return false;
        }
        if (completionCallbacks == null) {
            completionCallbacks = new ArrayList<>(1);
        }
        completionCallbacks.add(callback);
        return true;
    }

    Runnable whenAbandoned(Runnable callback) {
        synchronized (this) {
            if (done) {
                return () -> { };
            }
        }
        // registered outside the lock: the callback runs at once if the request is abandoned already
        Runnable removal = outboundAccess.whenAbandoned(callback);
        synchronized (this) {
            if (!done) {
                if (removals == null) {
                    removals = new ArrayList<>(2);
                }
                removals.add(removal);
                return removal;
            }
        }
        removal.run();
        return removal;
    }

    /**
     * Remember why the response failed. The first failure wins.
     *
     * @param cause The failure
     */
    synchronized void fail(Throwable cause) {
        if (!done && failure == null) {
            failure = cause;
        }
    }

    /**
     * The response is done: remove the abandon callbacks and run the completion callbacks, once.
     */
    void complete() {
        List<Consumer<@Nullable Throwable>> callbacks;
        List<Runnable> toRemove;
        Throwable cause;
        synchronized (this) {
            if (done) {
                return;
            }
            done = true;
            callbacks = completionCallbacks;
            toRemove = removals;
            completionCallbacks = null;
            removals = null;
            cause = failure;
        }
        if (toRemove != null) {
            for (Runnable removal : toRemove) {
                removal.run();
            }
        }
        if (callbacks == null) {
            return;
        }
        if (cause == null && outboundAccess.isAbandoned()) {
            cause = new ConnectionClosedException("The client went away before the response was written");
        }
        for (Consumer<@Nullable Throwable> callback : callbacks) {
            try {
                callback.accept(cause);
            } catch (Throwable e) {
                LOG.warn("A response completion callback failed", e);
            }
        }
    }
}
