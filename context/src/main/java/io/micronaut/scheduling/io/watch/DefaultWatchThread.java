/*
 * Copyright 2017-2019 original authors
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

import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.io.watch.event.FileChangedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.function.Consumer;

/**
 * Watches the directories of {@link FileWatchConfiguration#getPaths()} and publishes a
 * {@link FileChangedEvent} for every change. It is also the {@link FileWatcher} of the application
 * context, so other components register interest in directories with it instead of watching
 * on their own.
 *
 * <p>The thread delivers changes after the configured {@link FileWatchConfiguration#getQuietPeriod() quiet period},
 * so the events of one save arrive together, and every published path is absolute.</p>
 *
 * <p>It is up to an external tool to restart the server if that is wanted; for example with Gradle
 * you use <code>./gradlew run --continuous</code>.</p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Requires(property = FileWatchConfiguration.PATHS)
@Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Requires(condition = FileWatchCondition.class)
@Requires(notEnv = {Environment.FUNCTION, Environment.ANDROID})
@Requires(beans = WatchService.class)
@Parallel
@Singleton
public class DefaultWatchThread implements LifeCycle<DefaultWatchThread>, FileWatcher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWatchThread.class);
    private final FileWatchConfiguration configuration;
    private final ApplicationEventPublisher eventPublisher;
    private final WatchService watchService;
    private final DirectoryWatcher watcher;

    /**
     * Default constructor.
     *
     * @param eventPublisher The event publisher
     * @param configuration the configuration
     * @param watchService the watch service
     */
    protected DefaultWatchThread(
            ApplicationEventPublisher eventPublisher,
            FileWatchConfiguration configuration,
            WatchService watchService) {
        this.eventPublisher = eventPublisher;
        this.configuration = configuration;
        this.watchService = watchService;
        this.watcher = DirectoryWatcher.builder(watchService)
            .registrar((directory, service) -> registerPath(directory))
            .checkInterval(configuration.getCheckInterval())
            .quietPeriod(configuration.getQuietPeriod())
            .closeAction(this::closeWatchService)
            .build();
    }

    @Override
    public boolean isRunning() {
        return watcher.isRunning();
    }

    @Override
    @PostConstruct
    public DefaultWatchThread start() {
        try {
            final List<Path> paths = configuration.getPaths();
            for (Path path : paths) {
                if (Files.isDirectory(path)) {
                    watcher.watch(path, WatchOptions.DEFAULT, this::publish);
                }
            }
            watcher.start();
        } catch (RuntimeException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error starting file watch service: {}", e.getMessage(), e);
            }
        }
        return this;
    }

    @Override
    public DefaultWatchThread stop() {
        watcher.close();
        return this;
    }

    @Override
    @PreDestroy
    public void close() {
        stop();
    }

    @Override
    public Registration watch(Path root, WatchOptions options, Consumer<FileChangeBatch> listener) {
        return watcher.watch(root, options, listener);
    }

    @Override
    public boolean isWatching(Path path) {
        return watcher.isWatching(path);
    }

    /**
     * @return The watch service used.
     */
    public WatchService getWatchService() {
        return watchService;
    }

    /**
     * Closes the watch service.
     */
    protected void closeWatchService() {
        try {
            getWatchService().close();
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error stopping file watch service: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Registers a patch to watch.
     *
     * @param dir The directory to watch
     * @return The watch key
     * @throws IOException if an error occurs.
     */
    protected WatchKey registerPath(Path dir) throws IOException {
        return dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
    }

    @SuppressWarnings("unchecked")
    private void publish(FileChangeBatch batch) {
        for (FileChange change : batch.changes()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("File at path {} changed. Firing change event: {}", change.path(), change.type());
            }
            eventPublisher.publishEvent(new FileChangedEvent(change.path(), change.type()));
        }
    }
}
