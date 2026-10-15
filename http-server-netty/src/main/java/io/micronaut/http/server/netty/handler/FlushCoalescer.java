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
package io.micronaut.http.server.netty.handler;

import io.micronaut.core.annotation.Internal;
import io.netty.util.concurrent.EventExecutor;

/**
 * Runs a flush at most once per event loop turn, like netty's
 * {@link io.netty.handler.flush.FlushConsolidationHandler}
 * does for a whole pipeline. A response body that arrives as many small pieces in one turn (e.g.
 * a {@code Flux} that emits synchronously) is written with one {@code flush}, i.e. one
 * {@code write} syscall, instead of one per piece.
 * <p>
 * The flush is deferred by scheduling a task on the event loop, so it runs after the current
 * task has completed, which is when the pieces of the current turn have all been written. The
 * handlers do not use this while they are inside a read: they flush on read complete instead,
 * which comes even earlier.
 * <p>
 * Not thread-safe: all methods must be called on the event loop.
 *
 * @since 5.3.0
 */
@Internal
final class FlushCoalescer implements Runnable {
    private final EventExecutor executor;
    private final Runnable flush;
    /**
     * {@code true} iff a flush task has been submitted and has not run yet.
     */
    private boolean scheduled = false;

    /**
     * @param executor The event loop of the channel
     * @param flush    The operation that performs the flush. It must tolerate running after the
     *                 channel has been closed or the handler has been removed
     */
    FlushCoalescer(EventExecutor executor, Runnable flush) {
        this.executor = executor;
        this.flush = flush;
    }

    /**
     * Request a flush at the end of the current event loop turn. Only the first call in a turn
     * submits a task.
     */
    void schedule() {
        if (!scheduled) {
            scheduled = true;
            executor.execute(this);
        }
    }

    /**
     * @return {@code true} iff a flush is scheduled and has not run yet
     */
    boolean isScheduled() {
        return scheduled;
    }

    /**
     * Note that the caller flushes right now, so a scheduled flush is not needed anymore. The
     * submitted task then does nothing.
     */
    void cancel() {
        scheduled = false;
    }

    /**
     * Run the scheduled flush now instead of at the end of the turn, if one is scheduled.
     */
    void flushNow() {
        run();
    }

    @Override
    public void run() {
        if (scheduled) {
            scheduled = false;
            flush.run();
        }
    }
}
