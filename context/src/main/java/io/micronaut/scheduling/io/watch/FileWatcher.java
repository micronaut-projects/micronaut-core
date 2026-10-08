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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Watches directories for changes on behalf of every component of the process.
 *
 * <p>A process needs one file watcher, not one per component: every watcher costs a thread and, on
 * platforms without native change notification, a poll of the whole tree. Components describe the directory
 * they are interested in with {@link #directory(Path)} and register a listener with the
 * {@link WatchRequest#watch(Consumer) terminal operation} of the request:</p>
 *
 * <pre>
 * FileWatcherRegistration registration = fileWatcher.directory(root)
 *     .include("{**&#47;,}*.html")
 *     .exclude("build/**")
 *     .watch(batch -&gt; refresh(batch));
 * </pre>
 *
 * <p>Listeners receive coalesced {@link FileChangeBatch batches} of absolute paths; the watcher registers each
 * directory with the underlying {@link java.nio.file.WatchService} once, however many registrations cover it.</p>
 *
 * <p>Listeners are currently invoked on the watcher's thread, one batch at a time; while the API is
 * {@link Experimental experimental} the thread that delivers a batch may change. A listener whose work takes
 * long, such as restarting an application, should not block that thread: it registers with
 * {@link WatchRequest#watchAsync(Function)} and returns a {@link CompletionStage} that completes when the work is
 * done. Until then its registration receives no further batch: the changes that arrive meanwhile are merged, with
 * the rules of {@link FileChange}, into one batch delivered once the stage completed, while the other
 * registrations keep receiving theirs. A listener that throws, or whose stage completes exceptionally, does not
 * stop the watcher or affect other listeners: the failure is logged and the next batch is delivered.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public interface FileWatcher {

    /**
     * Starts a request to watch the given directory. Nothing is registered until the request's
     * {@link WatchRequest#watch(Consumer) watch} or {@link WatchRequest#watchAsync(Function) watchAsync} is called,
     * which is also where the directory must exist.
     *
     * @param root The directory to watch
     * @return The request, recursive and without include or exclude patterns
     */
    WatchRequest directory(Path root);

    /**
     * Whether the given path is covered by an active registration.
     *
     * @param path A file or directory
     * @return True if a registration's root contains the path
     */
    boolean isWatching(Path path);

    /**
     * A request to watch a directory, completed by {@link #watch(Consumer)} or {@link #watchAsync(Function)}.
     *
     * <p>Patterns are JDK {@link java.nio.file.FileSystem#getPathMatcher(String) glob} patterns matched against the
     * path of a change relative to the root. A pattern that starts with <code>**&#47;</code> needs a separator, so it
     * does not match a file directly under the root: <code>{**&#47;,}*.html</code> matches every HTML file,
     * <code>**&#47;*.html</code> only those in a subdirectory.</p>
     *
     * <p>Each terminal operation takes a snapshot of the request, so a request may be reused, and changed, for
     * further registrations.</p>
     */
    interface WatchRequest {

        /**
         * Whether directories below the root are watched too, including directories created later. Hidden directories
         * are never watched. Defaults to true.
         *
         * @param recursive Whether to watch the whole tree
         * @return This request
         */
        WatchRequest recursive(boolean recursive);

        /**
         * Adds patterns a changed path must match to be delivered. Without include patterns, every path is.
         *
         * @param globs Glob patterns relative to the root, for example <code>{**&#47;,}*.html</code>
         * @return This request
         */
        WatchRequest include(String... globs);

        /**
         * Adds patterns a changed path must not match to be delivered. A directory is not watched at all when an exclude
         * pattern excludes everything below it, as <code>build/**</code> does for {@code build}; a pattern such as
         * <code>build/*</code>, which excludes only the direct children of {@code build}, leaves the directories
         * below them watched.
         *
         * @param globs Glob patterns relative to the root, for example <code>build/**</code>
         * @return This request
         */
        WatchRequest exclude(String... globs);

        /**
         * Registers a listener that completes its work before it returns.
         *
         * @param listener The listener, invoked with each batch of changes under the root
         * @return The registration, which the caller closes when it is no longer interested
         * @throws IllegalArgumentException if the root does not exist or is not a directory
         * @throws java.io.UncheckedIOException if the directories cannot be registered
         * @see #watchAsync(Function)
         */
        default FileWatcherRegistration watch(Consumer<FileChangeBatch> listener) {
            return watchAsync(batch -> {
                listener.accept(batch);
                return CompletableFuture.completedFuture(null);
            });
        }

        /**
         * Registers a listener whose work completes with the stage it returns. The registration receives no further
         * batch until the stage completed; the changes that arrive meanwhile are merged into the next batch.
         *
         * <p>The directories are registered before the method returns. It is named apart from {@link #watch(Consumer)}
         * so that a lambda such as {@code batch -> restart(batch)} is not ambiguous between the two.</p>
         *
         * @param listener The listener, invoked with each batch of changes under the root
         * @return The registration, which the caller closes when it is no longer interested
         * @throws IllegalArgumentException if the root does not exist or is not a directory
         * @throws java.io.UncheckedIOException if the directories cannot be registered
         */
        FileWatcherRegistration watchAsync(Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener);
    }
}
