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
package io.micronaut.context.python;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Event-loop operations required to drive Micronaut-managed Python asyncio tasks.
 */
@Internal
@Experimental
public interface PythonEventLoop {

    /**
     * Used to decide whether Python work can run immediately or must be queued.
     *
     * @return Whether the current thread is this event-loop thread.
     */
    boolean inEventLoop();

    /**
     * Queue a callback for serialized execution on the loop.
     *
     * @param runnable The callback.
     */
    void execute(Runnable runnable);

    /**
     * Queue a callback after the requested delay using loop time.
     *
     * @param runnable The callback.
     * @param delay The delay.
     * @param unit The delay unit.
     * @return The scheduled future.
     */
    ScheduledFuture<?> schedule(Runnable runnable, long delay, TimeUnit unit);

    /**
     * Returns the monotonic time base exposed to Python's asyncio APIs.
     *
     * @return Event-loop time in seconds.
     */
    double time();
}
