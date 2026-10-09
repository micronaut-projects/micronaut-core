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
package io.micronaut.context.reload;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * When a request may proceed while a development launcher reloads the application: a request that arrives while a
 * batch of changes is compiled and applied waits for it, so that it is not answered by a generation about to be
 * replaced, and is never refused while one generation stops and the next starts. A server runtime that holds requests
 * at its own level, below Micronaut's filter chain, such as a servlet container serving servlets of its own, asks it
 * whether to hold a request, and for how long.
 *
 * <p>A development launcher provides one per process while it runs, {@link #current()}; outside development mode there
 * is none. The admission is the launcher's: it holds no application context, environment or class of the application,
 * so a server kept across restarts may keep it. Look it up when a server is created or a generation starts, never per
 * request: outside development mode that is one null check.</p>
 *
 * <p>Admission is granted once the batch is done, or once a restart is about to stop the generation the held requests
 * arrived on: that generation serves them, and those in flight, before it stops, within {@link #drainTimeout()}.
 * A request a server holds at its own level past that point waits for the next generation.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface RequestAdmission {

    /**
     * The admission of the development launcher that runs this process.
     *
     * @return The admission, or null outside development mode
     */
    static @Nullable RequestAdmission current() {
        return RequestAdmissionHolder.current();
    }

    /**
     * Whether a request that arrives now may proceed: true unless a batch of changes is in progress. Cheap, for a gate
     * to check on each request before it allocates anything to hold one.
     *
     * @return True when no batch holds requests
     */
    boolean isAdmitted();

    /**
     * Completes when a request that arrives now may proceed: at once when no batch is in progress, otherwise once the
     * batch is done or a restart drains the running generation. It always completes normally, and a caller cannot
     * complete it. Holding a request on it is for an asynchronous server; one that blocks a thread per request uses
     * {@link #awaitAdmission(Duration)}.
     *
     * @return The stage
     */
    CompletionStage<Void> whenAdmitted();

    /**
     * Blocks until a request that arrives now may proceed, at most for the given time.
     *
     * @param timeout How long to wait, usually {@link #holdTimeout()}
     * @return True when admitted, false when the time ran out first: the request is best answered with a 503 and a
     * {@code Retry-After}
     * @throws InterruptedException If the thread is interrupted while it waits
     */
    default boolean awaitAdmission(Duration timeout) throws InterruptedException {
        if (isAdmitted()) {
            return true;
        }
        try {
            whenAdmitted().toCompletableFuture().get(timeout.toNanos(), TimeUnit.NANOSECONDS);
            return true;
        } catch (TimeoutException e) {
            return false;
        } catch (ExecutionException e) {
            // never completes exceptionally; were it to, the batch is over and the request may proceed
            return true;
        }
    }

    /**
     * How long a request that arrives while a batch is in progress is held before it is answered with a 503, from the
     * launcher's settings ({@code micronaut.dev.requests.hold-timeout} for micronaut-dev).
     *
     * @return The hold timeout
     */
    Duration holdTimeout();

    /**
     * How long a restart waits for the requests in flight on the generation that stops, from the launcher's settings
     * ({@code micronaut.dev.requests.drain-timeout} for micronaut-dev).
     *
     * @return The drain timeout
     */
    Duration drainTimeout();
}
