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

import io.micronaut.context.LifeCycle;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.io.watch.FileChangeBatch;
import io.micronaut.scheduling.io.watch.FileWatcher;
import io.micronaut.scheduling.io.watch.WatchOptions;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * The {@link FileWatcher} bean of one application context in development mode: a view of the one watcher the
 * {@link DevRuntime} runs for the process, so that the launcher's roots and every module's registrations share one
 * {@link java.nio.file.WatchService}, one thread and one debounce. A registration over a directory a root already
 * covers adds a listener; one outside the roots adds the directory to the same service, where it stays for the life
 * of the process so that each generation's registration of it is a listener too.
 *
 * <p>The view closes every registration made through it when its context stops, so a retired generation keeps no
 * listener, and calls the listeners with the context's class loader as the thread's context loader.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
final class DevFileWatcher implements FileWatcher, LifeCycle<DevFileWatcher> {

    private final FileWatcher watcher;
    private final Map<Path, Registration> pinned;
    private final ClassLoader classLoader;
    private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();
    private volatile boolean running = true;

    /**
     * @param watcher The process's watcher
     * @param pinned The directories outside the roots registered for the process, shared by the views of every context
     * @param classLoader The class loader of the context the view belongs to
     */
    DevFileWatcher(FileWatcher watcher, Map<Path, Registration> pinned, ClassLoader classLoader) {
        this.watcher = watcher;
        this.pinned = pinned;
        this.classLoader = classLoader;
    }

    @Override
    public Registration watch(Path root, WatchOptions options, Consumer<FileChangeBatch> listener) {
        if (!running) {
            throw new IllegalStateException("The file watcher of a stopped context cannot watch " + root);
        }
        pin(root);
        Registration registration = new ViewRegistration(watcher.watch(root, options, batch -> deliver(listener, batch)));
        registrations.add(registration);
        if (!running) {
            // stopped while the registration was made: nothing would close it later
            registration.close();
            throw new IllegalStateException("The file watcher of a stopped context cannot watch " + root);
        }
        return registration;
    }

    /**
     * Keeps a directory outside the roots registered for the life of the process, so that the registration of a context
     * adds a listener over it rather than a watch key of its own. A key, once cancelled, cannot be had again for the same
     * directory from the native macOS service, which would leave the next generation deaf to the directory it watches;
     * kept, every generation's registration reuses the key of the first. The pin is recursive whatever the request
     * asked for, so that a later recursive registration of the directory, or of one below it, finds every key in place.
     */
    private void pin(Path root) {
        Path absolute = root.toAbsolutePath().normalize();
        if (watcher.isWatching(absolute)) {
            return;
        }
        pinned.computeIfAbsent(absolute, directory -> watcher.watch(directory, WatchOptions.DEFAULT, batch -> { }));
    }

    @Override
    public boolean isWatching(Path path) {
        return watcher.isWatching(path);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public DevFileWatcher stop() {
        running = false;
        for (Registration registration : List.copyOf(registrations)) {
            registration.close();
        }
        return this;
    }

    /**
     * @return The registrations made through this view that are still open
     */
    int openRegistrations() {
        return registrations.size();
    }

    private void deliver(Consumer<FileChangeBatch> listener, FileChangeBatch batch) {
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        thread.setContextClassLoader(classLoader);
        try {
            listener.accept(batch);
        } finally {
            thread.setContextClassLoader(previous);
        }
    }

    /**
     * A registration that leaves the view when it is closed.
     */
    private final class ViewRegistration implements Registration {
        private final Registration delegate;

        private ViewRegistration(Registration delegate) {
            this.delegate = delegate;
        }

        @Override
        public Path root() {
            return delegate.root();
        }

        @Override
        public WatchOptions options() {
            return delegate.options();
        }

        @Override
        public boolean isActive() {
            return delegate.isActive();
        }

        @Override
        public void close() {
            registrations.remove(this);
            delegate.close();
        }
    }
}
