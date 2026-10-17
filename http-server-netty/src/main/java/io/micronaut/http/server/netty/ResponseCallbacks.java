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
import io.netty.util.concurrent.Future;
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
 * <p>The completion callbacks are settled once, by whichever comes first: the final write of
 * the response completes (or fails), the request is cleaned up without a final write, or the
 * client abandons the request.
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
    private boolean watching;
    private boolean cleanedUp;
    private boolean settled;

    /**
     * @param outboundAccess The connection of the request
     * @param failure        The failure of the response recorded before the callbacks were created
     */
    ResponseCallbacks(OutboundAccess outboundAccess, @Nullable Throwable failure) {
        this.outboundAccess = outboundAccess;
        this.failure = failure;
    }

    boolean whenCompleted(Consumer<@Nullable Throwable> callback) {
        synchronized (this) {
            if (settled) {
                return false;
            }
            if (completionCallbacks == null) {
                completionCallbacks = new ArrayList<>(1);
            }
            completionCallbacks.add(callback);
            if (watching) {
                return true;
            }
            watching = true;
        }
        outboundAccess.recordFinalWrite();
        addRemoval(outboundAccess.whenAbandoned(this::abandoned));
        return true;
    }

    Runnable whenAbandoned(Runnable callback) {
        synchronized (this) {
            if (cleanedUp) {
                return () -> { };
            }
        }
        // a failing callback must not keep the others of the stream from running
        Runnable removal = addRemoval(outboundAccess.whenAbandoned(() -> {
            try {
                callback.run();
            } catch (Throwable e) {
                LOG.warn("A request abandonment callback failed", e);
            }
        }));
        return () -> {
            synchronized (this) {
                if (removals != null) {
                    removals.remove(removal);
                }
            }
            removal.run();
        };
    }

    /**
     * Keep a removal to run when the request is cleaned up, or run it at once if it is already.
     *
     * @param removal The removal
     * @return The removal
     */
    private Runnable addRemoval(Runnable removal) {
        synchronized (this) {
            if (!cleanedUp) {
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
        if (failure == null) {
            failure = cause;
        }
    }

    private void abandoned() {
        settle(new ConnectionClosedException("The client went away before the response was written"));
    }

    /**
     * The request is cleaned up after its response: remove the abandon callbacks, and settle the
     * completion callbacks when the final write of the response completes.
     */
    void cleanedUp() {
        List<Runnable> toRemove;
        synchronized (this) {
            if (cleanedUp) {
                return;
            }
            cleanedUp = true;
            toRemove = removals;
            removals = null;
        }
        if (toRemove != null) {
            for (Runnable removal : toRemove) {
                removal.run();
            }
        }
        Future<?> finalWrite = outboundAccess.finalWrite();
        if (finalWrite != null) {
            finalWrite.addListener(future -> settle(future.cause()));
        } else if (outboundAccess.isAbandoned()) {
            abandoned();
        } else {
            // e.g. a switch of protocols, or a final write issued before the first registration
            settle(null);
        }
    }

    private void settle(@Nullable Throwable cause) {
        List<Consumer<@Nullable Throwable>> callbacks;
        Throwable reported;
        synchronized (this) {
            if (settled) {
                return;
            }
            settled = true;
            callbacks = completionCallbacks;
            completionCallbacks = null;
            reported = failure == null ? cause : failure;
        }
        if (callbacks == null) {
            return;
        }
        for (Consumer<@Nullable Throwable> callback : callbacks) {
            try {
                callback.accept(reported);
            } catch (Throwable e) {
                LOG.warn("A response completion callback failed", e);
            }
        }
    }
}
