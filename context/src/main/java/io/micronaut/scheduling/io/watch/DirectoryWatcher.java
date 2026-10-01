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
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.scheduling.io.watch.event.WatchEventType;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A {@link FileWatcher} over one {@link WatchService}, usable with or without an application context.
 *
 * <p>The watcher runs one thread. It registers every directory once, whatever number of registrations
 * cover it, reports absolute paths, coalesces the events of one save into one {@link FileChangeBatch}
 * per registration after a {@link Builder#quietPeriod(Duration) quiet period}, registers directories
 * created after the registration, and skips hidden directories.</p>
 *
 * <p>The way a directory is registered with the service is pluggable through a
 * {@link WatchKeyRegistrar}, because the native macOS service of {@code micronaut-runtime-osx}
 * registers a {@code WatchablePath} rather than the {@link Path} itself, and because tests want a
 * higher polling sensitivity than the JDK's default on platforms without native notification.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Experimental
@NullMarked
public final class DirectoryWatcher implements FileWatcher, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(DirectoryWatcher.class);
    /**
     * How many quiet periods the oldest pending change waits at most before its batch is delivered.
     */
    private static final int MAX_QUIET_PERIODS = 5;

    private final WatchService watchService;
    private final WatchKeyRegistrar registrar;
    private final Duration checkInterval;
    private final Duration quietPeriod;
    private final String threadName;
    private final Runnable closeAction;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<DirectoryRegistration> registrations = new CopyOnWriteArrayList<>();
    /**
     * Watched directories by absolute path. Guarded by {@code this}.
     */
    private final Map<Path, WatchedDirectory> directories = new LinkedHashMap<>();
    /**
     * Watched directories by key. Guarded by {@code this}.
     */
    private final Map<WatchKey, WatchedDirectory> directoriesByKey = new LinkedHashMap<>();
    /**
     * Changes waiting for the quiet period to elapse, in the order first observed. Only the watch thread touches it.
     */
    private final Map<Path, PendingChange> pending = new LinkedHashMap<>();
    /**
     * Counts recorded events. A registration remembers the count at its creation and receives only
     * changes recorded after it, never ones that were pending before it was made.
     */
    private volatile long sequence;
    private long firstPendingNanos;
    private long lastEventNanos;
    private @Nullable Thread thread;

    private DirectoryWatcher(Builder builder) {
        this.watchService = Objects.requireNonNull(builder.watchService, "watchService");
        this.registrar = builder.registrar;
        this.checkInterval = builder.checkInterval;
        this.quietPeriod = builder.quietPeriod;
        this.threadName = builder.threadName;
        this.closeAction = builder.closeAction != null ? builder.closeAction : this::closeWatchService;
    }

    /**
     * A builder for a watcher over the given service.
     *
     * @param watchService The watch service
     * @return The builder
     */
    public static Builder builder(WatchService watchService) {
        return new Builder(watchService);
    }

    /**
     * The registrar that registers a {@link Path} for create, delete and modify events.
     *
     * @return The registrar
     */
    public static WatchKeyRegistrar defaultRegistrar() {
        return (directory, service) -> directory.register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
    }

    /**
     * Starts the watch thread. Registrations may be made before or after starting.
     *
     * @return This watcher
     */
    public DirectoryWatcher start() {
        if (closed.get()) {
            throw new IllegalStateException("The watcher is closed");
        }
        if (active.compareAndSet(false, true)) {
            Thread watchThread = new Thread(this::run, threadName);
            watchThread.setDaemon(true);
            this.thread = watchThread;
            watchThread.start();
        }
        return this;
    }

    /**
     * @return Whether the watch thread is running
     */
    public boolean isRunning() {
        return active.get();
    }

    /**
     * @return The watch service the watcher polls
     */
    public WatchService getWatchService() {
        return watchService;
    }

    @Override
    public Registration watch(Path root, WatchOptions options, Consumer<FileChangeBatch> listener) {
        ArgumentUtils.requireNonNull("root", root);
        ArgumentUtils.requireNonNull("options", options);
        ArgumentUtils.requireNonNull("listener", listener);
        if (closed.get()) {
            throw new IllegalStateException("The watcher is closed");
        }
        Path absoluteRoot = root.toAbsolutePath().normalize();
        if (!Files.isDirectory(absoluteRoot)) {
            throw new IllegalArgumentException("Cannot watch " + absoluteRoot + ": not an existing directory");
        }
        synchronized (this) {
            // under the lock close() cannot clear the registrations between the check and the publication, and the
            // registration is published before its tree is scanned so a directory created during the scan is
            // registered by the watch thread once the lock is released
            if (closed.get()) {
                throw new IllegalStateException("The watcher is closed");
            }
            DirectoryRegistration registration = new DirectoryRegistration(absoluteRoot, options, listener, sequence);
            registrations.add(registration);
            try {
                registerTree(absoluteRoot, registration, null);
            } catch (IOException e) {
                registration.close();
                throw new UncheckedIOException("Cannot watch " + absoluteRoot, e);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Watching {} ({})", absoluteRoot, options.recursive() ? "recursive" : "non-recursive");
            }
            return registration;
        }
    }

    @Override
    public boolean isWatching(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        for (DirectoryRegistration registration : registrations) {
            if (registration.covers(absolute)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The absolute directories currently registered with the watch service.
     *
     * @return The directories
     */
    public synchronized List<Path> watchedDirectories() {
        return List.copyOf(directories.keySet());
    }

    /**
     * Stops the watch thread, closes every registration and runs the close action, which closes the
     * watch service unless the builder replaced it.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            active.set(false);
            synchronized (this) {
                for (DirectoryRegistration registration : registrations) {
                    registration.deactivate();
                }
                registrations.clear();
                for (WatchedDirectory directory : directories.values()) {
                    directory.key.cancel();
                }
                directories.clear();
                directoriesByKey.clear();
            }
            closeAction.run();
            Thread watchThread = this.thread;
            if (watchThread != null && watchThread != Thread.currentThread()) {
                watchThread.interrupt();
            }
        }
    }

    private void closeWatchService() {
        try {
            watchService.close();
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error stopping file watch service: {}", e.getMessage(), e);
            }
        }
    }

    private void run() {
        while (active.get()) {
            try {
                long timeout = pending.isEmpty() ? checkInterval.toMillis() : Math.max(1, millisUntilDue());
                WatchKey key = watchService.poll(timeout, TimeUnit.MILLISECONDS);
                if (key != null) {
                    drain(key);
                }
                if (!pending.isEmpty() && millisUntilDue() <= 0) {
                    flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ClosedWatchServiceException e) {
                return;
            } catch (RuntimeException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error processing file watch events: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Records the events of one key into the pending changes.
     *
     * @param key The signalled key
     * @return Whether an event was recorded
     */
    private boolean drain(WatchKey key) {
        WatchedDirectory directory;
        synchronized (this) {
            directory = directoriesByKey.get(key);
        }
        if (directory == null) {
            // a key of a directory a registration released, or one another component registered with the same service
            key.pollEvents();
            key.reset();
            return false;
        }
        boolean recorded = false;
        for (WatchEvent<?> event : key.pollEvents()) {
            WatchEvent.Kind<?> kind = event.kind();
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("WatchService overflow under {}: some changes may have been lost", directory.path);
                }
                continue;
            }
            if (!(event.context() instanceof Path context)) {
                continue;
            }
            Path changed = directory.path.resolve(context).toAbsolutePath().normalize();
            WatchEventType type = WatchEventType.of(kind);
            record(changed, type);
            recorded = true;
            if (type == WatchEventType.CREATE && Files.isDirectory(changed)) {
                registerCreatedDirectory(changed);
            }
        }
        if (!key.reset()) {
            release(directory);
        }
        return recorded;
    }

    /**
     * A batch is due once no event arrived for a quiet period, or once the oldest pending change has
     * waited for several quiet periods, so a stream of unrelated events (writes to an excluded file,
     * activity under another root) cannot postpone it indefinitely.
     */
    private long millisUntilDue() {
        long now = System.nanoTime();
        long quietDue = TimeUnit.NANOSECONDS.toMillis(lastEventNanos + quietPeriod.toNanos() - now);
        long latestDue = TimeUnit.NANOSECONDS.toMillis(firstPendingNanos + quietPeriod.toNanos() * MAX_QUIET_PERIODS - now);
        return Math.min(quietDue, latestDue);
    }

    private void record(Path path, WatchEventType type) {
        long now = System.nanoTime();
        if (pending.isEmpty()) {
            firstPendingNanos = now;
        }
        lastEventNanos = now;
        long recordedAt = ++sequence;
        PendingChange existing = pending.get(path);
        if (existing == null) {
            pending.put(path, new PendingChange(new FileChange(path, type), recordedAt));
        } else {
            pending.put(path, new PendingChange(existing.change().merge(type), recordedAt));
        }
    }

    /**
     * Registers a directory created after its parent was, for the recursive registrations that cover it,
     * and reports the files it already contains, since the service saw only the directory appear.
     */
    private void registerCreatedDirectory(Path created) {
        if (isHidden(created)) {
            return;
        }
        for (DirectoryRegistration registration : registrations) {
            if (registration.options().recursive()
                && registration.isActive()
                && registration.covers(created)
                && registration.acceptsDirectory(created)) {
                try {
                    registerTree(created, registration, path -> {
                        if (!path.equals(created)) {
                            record(path, WatchEventType.CREATE);
                        }
                    });
                } catch (IOException e) {
                    if (LOG.isWarnEnabled()) {
                        LOG.warn("Cannot watch new directory {}: {}", created, e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Delivers the pending changes to the registrations they fall under, once the quiet period passed.
     */
    private void flush() {
        if (pending.isEmpty()) {
            return;
        }
        List<PendingChange> changes = new ArrayList<>(pending.values());
        pending.clear();
        for (DirectoryRegistration registration : registrations) {
            if (!registration.isActive()) {
                continue;
            }
            List<FileChange> matching = new ArrayList<>(changes.size());
            for (PendingChange change : changes) {
                if (change.recordedAt() > registration.since && registration.accepts(change.change().path())) {
                    matching.add(change.change());
                }
            }
            if (!matching.isEmpty()) {
                registration.deliver(new FileChangeBatch(registration.root(), matching));
            }
        }
    }

    private void registerTree(Path start, DirectoryRegistration registration, @Nullable Consumer<Path> visitedFile) throws IOException {
        if (!registration.options().recursive()) {
            register(start, registration);
            return;
        }
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!dir.equals(start) && (isHidden(dir) || !registration.acceptsDirectory(dir))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                register(dir, registration);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (visitedFile != null) {
                    visitedFile.accept(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Skipping {} while registering watches: {}", file, exc.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private synchronized void register(Path directory, DirectoryRegistration registration) throws IOException {
        if (closed.get() || !registration.isActive()) {
            // a registration closed while a directory created under it was being registered: its keys are
            // released by close() and it must not acquire new ones the release would miss
            return;
        }
        Path absolute = directory.toAbsolutePath().normalize();
        WatchedDirectory watched = directories.get(absolute);
        if (watched == null) {
            WatchKey key = registrar.register(absolute, watchService);
            watched = new WatchedDirectory(absolute, key);
            directories.put(absolute, watched);
            directoriesByKey.put(key, watched);
        }
        // the initial scan and the watch thread may both register a directory created during the scan
        if (!watched.owners.contains(registration)) {
            watched.owners.add(registration);
        }
        if (!registration.directories.contains(watched)) {
            registration.directories.add(watched);
        }
    }

    private synchronized void release(WatchedDirectory directory) {
        directories.remove(directory.path);
        directoriesByKey.remove(directory.key);
        for (DirectoryRegistration owner : directory.owners) {
            owner.directories.remove(directory);
        }
        directory.owners.clear();
        directory.key.cancel();
    }

    private synchronized void unregister(DirectoryRegistration registration) {
        for (WatchedDirectory directory : List.copyOf(registration.directories)) {
            directory.owners.remove(registration);
            if (directory.owners.isEmpty()) {
                directories.remove(directory.path);
                directoriesByKey.remove(directory.key);
                directory.key.cancel();
            }
        }
        registration.directories.clear();
    }

    private static boolean isHidden(Path dir) {
        Path name = dir.getFileName();
        if (name != null && name.toString().startsWith(".")) {
            return true;
        }
        try {
            return Files.isHidden(dir);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Registers a directory with a watch service.
     */
    @FunctionalInterface
    public interface WatchKeyRegistrar {

        /**
         * Registers the directory for create, delete and modify events.
         *
         * @param directory The absolute directory
         * @param watchService The service
         * @return The key
         * @throws IOException if registration fails
         */
        WatchKey register(Path directory, WatchService watchService) throws IOException;
    }

    /**
     * Builds a {@link DirectoryWatcher}.
     */
    public static final class Builder {
        private final WatchService watchService;
        private WatchKeyRegistrar registrar = defaultRegistrar();
        private Duration checkInterval = Duration.ofMillis(300);
        private Duration quietPeriod = Duration.ofMillis(120);
        private String threadName = "micronaut-filewatch-thread";
        private @Nullable Runnable closeAction;

        private Builder(WatchService watchService) {
            this.watchService = watchService;
        }

        /**
         * @param registrar How directories are registered with the service
         * @return This builder
         */
        public Builder registrar(WatchKeyRegistrar registrar) {
            this.registrar = Objects.requireNonNull(registrar, "registrar");
            return this;
        }

        /**
         * @param checkInterval How long a poll of the service waits while no change is pending
         * @return This builder
         */
        public Builder checkInterval(Duration checkInterval) {
            this.checkInterval = Objects.requireNonNull(checkInterval, "checkInterval");
            return this;
        }

        /**
         * @param quietPeriod How long the watcher waits for further events before delivering a batch
         * @return This builder
         */
        public Builder quietPeriod(Duration quietPeriod) {
            this.quietPeriod = Objects.requireNonNull(quietPeriod, "quietPeriod");
            return this;
        }

        /**
         * @param threadName The name of the watch thread
         * @return This builder
         */
        public Builder threadName(String threadName) {
            this.threadName = Objects.requireNonNull(threadName, "threadName");
            return this;
        }

        /**
         * Replaces closing the watch service when the watcher closes. The native macOS service must
         * not be closed, because closing it has crashed the JVM.
         *
         * @param closeAction What to run instead
         * @return This builder
         */
        public Builder closeAction(Runnable closeAction) {
            this.closeAction = Objects.requireNonNull(closeAction, "closeAction");
            return this;
        }

        /**
         * @return The watcher, not yet started
         */
        public DirectoryWatcher build() {
            return new DirectoryWatcher(this);
        }
    }

    private record PendingChange(FileChange change, long recordedAt) {
    }

    private static final class WatchedDirectory {
        private final Path path;
        private final WatchKey key;
        private final List<DirectoryRegistration> owners = new ArrayList<>(2);

        WatchedDirectory(Path path, WatchKey key) {
            this.path = path;
            this.key = key;
        }
    }

    private final class DirectoryRegistration implements Registration {
        private final Path root;
        private final WatchOptions options;
        private final Consumer<FileChangeBatch> listener;
        private final long since;
        private final AtomicBoolean registrationActive = new AtomicBoolean(true);
        private final List<WatchedDirectory> directories = new CopyOnWriteArrayList<>();

        DirectoryRegistration(Path root, WatchOptions options, Consumer<FileChangeBatch> listener, long since) {
            this.root = root;
            this.options = options;
            this.listener = listener;
            this.since = since;
        }

        @Override
        public Path root() {
            return root;
        }

        @Override
        public WatchOptions options() {
            return options;
        }

        @Override
        public boolean isActive() {
            return registrationActive.get();
        }

        @Override
        public void close() {
            if (registrationActive.compareAndSet(true, false)) {
                synchronized (DirectoryWatcher.this) {
                    registrations.remove(this);
                    unregister(this);
                }
            }
        }

        void deactivate() {
            registrationActive.set(false);
        }

        boolean covers(Path absolute) {
            if (!absolute.startsWith(root)) {
                return false;
            }
            if (options.recursive()) {
                return true;
            }
            Path parent = absolute.getParent();
            return absolute.equals(root) || root.equals(parent);
        }

        /**
         * Whether a change is delivered to this registration: it must lie under the root, in a directory
         * this registration itself registered (an overlapping registration without the same exclusions
         * may watch directories this one excluded), and pass the include and exclude patterns.
         */
        boolean accepts(Path absolute) {
            if (!covers(absolute) || absolute.equals(root)) {
                return false;
            }
            Path parent = absolute.getParent();
            return parent != null && owns(parent) && options.accepts(root.relativize(absolute));
        }

        private boolean owns(Path directory) {
            for (WatchedDirectory watched : directories) {
                if (watched.path.equals(directory)) {
                    return true;
                }
            }
            return false;
        }

        boolean acceptsDirectory(Path absoluteDirectory) {
            return !options.excludesDirectory(root.relativize(absoluteDirectory));
        }

        void deliver(FileChangeBatch batch) {
            try {
                listener.accept(batch);
            } catch (RuntimeException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("File watch listener for {} failed: {}", root, e.getMessage(), e);
                }
            }
        }
    }
}
