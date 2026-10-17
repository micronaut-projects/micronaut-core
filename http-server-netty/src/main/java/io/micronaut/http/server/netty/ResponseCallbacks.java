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
import io.netty.channel.ChannelFuture;
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
 * <p>The first registration has the connection record the final write of the response. The
 * completion callbacks are settled once, by whichever comes first: the recorded final write
 * completes or fails, the request is cleaned up without a recorded final write, or the client
 * abandons the request. The abandonment callbacks stay registered until the recorded final write
 * completes.
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
    /**
     * Whether the final write of the response is recorded.
     */
    private boolean tracking;
    private boolean watching;
    private boolean cleanedUp;
    /**
     * Whether the abandon callbacks have been removed: the response is done.
     */
    private boolean released;
    private boolean settled;
    /**
     * Whether the response switched the protocol of the connection.
     */
    private boolean upgraded;

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
            if (settled || !track()) {
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
        addRemoval(outboundAccess.whenAbandoned(() -> {
            if (outboundAccess.isAbandoned()) {
                abandoned();
            }
        }));
        return true;
    }

    /**
     * Have the connection record the final write, once, before a callback is accepted.
     *
     * @return {@code false} if the final write was issued already, unrecorded, so that its
     * outcome cannot be told
     */
    private boolean track() {
        if (!tracking) {
            if (!outboundAccess.recordFinalWrite()) {
                return false;
            }
            tracking = true;
        }
        return true;
    }

    @Nullable Runnable whenAbandoned(Runnable callback) {
        synchronized (this) {
            if (released) {
                return () -> { };
            }
            // keeps the callback registered while the final write is in progress; a final write
            // issued unrecorded cannot be awaited, so the callback could not be kept for it
            if (!track()) {
                return null;
            }
        }
        // a failing callback must not keep the others of the stream from running
        Runnable removal = addRemoval(outboundAccess.whenAbandoned(() -> {
            // the HTTP/1.1 connection also closes after a written response, e.g. with connection: close
            if (!outboundAccess.isAbandoned()) {
                return;
            }
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
     * Keep a removal to run when the response is done, or run it at once if it is already.
     *
     * @param removal The removal
     * @return The removal
     */
    private Runnable addRemoval(Runnable removal) {
        synchronized (this) {
            if (!released) {
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
     * The response switched the protocol of the connection: remove the abandon callbacks, the
     * close of the connection ends the new protocol. The completion is settled at cleanup.
     */
    void upgraded() {
        synchronized (this) {
            upgraded = true;
        }
        release();
    }

    /**
     * The request is cleaned up after its response: once the recorded final write of the
     * response completes, remove the abandon callbacks and settle the completion callbacks.
     */
    void cleanedUp() {
        synchronized (this) {
            if (cleanedUp) {
                return;
            }
            cleanedUp = true;
        }
        boolean switched;
        synchronized (this) {
            switched = upgraded;
        }
        if (switched) {
            release();
            settle(null);
            return;
        }
        ChannelFuture finalWrite = outboundAccess.finalWrite();
        if (finalWrite != null) {
            finalWrite.addListener(future -> {
                Throwable cause = future.cause();
                settle(cause);
                if (cause == null) {
                    release();
                } else {
                    // the write fails when the connection or the stream closes, before the close
                    // is reported to the abandon callbacks in the same turn of the event loop:
                    // they are removed after that
                    finalWrite.channel().eventLoop().execute(this::release);
                }
            });
            return;
        }
        release();
        if (outboundAccess.isAbandoned()) {
            abandoned();
        } else {
            // no recorded final write, e.g. a switch of protocols
            settle(null);
        }
    }

    private void release() {
        List<Runnable> toRemove;
        synchronized (this) {
            released = true;
            toRemove = removals;
            removals = null;
        }
        if (toRemove != null) {
            for (Runnable removal : toRemove) {
                removal.run();
            }
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
