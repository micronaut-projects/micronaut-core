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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * A watch service that compares each registered directory's entries with their previous state at a fixed interval.
 * The development runtime watches with it in a native image on macOS, where the JDK's own service polls every two
 * seconds at its highest sensitivity and the native FSEvents service of {@code micronaut-runtime-osx}, which uses JNA,
 * cannot be loaded at runtime.
 *
 * <p>Like the JDK's services it watches one directory per key, without its subdirectories, and reports names
 * relative to it: a created, deleted, or modified entry, by its modification time and size.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class PollingWatchService implements WatchService {

    private final Map<Path, Key> keys = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<Key> signalled = new LinkedBlockingQueue<>();
    private final Thread poller;
    private final long intervalMillis;
    private volatile boolean closed;

    /**
     * Creates the service and starts polling.
     *
     * @param interval How long to wait between two comparisons
     */
    PollingWatchService(Duration interval) {
        this.intervalMillis = Math.max(1, interval.toMillis());
        this.poller = new Thread(this::pollLoop, "micronaut-dev-watch-poller");
        poller.setDaemon(true);
        poller.start();
    }

    /**
     * Registers a directory, or returns its key if it is registered already.
     *
     * @param directory The directory
     * @return The key
     * @throws IOException if the directory cannot be read
     */
    WatchKey register(Path directory) throws IOException {
        if (closed) {
            throw new ClosedWatchServiceException();
        }
        Path absolute = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(absolute)) {
            throw new IOException("Not a directory: " + absolute);
        }
        try {
            return keys.compute(absolute, (path, existing) -> existing != null && existing.isValid() ? existing : new Key(path, entries(path)));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public void close() {
        closed = true;
        poller.interrupt();
        for (Key key : keys.values()) {
            key.cancel();
        }
        keys.clear();
    }

    @Override
    public @Nullable WatchKey poll() {
        checkOpen();
        return signalled.poll();
    }

    @Override
    public @Nullable WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        checkOpen();
        return signalled.poll(timeout, unit);
    }

    @Override
    public WatchKey take() throws InterruptedException {
        checkOpen();
        return signalled.take();
    }

    private void checkOpen() {
        if (closed) {
            throw new ClosedWatchServiceException();
        }
    }

    private void pollLoop() {
        while (!closed) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                return;
            }
            for (Key key : keys.values()) {
                key.compare();
            }
        }
    }

    private static Map<String, Entry> entries(Path directory) {
        Map<String, Entry> entries = new HashMap<>();
        try (Stream<Path> children = Files.list(directory)) {
            children.forEach(child -> {
                try {
                    BasicFileAttributes attributes = Files.readAttributes(child, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                    entries.put(child.getFileName().toString(), new Entry(attributes.lastModifiedTime().toMillis(), attributes.size(), attributes.isDirectory()));
                } catch (IOException e) {
                    // deleted while listed: absent
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }

    /**
     * The state of an entry the comparison looks at.
     *
     * @param modified The modification time, in milliseconds
     * @param size The size
     * @param directory Whether it is a directory
     */
    private record Entry(long modified, long size, boolean directory) {
    }

    /**
     * An event: the kind, and the entry's name relative to the directory.
     *
     * @param kind The kind
     * @param context The name
     */
    private record Event(Kind<Path> kind, Path context) implements WatchEvent<Path> {
        @Override
        public int count() {
            return 1;
        }
    }

    /**
     * The key of one directory: signalled when a comparison finds a difference, and queued until reset.
     */
    private final class Key implements WatchKey {
        private final Path directory;
        private Map<String, Entry> entries;
        private final List<WatchEvent<?>> events = new ArrayList<>();
        private boolean queued;
        private volatile boolean valid = true;

        Key(Path directory, Map<String, Entry> entries) {
            this.directory = directory;
            this.entries = entries;
        }

        synchronized void compare() {
            if (!valid) {
                return;
            }
            Map<String, Entry> current;
            try {
                current = Files.isDirectory(directory) ? entries(directory) : null;
            } catch (UncheckedIOException e) {
                current = null;
            }
            if (current == null) {
                // the directory went away: its key is no longer valid, as with the JDK's services
                valid = false;
                keys.remove(directory, this);
                signal();
                return;
            }
            for (Map.Entry<String, Entry> entry : current.entrySet()) {
                Entry previous = entries.get(entry.getKey());
                if (previous == null) {
                    events.add(new Event(StandardWatchEventKinds.ENTRY_CREATE, Path.of(entry.getKey())));
                } else if (!previous.equals(entry.getValue())) {
                    events.add(new Event(StandardWatchEventKinds.ENTRY_MODIFY, Path.of(entry.getKey())));
                }
            }
            for (String name : entries.keySet()) {
                if (!current.containsKey(name)) {
                    events.add(new Event(StandardWatchEventKinds.ENTRY_DELETE, Path.of(name)));
                }
            }
            entries = current;
            if (!events.isEmpty()) {
                signal();
            }
        }

        private void signal() {
            if (!queued) {
                queued = true;
                signalled.add(this);
            }
        }

        @Override
        public boolean isValid() {
            return valid && !closed;
        }

        @Override
        public synchronized List<WatchEvent<?>> pollEvents() {
            List<WatchEvent<?>> taken = new ArrayList<>(events);
            events.clear();
            return taken;
        }

        @Override
        public synchronized boolean reset() {
            if (!isValid()) {
                return false;
            }
            queued = false;
            if (!events.isEmpty()) {
                signal();
            }
            return true;
        }

        @Override
        public void cancel() {
            valid = false;
            keys.remove(directory, this);
        }

        @Override
        public Watchable watchable() {
            return directory;
        }
    }
}
