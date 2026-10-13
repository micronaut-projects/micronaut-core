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

import io.micronaut.core.annotation.Internal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
 * <p>The native side holds the callback of a stream from its creation until its release: each stream, which holds its
 * callback, stays strongly reachable until then, from {@link #LIVE}, so that the garbage collector cannot free the
 * code the native side calls. A stream is created, scheduled, started, stopped, invalidated and released on the run
 * loop thread only, outside the run loop: no callback can run while it is stopped or released, nor after. Closing stops
 * the run loop, lets the thread release the streams, and waits for it; a thread that does not finish in time keeps
 * its callbacks for the rest of the process.</p>
 *
 * <p>The calls to FSEvents go through {@link FsEventsApi}; everything else here runs on any platform.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
final class FsEventStreams implements Closeable {

    static final String THREAD_NAME = "micronaut-dev-fsevents";

    private static final Logger LOG = LoggerFactory.getLogger(FsEventStreams.class);
    /**
     * The streams, and through them the callbacks, the native side may still call: removed only once released.
     */
    private static final Set<Object> LIVE = ConcurrentHashMap.newKeySet();
    private static final int EVENT_MUST_SCAN_SUB_DIRS = 0x00000001;
    private static final int EVENT_USER_DROPPED = 0x00000002;
    private static final int EVENT_KERNEL_DROPPED = 0x00000004;
    private static final int EVENT_ROOT_CHANGED = 0x00000020;
    private static final int RESCAN = EVENT_MUST_SCAN_SUB_DIRS | EVENT_USER_DROPPED | EVENT_KERNEL_DROPPED | EVENT_ROOT_CHANGED;
    private static final double RUN_SECONDS = 1.0;
    private static final long RUN_SECONDS_MILLIS = (long) (RUN_SECONDS * 1000);
    private static final long REGISTER_TIMEOUT_SECONDS = 10;
    private static final long CLOSE_TIMEOUT_MILLIS = 5000;

    private final FsEventsApi api;
    private final double latency;
    private final Consumer<Path> changed;
    private final Thread thread;
    private final LinkedBlockingQueue<Runnable> tasks = new LinkedBlockingQueue<>();
    private final CompletableFuture<FsEventsApi.RunLoop> runLoop = new CompletableFuture<>();
    /**
     * The streams, touched on the run loop thread only.
     */
    private final List<FsEventsApi.NativeStream> streams = new ArrayList<>();
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
     * @param api The calls to FSEvents
     * @param latency How long FSEvents holds the events that follow a first one, in seconds
     * @param changed Told of each directory under a watched tree in which something changed, or of the tree itself
     *                when the events under it must be looked for
     * @param failed Run if the run loop ends before the streams are closed, which no longer report anything
     * @throws IOException if the run loop does not start
     */
    FsEventStreams(FsEventsApi api, double latency, Consumer<Path> changed, Runnable failed) throws IOException {
        this.api = api;
        this.latency = latency;
        this.changed = changed;
        this.failed = failed;
        this.thread = new Thread(this::run, THREAD_NAME);
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
     * @param stream A stream
     * @return Whether the native side may still call the stream's callback, which is kept reachable until then
     */
    static boolean isLive(FsEventsApi.NativeStream stream) {
        return LIVE.contains(stream);
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
        // none when the run loop could not start
        FsEventsApi.RunLoop loop = runLoop.isDone() && !runLoop.isCompletedExceptionally() ? runLoop.join() : null;
        if (loop != null) {
            // stops the current or the next run of the loop: the thread then runs the tasks
            loop.stop();
        }
    }

    private void run() {
        try {
            FsEventsApi.RunLoop loop = api.currentRunLoop();
            runLoop.complete(loop);
            while (!closed) {
                Runnable task = streams.isEmpty() ? tasks.poll(RUN_SECONDS_MILLIS, TimeUnit.MILLISECONDS) : tasks.poll();
                while (task != null) {
                    task.run();
                    task = tasks.poll();
                }
                if (!closed && !streams.isEmpty()) {
                    loop.run(RUN_SECONDS);
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
            for (FsEventsApi.NativeStream stream : streams) {
                release(stream, true);
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
    private FsEventsApi.NativeStream start(Path directory) throws IOException {
        Path real = directory.toRealPath();
        FsEventsApi.NativeStream stream = api.create(real.toString(), latency, new Events(directory, real, changed));
        LIVE.add(stream);
        boolean started = false;
        try {
            stream.schedule(runLoop.join());
            if (!stream.start()) {
                throw new IOException("FSEvents could not start watching " + directory);
            }
            started = true;
            return stream;
        } finally {
            if (!started) {
                // only a started stream may be stopped
                release(stream, false);
            }
        }
    }

    /**
     * Stops, invalidates and releases a stream. Called on the run loop thread, outside the run loop.
     */
    private static void release(FsEventsApi.NativeStream stream, boolean started) {
        try {
            if (started) {
                stream.stop();
            }
            stream.invalidate();
            stream.release();
        } finally {
            LIVE.remove(stream);
        }
    }

    /**
     * Maps the real paths FSEvents reports under a tree to the watched ones and hands them over.
     */
    static final class Events {
        private final Path root;
        private final Path realRoot;
        private final Consumer<Path> changed;

        /**
         * @param root The tree watched
         * @param realRoot Its real path, under which FSEvents reports
         * @param changed Told of each directory in which something changed
         */
        Events(Path root, Path realRoot, Consumer<Path> changed) {
            this.root = root;
            this.realRoot = realRoot;
            this.changed = changed;
        }

        /**
         * Hands over the directories of events, or the whole tree for an event after which it must be looked for.
         *
         * @param realPaths The real paths of the events
         * @param flags The flags of the events
         */
        void report(String[] realPaths, int[] flags) {
            for (int i = 0; i < realPaths.length; i++) {
                changed.accept((flags[i] & RESCAN) != 0 ? root : watched(realPaths[i]));
            }
        }

        /**
         * Hands over the whole tree, when the events cannot be read.
         */
        void rescan() {
            changed.accept(root);
        }

        private Path watched(String reported) {
            Path real = Path.of(reported).normalize();
            if (real.startsWith(realRoot)) {
                return root.resolve(realRoot.relativize(real).toString());
            }
            return root;
        }
    }
}
