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
package io.micronaut.dev;

import io.micronaut.context.ApplicationContext;
import org.jspecify.annotations.Nullable;

import java.time.Duration;

/**
 * What the {@link DevRuntime} does with its own threads as it closes or is asked to reload: the reload thread, and the
 * thread that runs a generation's main.
 *
 * @author graemerocher
 * @since 5.3.0
 */
final class RuntimeThreads {

    private RuntimeThreads() {
    }

    /**
     * Waits a bounded time for a thread to end, unless it is the calling thread.
     *
     * @param thread The thread
     * @param timeout How long to wait at most
     */
    static void join(@Nullable Thread thread, Duration timeout) {
        if (thread == null || thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(timeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Refuses to wait for a reload on a thread the reload waits for: the reload thread, which runs the reload
     * listeners, and the thread running the current generation's main, which a restart waits to end.
     *
     * @param worker The reload thread
     * @param application The thread running the current generation's main
     * @throws IllegalStateException if the calling thread is one of them
     */
    static void refuseOwnThreads(@Nullable Thread worker, @Nullable Thread application) {
        Thread current = Thread.currentThread();
        if (current == worker || current == application) {
            throw new IllegalStateException("A reload cannot be requested on " + current.getName() + ", which the reload waits for: request it from another thread");
        }
    }

    /**
     * Stops a context that is starting on the calling thread once its start ends: stopping waits for the start.
     *
     * @param context The context
     */
    static void stopOnceStarted(ApplicationContext context) {
        Thread stop = new Thread(context::stop, "micronaut-dev-stop");
        stop.setDaemon(true);
        stop.start();
    }
}
