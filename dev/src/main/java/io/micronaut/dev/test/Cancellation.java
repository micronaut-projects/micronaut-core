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
package io.micronaut.dev.test;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * Asks a run to stop early, as when a change arrives while it runs. A runner checks it between tests, and
 * stops the test running when its platform can.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class Cancellation {

    private final List<Runnable> callbacks = new ArrayList<>();
    private volatile boolean cancelled;

    /**
     * Asks the run to stop. The callbacks registered so far run once, on the calling thread; asking again does nothing.
     * Every callback runs even when one throws, and the first failure is rethrown afterwards with the others suppressed.
     */
    public void cancel() {
        List<Runnable> registered;
        synchronized (this) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            registered = List.copyOf(callbacks);
            callbacks.clear();
        }
        RuntimeException failure = null;
        for (Runnable callback : registered) {
            try {
                callback.run();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * @return Whether the run was asked to stop
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * Runs a callback once when the run is asked to stop, or now, on the calling thread, if it already was.
     *
     * @param callback The callback
     */
    public void onCancel(Runnable callback) {
        synchronized (this) {
            if (!cancelled) {
                callbacks.add(callback);
                return;
            }
        }
        callback.run();
    }
}
