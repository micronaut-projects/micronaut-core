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

import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.ArgumentUtils;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.Closeable;
import java.nio.file.Path;
import java.nio.file.WatchService;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * The {@link FileWatcher} of an application context: a {@link DirectoryWatcher} over the context's
 * {@link WatchService}, created, and its thread started, by the first registration. It needs neither
 * {@link FileWatchConfiguration#PATHS} nor {@link FileWatchConfiguration#ENABLED}; only setting
 * {@link FileWatchConfiguration#ENABLED} to {@code false}, which removes the watch service, removes it. The
 * {@link FileWatchConfiguration#getCheckInterval() check interval} and the
 * {@link FileWatchConfiguration#getQuietPeriod() quiet period} of the configuration apply when it is present.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
@Singleton
@Requires(notEnv = {Environment.FUNCTION, Environment.ANDROID})
@Requires(beans = WatchService.class)
public class DefaultFileWatcher implements FileWatcher, Closeable {

    private final BeanProvider<WatchService> watchServices;
    private final @Nullable FileWatchConfiguration configuration;
    private @Nullable DirectoryWatcher watcher;
    private boolean closed;

    /**
     * @param watchServices The watch service, obtained by the first registration
     * @param configuration The configuration, if the watch paths are set
     */
    public DefaultFileWatcher(BeanProvider<WatchService> watchServices, @Nullable FileWatchConfiguration configuration) {
        this.watchServices = watchServices;
        this.configuration = configuration;
    }

    @Override
    public WatchRequest directory(Path root) {
        ArgumentUtils.requireNonNull("root", root);
        return new LazyRequest(root);
    }

    @Override
    public boolean isWatching(Path path) {
        DirectoryWatcher current;
        synchronized (this) {
            current = watcher;
        }
        return current != null && current.isWatching(path);
    }

    /**
     * Configures the watcher before it is built. A subclass for a platform's watch service sets the registrar the
     * service needs, and whether it may be closed.
     *
     * @param builder The builder over the context's watch service
     * @param watchService The context's watch service
     * @return The builder
     */
    protected DirectoryWatcher.Builder configure(DirectoryWatcher.Builder builder, WatchService watchService) {
        return builder;
    }

    @Override
    @PreDestroy
    public void close() {
        DirectoryWatcher current;
        synchronized (this) {
            closed = true;
            current = watcher;
            watcher = null;
        }
        if (current != null) {
            current.close();
        }
    }

    private synchronized DirectoryWatcher watcher() {
        if (closed) {
            throw new IllegalStateException("The file watcher is closed");
        }
        DirectoryWatcher current = watcher;
        if (current == null) {
            WatchService watchService = watchServices.get();
            DirectoryWatcher.Builder builder = DirectoryWatcher.builder(watchService);
            if (configuration != null) {
                builder.checkInterval(configuration.getCheckInterval()).quietPeriod(configuration.getQuietPeriod());
            }
            current = configure(builder, watchService).build().start();
            watcher = current;
        }
        return current;
    }

    /**
     * Collects the request until its terminal operation, which creates the watcher if it does not run yet.
     */
    private final class LazyRequest implements WatchRequest {
        private final Path root;
        private boolean recursive = true;
        private final Set<String> includes = new LinkedHashSet<>();
        private final Set<String> excludes = new LinkedHashSet<>();

        LazyRequest(Path root) {
            this.root = root;
        }

        @Override
        public WatchRequest recursive(boolean recursive) {
            this.recursive = recursive;
            return this;
        }

        @Override
        public WatchRequest include(String... globs) {
            includes.addAll(Arrays.asList(globs));
            return this;
        }

        @Override
        public WatchRequest exclude(String... globs) {
            excludes.addAll(Arrays.asList(globs));
            return this;
        }

        @Override
        public FileWatcherRegistration watchAsync(Function<? super FileChangeBatch, ? extends CompletionStage<?>> listener) {
            return watcher().directory(root)
                .recursive(recursive)
                .include(includes.toArray(String[]::new))
                .exclude(excludes.toArray(String[]::new))
                .watchAsync(listener);
        }
    }
}
