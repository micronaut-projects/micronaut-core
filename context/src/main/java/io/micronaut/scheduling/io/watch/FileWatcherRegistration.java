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
package io.micronaut.scheduling.io.watch;

import io.micronaut.core.annotation.Experimental;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;

/**
 * A registration of interest in a directory, made with a {@link FileWatcher.WatchRequest}. Closing it removes the
 * listener; directories no other registration covers stop being watched.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface FileWatcherRegistration extends AutoCloseable {

    /**
     * @return The absolute root the registration watches
     */
    Path root();

    /**
     * @return Whether the registration is still active
     */
    boolean isActive();

    /**
     * Closes the registration. Once the method returned the listener is not called again, even when a stage it
     * returned is still pending; unless the method is called from the listener itself, it waits for a call of the
     * listener under way to return, unless the calling thread is interrupted, which stops the wait and leaves the
     * thread's interrupt flag set.
     */
    @Override
    void close();
}
