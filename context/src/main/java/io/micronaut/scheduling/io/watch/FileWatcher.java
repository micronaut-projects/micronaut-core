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
import java.util.function.Consumer;

/**
 * Watches directories for changes on behalf of every component of the process.
 *
 * <p>A process needs one file watcher, not one per component: every watcher costs a thread and, on
 * platforms without native change notification, a poll of the whole tree. Components register interest
 * in a directory with {@link #watch(Path, WatchOptions, Consumer)} and receive coalesced
 * {@link FileChangeBatch batches} of absolute paths; the watcher registers each directory with the
 * underlying {@link java.nio.file.WatchService} once, however many registrations cover it.</p>
 *
 * <p>Listeners are invoked on the watch thread, one batch at a time. A listener that throws does not
 * stop the watcher or affect other listeners: the exception is logged and the next batch is delivered.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface FileWatcher {

    /**
     * Watches the given root with the given options.
     *
     * <p>The root must exist and be a directory. With {@link WatchOptions#recursive()} every directory
     * below it that is not hidden and not excluded is watched too, including directories created later.</p>
     *
     * @param root The directory to watch
     * @param options The options
     * @param listener The listener, invoked with each batch of changes under the root
     * @return The registration, which the caller closes when it is no longer interested
     * @throws IllegalArgumentException if the root does not exist or is not a directory
     * @throws java.io.UncheckedIOException if the directories cannot be registered
     */
    Registration watch(Path root, WatchOptions options, Consumer<FileChangeBatch> listener);

    /**
     * Watches the given root recursively with the {@link WatchOptions#DEFAULT default options}.
     *
     * @param root The directory to watch
     * @param listener The listener, invoked with each batch of changes under the root
     * @return The registration
     * @see #watch(Path, WatchOptions, Consumer)
     */
    default Registration watch(Path root, Consumer<FileChangeBatch> listener) {
        return watch(root, WatchOptions.DEFAULT, listener);
    }

    /**
     * Whether the given path is covered by an active registration.
     *
     * @param path A file or directory
     * @return True if a registration's root contains the path
     */
    boolean isWatching(Path path);

    /**
     * A registration of interest in a directory. Closing it removes the listener; directories no other
     * registration covers stop being watched.
     */
    interface Registration extends AutoCloseable {

        /**
         * @return The absolute root the registration watches
         */
        Path root();

        /**
         * @return The options the registration was made with
         */
        WatchOptions options();

        /**
         * @return Whether the registration is still active
         */
        boolean isActive();

        @Override
        void close();
    }
}
