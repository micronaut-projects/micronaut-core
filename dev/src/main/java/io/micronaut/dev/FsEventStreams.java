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

import com.sun.jna.Callback;
import com.sun.jna.FunctionMapper;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import io.micronaut.core.annotation.Internal;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * The FSEvents streams of a {@link FsEventsWatchService}: one per watched tree, all scheduled on one run loop thread
 * of their own, telling a consumer which directory changed. The callback only maps the reported paths to the watched
 * ones and hands them over; it never stops or releases a stream.
 *
 * <p>The native side holds the callback of a stream from its creation until its release: each callback stays
 * strongly reachable until then, from the stream and from {@link #LIVE}, so that the garbage collector cannot free the
 * code the native side calls. A stream is created, scheduled, started, stopped, invalidated and released on the run
 * loop thread only, outside the run loop: no callback can run while it is stopped or released, nor after. Closing stops
 * the run loop, lets the thread release the streams, and waits for it; a thread that does not finish in time keeps
 * its callbacks for the rest of the process.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class FsEventStreams implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(FsEventStreams.class);
    /**
     * The callbacks the native side may still call: removed only once their stream is released.
     */
    private static final Set<Object> LIVE = ConcurrentHashMap.newKeySet();
    private static final long SINCE_NOW = -1;
    private static final int CREATE_NO_DEFER = 0x00000002;
    private static final int EVENT_MUST_SCAN_SUB_DIRS = 0x00000001;
    private static final int EVENT_USER_DROPPED = 0x00000002;
    private static final int EVENT_KERNEL_DROPPED = 0x00000004;
    private static final int EVENT_ROOT_CHANGED = 0x00000020;
    private static final int RESCAN = EVENT_MUST_SCAN_SUB_DIRS | EVENT_USER_DROPPED | EVENT_KERNEL_DROPPED | EVENT_ROOT_CHANGED;
    private static final double RUN_SECONDS = 1.0;
    private static final long RUN_SECONDS_MILLIS = (long) (RUN_SECONDS * 1000);
    private static final long REGISTER_TIMEOUT_SECONDS = 10;
    private static final long CLOSE_TIMEOUT_MILLIS = 5000;

    private final double latency;
    private final Consumer<Path> changed;
    private final Thread thread;
    private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final CompletableFuture<Pointer> runLoop = new CompletableFuture<>();
    /**
     * The streams, touched on the run loop thread only.
     */
    private final List<Stream> streams = new ArrayList<>();
    /**
     * The trees watched, in the order they were registered.
     */
    private final List<Path> roots = new ArrayList<>();
    private final Runnable failed;
    private volatile boolean closed;
    private volatile boolean ended;

    /**
     * Starts the run loop thread.
     *
     * @param latency How long FSEvents holds the events that follow a first one, in seconds
     * @param changed Told of each directory under a watched tree in which something changed, or of the tree itself
     *                when the events under it must be looked for
     * @param failed Run if the run loop ends before the streams are closed, which no longer report anything
     * @throws IOException if the run loop does not start
     */
    FsEventStreams(double latency, Consumer<Path> changed, Runnable failed) throws IOException {
        this.latency = latency;
        this.changed = changed;
        this.failed = failed;
        this.thread = new Thread(this::run, "micronaut-dev-fsevents");
        thread.setDaemon(true);
        thread.start();
        try {
            runLoop.get(REGISTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            close();
            throw new IOException("Interrupted starting the FSEvents run loop", e);
        } catch (ExecutionException | TimeoutException e) {
            close();
            throw new IOException("Cannot start the FSEvents run loop: " + e.getMessage(), e);
        }
    }

    /**
     * Watches the tree under a directory, unless a tree watched already holds it.
     *
     * @param directory The directory, absolute and normalized
     * @throws IOException if the stream cannot be created or started
     */
    void watch(Path directory) throws IOException {
        synchronized (roots) {
            if (closed) {
                throw new ClosedWatchServiceException();
            }
            if (ended) {
                throw new IOException("The FSEvents run loop ended");
            }
            for (Path root : roots) {
                if (directory.startsWith(root)) {
                    return;
                }
            }
            CompletableFuture<Void> started = new CompletableFuture<>();
            submit(() -> {
                try {
                    streams.add(start(directory));
                    started.complete(null);
                } catch (IOException | RuntimeException | LinkageError e) {
                    started.completeExceptionally(e);
                }
            });
            try {
                started.get(REGISTER_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted watching " + directory, e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new IOException("Cannot watch " + directory + " with FSEvents: " + cause.getMessage(), cause);
            } catch (TimeoutException e) {
                throw new IOException("Timed out watching " + directory + " with FSEvents", e);
            }
            roots.add(directory);
        }
    }

    @Override
    public void close() {
        synchronized (roots) {
            if (closed) {
                return;
            }
            closed = true;
        }
        wake();
        if (thread == Thread.currentThread()) {
            return;
        }
        try {
            thread.join(CLOSE_TIMEOUT_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (thread.isAlive()) {
            // its streams were not released: their callbacks stay in LIVE for the rest of the process
            LOG.warn("The FSEvents run loop did not stop within {} ms", CLOSE_TIMEOUT_MILLIS);
        }
    }

    private void submit(Runnable task) {
        tasks.add(task);
        wake();
    }

    private void wake() {
        // the thread runs the loop only while it has streams; otherwise it waits on the tasks
        tasks.add(() -> { });
        Pointer loop = runLoop.getNow(null);
        if (loop != null) {
            // stops the current or the next run of the loop: the thread then runs the tasks
            CoreServices.INSTANCE.cfRunLoopStop(loop);
        }
    }

    private void run() {
        try {
            Pointer mode = NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFRunLoopDefaultMode").getPointer(0);
            runLoop.complete(CoreServices.INSTANCE.cfRunLoopGetCurrent());
            while (!closed) {
                Runnable task = streams.isEmpty() ? tasks.poll(RUN_SECONDS_MILLIS, TimeUnit.MILLISECONDS) : tasks.poll();
                while (task != null) {
                    task.run();
                    task = tasks.poll();
                }
                if (!closed && !streams.isEmpty()) {
                    CoreServices.INSTANCE.cfRunLoopRunInMode(mode, RUN_SECONDS, (byte) 0);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException | LinkageError e) {
            runLoop.completeExceptionally(e);
            LOG.warn("The FSEvents run loop failed: {}", e.getMessage(), e);
        } finally {
            ended = true;
            // outside the run loop: no callback runs while the streams stop and are released
            for (Stream stream : streams) {
                stream.release();
            }
            streams.clear();
            if (!closed) {
                failed.run();
            }
        }
    }

    /**
     * Creates, schedules and starts the stream of a tree. Called on the run loop thread.
     */
    private Stream start(Path directory) throws IOException {
        Path real = directory.toRealPath();
        EventCallback callback = new EventCallback(directory, real, changed);
        LIVE.add(callback);
        Pointer stream = null;
        boolean started = false;
        try {
            stream = create(directory, real.toString(), callback);
            Pointer mode = NativeLibrary.getInstance("CoreFoundation").getGlobalVariableAddress("kCFRunLoopDefaultMode").getPointer(0);
            CoreServices.INSTANCE.fsEventStreamScheduleWithRunLoop(stream, runLoop.join(), mode);
            if (CoreServices.INSTANCE.fsEventStreamStart(stream) == 0) {
                throw new IOException("FSEvents could not start watching " + directory);
            }
            started = true;
            return new Stream(stream, callback, true);
        } finally {
            if (!started) {
                if (stream != null) {
                    // only a started stream may be stopped
                    new Stream(stream, callback, false).release();
                } else {
                    LIVE.remove(callback);
                }
            }
        }
    }

    private Pointer create(Path directory, String name, EventCallback callback) throws IOException {
        Pointer path = CoreServices.INSTANCE.cfStringCreateWithCharacters(null, name.toCharArray(), name.length());
        if (path == null) {
            throw new IOException("Cannot create the path of " + directory);
        }
        try {
            Pointer paths = CoreServices.INSTANCE.cfArrayCreate(null, new Pointer[] {path}, 1, null);
            if (paths == null) {
                throw new IOException("Cannot create the paths of " + directory);
            }
            try {
                Pointer stream = CoreServices.INSTANCE.fsEventStreamCreate(null, callback, null, paths, SINCE_NOW, latency, CREATE_NO_DEFER);
                if (stream == null) {
                    throw new IOException("FSEvents refused to watch " + directory);
                }
                return stream;
            } finally {
                // the stream copied the paths
                CoreServices.INSTANCE.cfRelease(paths);
            }
        } finally {
            CoreServices.INSTANCE.cfRelease(path);
        }
    }

    /**
     * A stream and the callback the native side calls for it.
     *
     * @param stream The stream
     * @param callback The callback, kept reachable until the stream is released
     * @param started Whether the stream started
     */
    private record Stream(Pointer stream, EventCallback callback, boolean started) {
        /**
         * Stops, invalidates and releases the stream. Called on the run loop thread, outside the run loop.
         */
        void release() {
            try {
                if (started) {
                    CoreServices.INSTANCE.fsEventStreamStop(stream);
                }
                CoreServices.INSTANCE.fsEventStreamInvalidate(stream);
                CoreServices.INSTANCE.fsEventStreamRelease(stream);
            } finally {
                LIVE.remove(callback);
            }
        }
    }

    /**
     * Maps the real paths FSEvents reports to the watched ones and hands them over. Never throws: an exception thrown
     * into the native side would end the process.
     */
    static final class EventCallback implements Callback {
        private final Path root;
        private final Path realRoot;
        private final Consumer<Path> changed;

        EventCallback(Path root, Path realRoot, Consumer<Path> changed) {
            this.root = root;
            this.realRoot = realRoot;
            this.changed = changed;
        }

        /**
         * Called by FSEvents on the run loop thread.
         *
         * @param stream The stream
         * @param info The stream's context, none
         * @param count How many events there are
         * @param paths The paths of the events, C strings
         * @param flags The flags of the events
         * @param ids The identifiers of the events
         */
        public void invoke(Pointer stream, Pointer info, long count, Pointer paths, Pointer flags, Pointer ids) {
            try {
                int events = (int) count;
                String[] names = paths.getStringArray(0, events, "UTF-8");
                int[] eventFlags = flags.getIntArray(0, events);
                for (int i = 0; i < events; i++) {
                    changed.accept((eventFlags[i] & RESCAN) != 0 ? root : watched(names[i]));
                }
            } catch (RuntimeException | LinkageError e) {
                // the tree is compared as a whole instead
                changed.accept(root);
            }
        }

        private Path watched(String reported) {
            Path real = Path.of(reported).normalize();
            if (real.startsWith(realRoot)) {
                return root.resolve(realRoot.relativize(real).toString());
            }
            return root;
        }
    }

    /**
     * The functions of CoreServices and CoreFoundation the streams use.
     */
    interface CoreServices extends Library {
        // the Java names start in lower case: cfRelease is CFRelease, fsEventStreamStart FSEventStreamStart
        CoreServices INSTANCE = Native.load("CoreServices", CoreServices.class, Map.of(
            Library.OPTION_FUNCTION_MAPPER, (FunctionMapper) (library, method) -> method.getName().toUpperCase(Locale.ROOT).substring(0, 2) + method.getName().substring(2)));

        Pointer cfStringCreateWithCharacters(@Nullable Pointer allocator, char[] chars, long length);

        Pointer cfArrayCreate(@Nullable Pointer allocator, Pointer[] values, long count, @Nullable Pointer callbacks);

        void cfRelease(Pointer object);

        Pointer fsEventStreamCreate(@Nullable Pointer allocator, Callback callback, @Nullable Pointer context, Pointer paths, long sinceWhen, double latency, int flags);

        void fsEventStreamScheduleWithRunLoop(Pointer stream, Pointer runLoop, Pointer mode);

        byte fsEventStreamStart(Pointer stream);

        void fsEventStreamStop(Pointer stream);

        void fsEventStreamInvalidate(Pointer stream);

        void fsEventStreamRelease(Pointer stream);

        Pointer cfRunLoopGetCurrent();

        int cfRunLoopRunInMode(Pointer mode, double seconds, byte returnAfterSourceHandled);

        void cfRunLoopStop(Pointer runLoop);
    }
}
