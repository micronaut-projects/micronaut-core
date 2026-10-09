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
import java.nio.file.FileSystems;
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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A {@link FileWatcher} over one {@link WatchService}, usable with or without an application context.
 *
 * <p>The watcher runs one thread. It registers every directory once, whatever number of registrations
 * cover it, reports absolute paths, coalesces the events of one save into one {@link FileChangeBatch}
 * per registration after a {@link Builder#quietPeriod(Duration) quiet period}, registers directories
 * created after the registration, and skips hidden directories.</p>
 *
 * <p>Listeners run on the watch thread. A registration whose listener returned a stage that has not completed yet
 * receives no batch until it has: the changes meanwhile are merged into the batch delivered after it, while the
 * other registrations keep receiving theirs.</p>
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
    private final boolean closeWatchServiceOnClose;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final List<DirectoryRegistration> registrations = new CopyOnWriteArrayList<>();
    /**
     * Registrations whose stage completed while changes were held back for them, for the watch thread to deliver.
     */
    private final ConcurrentLinkedQueue<DirectoryRegistration> ready = new ConcurrentLinkedQueue<>();
    /**
     * How many stages returned by listeners are pending. While any is, the watch thread polls at least every quiet
     * period, so that changes held back for it are delivered soon after it completes.
     */
    private final AtomicInteger pendingStages = new AtomicInteger();
    /**
     * Watched directories by absolute path. Guarded by {@code this}.
     */
    private final Map<Path, WatchedDirectory> directories = new LinkedHashMap<>();
    /**
     * Watched directories by key. Guarded by {@code this}.
     */
    private final Map<WatchKey, WatchedDirectory> directoriesByKey = new LinkedHashMap<>();
    /**
     * Whether a registration has changes waiting for the quiet period to elapse. Each registration keeps its own,
     * decided when they are recorded, so that a registration receives only changes recorded after it was made, merged
     * only with each other, and still receives those of a directory released before they were delivered. Only the
     * watch thread touches it.
     */
    private boolean pending;
    private long firstPendingNanos;
    private long lastEventNanos;
    private @Nullable Thread thread;

    private DirectoryWatcher(Builder builder, WatchService watchService) {
        this.watchService = watchService;
        this.registrar = builder.registrar;
        this.checkInterval = builder.checkInterval;
        this.quietPeriod = builder.quietPeriod;
        this.threadName = builder.threadName;
        this.closeWatchServiceOnClose = builder.closeWatchServiceOnClose;
    }

    /**
     * A builder for a watcher over the given service.
     *
     * @param watchService The watch service
     * @return The builder
     */
    public static Builder builder(WatchService watchService) {
        return new Builder(Objects.requireNonNull(watchService, "watchService"));
    }

    /**
     * A builder for a watcher over a new {@link WatchService} of the default file system, which the watcher creates
     * when it is built and closes when it is closed.
     *
     * @return The builder
     */
    public static Builder builder() {
        return new Builder(null);
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
    WatchService getWatchService() {
        return watchService;
    }

    @Override
    public FileWatcher.WatchRequest directory(Path root) {
        ArgumentUtils.requireNonNull("root", root);
        return new Request(root);
    }

    private FileWatcherRegistration registerRoot(Path root, WatchFilter filter, Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener) {
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
            DirectoryRegistration registration = new DirectoryRegistration(absoluteRoot, filter, listener);
            registrations.add(registration);
            try {
                registerTree(absoluteRoot, registration, null);
            } catch (IOException e) {
                registration.close();
                throw new UncheckedIOException("Cannot watch " + absoluteRoot, e);
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Watching {} ({})", absoluteRoot, filter.recursive() ? "recursive" : "non-recursive");
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
    synchronized List<Path> watchedDirectories() {
        return List.copyOf(directories.keySet());
    }

    /**
     * Stops the watch thread, closes every registration and, unless the builder said
     * {@link Builder#closeWatchServiceOnClose(boolean) otherwise}, the watch service. Once it returned no listener is
     * called again.
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            active.set(false);
            List<DirectoryRegistration> closing;
            synchronized (this) {
                closing = List.copyOf(registrations);
                registrations.clear();
                for (WatchedDirectory directory : directories.values()) {
                    directory.key.cancel();
                }
                directories.clear();
                directoriesByKey.clear();
            }
            // outside the watcher's lock: a listener under way may itself call the watcher
            for (DirectoryRegistration registration : closing) {
                registration.deactivate();
            }
            ready.clear();
            if (closeWatchServiceOnClose) {
                closeWatchService();
            }
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
                long timeout = pending ? Math.max(1, millisUntilDue()) : checkInterval.toMillis();
                if (pendingStages.get() > 0 || !ready.isEmpty()) {
                    timeout = ready.isEmpty() ? Math.max(1, Math.min(timeout, quietPeriod.toMillis())) : 0;
                }
                WatchKey key = timeout == 0 ? watchService.poll() : watchService.poll(timeout, TimeUnit.MILLISECONDS);
                if (key != null) {
                    drain(key);
                }
                if (pending && millisUntilDue() <= 0) {
                    flush();
                }
                deliverReady();
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

    /**
     * Records a change for every registration it is delivered to, decided now: a directory whose key became invalid
     * is released right after its events are recorded, and a registration made later must not receive the change.
     */
    private void record(Path path, WatchEventType type) {
        lastEventNanos = System.nanoTime();
        for (DirectoryRegistration registration : registrations) {
            recordFor(registration, path, type);
        }
    }

    private void recordFor(DirectoryRegistration registration, Path path, WatchEventType type) {
        if (!registration.isActive() || !registration.accepts(path)) {
            return;
        }
        if (!pending) {
            pending = true;
            firstPendingNanos = System.nanoTime();
        }
        registration.record(path, type);
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
            if (registration.filter.recursive()
                && registration.isActive()
                && registration.covers(created)
                && registration.acceptsDirectory(created)) {
                try {
                    registerTree(created, registration, path -> {
                        if (!path.equals(created)) {
                            lastEventNanos = System.nanoTime();
                            recordFor(registration, path, WatchEventType.CREATE);
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
        if (!pending) {
            return;
        }
        pending = false;
        for (DirectoryRegistration registration : registrations) {
            List<FileChange> changes = registration.takePending();
            if (!changes.isEmpty() && registration.isActive()) {
                registration.offer(changes);
            }
        }
    }

    /**
     * Delivers the changes held back for registrations whose stage completed meanwhile.
     */
    private void deliverReady() {
        DirectoryRegistration registration;
        while ((registration = ready.poll()) != null) {
            registration.deliverHeld();
        }
    }

    private void registerTree(Path start, DirectoryRegistration registration, @Nullable Consumer<Path> visitedFile) throws IOException {
        if (!registration.filter.recursive()) {
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
        private final @Nullable WatchService watchService;
        private WatchKeyRegistrar registrar = defaultRegistrar();
        private Duration checkInterval = Duration.ofMillis(300);
        private Duration quietPeriod = Duration.ofMillis(120);
        private String threadName = "micronaut-filewatch-thread";
        private boolean closeWatchServiceOnClose = true;

        private Builder(@Nullable WatchService watchService) {
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
         * Whether closing the watcher closes the watch service, which it does by default. A service the caller owns,
         * or the native macOS service, closing which has crashed the JVM, is left open with {@code false}.
         *
         * @param closeWatchServiceOnClose Whether to close the service with the watcher
         * @return This builder
         */
        public Builder closeWatchServiceOnClose(boolean closeWatchServiceOnClose) {
            this.closeWatchServiceOnClose = closeWatchServiceOnClose;
            return this;
        }

        /**
         * @return The watcher, not yet started
         * @throws UncheckedIOException if the builder creates the watch service and that fails
         */
        public DirectoryWatcher build() {
            WatchService service = watchService;
            if (service == null) {
                try {
                    service = FileSystems.getDefault().newWatchService();
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot create a watch service", e);
                }
            }
            return new DirectoryWatcher(this, service);
        }
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

    /**
     * A request of {@link #directory(Path)}; every terminal operation registers a snapshot of it.
     */
    private final class Request implements FileWatcher.WatchRequest {
        private final Path root;
        private boolean recursive = true;
        private final Set<String> includes = new LinkedHashSet<>();
        private final Set<String> excludes = new LinkedHashSet<>();

        Request(Path root) {
            this.root = root;
        }

        @Override
        public FileWatcher.WatchRequest recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        @Override
        public FileWatcher.WatchRequest include(String... globs) {
            includes.addAll(Arrays.asList(globs));
            return this;
        }

        @Override
        public FileWatcher.WatchRequest exclude(String... globs) {
            excludes.addAll(Arrays.asList(globs));
            return this;
        }

        @Override
        public FileWatcherRegistration watchAsync(Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener) {
            return registerRoot(root, new WatchFilter(recursive, includes, excludes), listener);
        }
    }

    private final class DirectoryRegistration implements FileWatcherRegistration {
        private final Path root;
        private final WatchFilter filter;
        private final Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener;
        private volatile boolean registrationActive = true;
        private final List<WatchedDirectory> directories = new CopyOnWriteArrayList<>();
        /**
         * Changes waiting for the quiet period to elapse, merged by path in the order first observed. Only the watch
         * thread touches it.
         */
        private final Map<Path, FileChange> pending = new LinkedHashMap<>();
        /**
         * Changes that arrived while the listener's stage was pending, merged by path. Guarded by {@code this}.
         */
        private final Map<Path, FileChange> held = new LinkedHashMap<>();
        /**
         * Whether a batch was handed to the listener whose stage has not completed. Guarded by {@code this}.
         */
        private boolean inFlight;
        /**
         * The thread calling the listener right now, which {@link #close()} waits for. Guarded by {@code this}.
         */
        private @Nullable Thread delivering;

        DirectoryRegistration(Path root, WatchFilter filter, Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener) {
            this.root = root;
            this.filter = filter;
            this.listener = listener;
        }

        @Override
        public Path root() {
            return root;
        }

        @Override
        public boolean isActive() {
            return registrationActive;
        }

        @Override
        public void close() {
            if (deactivate()) {
                synchronized (DirectoryWatcher.this) {
                    registrations.remove(this);
                    unregister(this);
                }
            }
        }

        /**
         * Stops deliveries and waits for a call of the listener under way, unless that call is the caller or the
         * caller is interrupted, which stops the wait and keeps the interrupt flag set.
         *
         * @return Whether this call deactivated the registration
         */
        boolean deactivate() {
            boolean deactivated;
            synchronized (this) {
                deactivated = registrationActive;
                registrationActive = false;
                held.clear();
                while (delivering != null && delivering != Thread.currentThread()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
            return deactivated;
        }

        boolean covers(Path absolute) {
            if (!absolute.startsWith(root)) {
                return false;
            }
            if (filter.recursive()) {
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
            return parent != null && owns(parent) && filter.accepts(root.relativize(absolute));
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
            return !filter.excludesDirectory(root.relativize(absoluteDirectory));
        }

        /**
         * Records a change until the quiet period elapsed. Called on the watch thread.
         */
        void record(Path path, WatchEventType type) {
            merge(pending, path, type);
        }

        /**
         * @return The changes recorded since the last call. Called on the watch thread.
         */
        List<FileChange> takePending() {
            if (pending.isEmpty()) {
                return List.of();
            }
            List<FileChange> changes = List.copyOf(pending.values());
            pending.clear();
            return changes;
        }

        /**
         * Hands the changes to the listener, or holds them back while its stage is pending. Called on the watch thread.
         */
        void offer(List<FileChange> changes) {
            synchronized (this) {
                if (!registrationActive) {
                    return;
                }
                if (inFlight) {
                    for (FileChange change : changes) {
                        merge(held, change.path(), change.type());
                    }
                    return;
                }
                inFlight = true;
            }
            dispatch(changes);
        }

        /**
         * Delivers what was held back while the stage was pending. Called on the watch thread.
         */
        void deliverHeld() {
            List<FileChange> changes;
            synchronized (this) {
                if (!registrationActive || held.isEmpty()) {
                    inFlight = false;
                    held.clear();
                    return;
                }
                changes = List.copyOf(held.values());
                held.clear();
            }
            dispatch(changes);
        }

        /**
         * Merges a later change of a path into the changes of this registration.
         */
        private static void merge(Map<Path, FileChange> changes, Path path, WatchEventType type) {
            FileChange existing = changes.get(path);
            if (existing == null) {
                changes.put(path, new FileChange(path, type));
                return;
            }
            FileChange merged = existing.merge(type);
            if (merged == null) {
                // created and deleted again: this registration was never told the path existed
                changes.remove(path);
            } else {
                changes.put(path, merged);
            }
        }

        private void dispatch(List<FileChange> changes) {
            synchronized (this) {
                if (!registrationActive) {
                    inFlight = false;
                    return;
                }
                delivering = Thread.currentThread();
            }
            CompletionStage<?> stage = null;
            try {
                stage = listener.apply(new FileChangeBatch(root, changes));
            } catch (RuntimeException e) {
                logFailure(e);
            } finally {
                synchronized (this) {
                    delivering = null;
                    notifyAll();
                }
            }
            if (stage == null) {
                completed();
                return;
            }
            pendingStages.incrementAndGet();
            stage.whenComplete((ignored, failure) -> {
                pendingStages.decrementAndGet();
                if (failure != null) {
                    logFailure(failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure);
                }
                completed();
            });
        }

        /**
         * The listener's work for the last batch is done, on whichever thread completed its stage.
         */
        private void completed() {
            synchronized (this) {
                if (!registrationActive || held.isEmpty()) {
                    inFlight = false;
                    held.clear();
                    return;
                }
            }
            // still in flight: the watch thread delivers the held changes, so that listeners only ever run there
            ready.add(this);
        }

        private void logFailure(Throwable failure) {
            if (LOG.isErrorEnabled()) {
                LOG.error("File watch listener for {} failed: {}", root, failure.getMessage(), failure);
            }
        }
    }
}
