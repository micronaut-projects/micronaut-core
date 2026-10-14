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

import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.io.watch.event.FileChangedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Watches the directories of {@link FileWatchConfiguration#getPaths()} and publishes a
 * {@link FileChangedEvent} for every change. It registers them with the {@link FileWatcher} of the application
 * context, which other components register their own directories with.
 *
 * <p>The changes are published after the configured {@link FileWatchConfiguration#getQuietPeriod() quiet period},
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
@Requires(beans = FileWatcher.class)
@Parallel
@Singleton
public class DefaultWatchThread implements LifeCycle<DefaultWatchThread> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWatchThread.class);
    private final FileWatchConfiguration configuration;
    private final ApplicationEventPublisher eventPublisher;
    private final FileWatcher fileWatcher;
    /**
     * The service and the watcher of a thread made with the deprecated constructor, which watches on its own.
     */
    private final @Nullable WatchService watchService;
    private final @Nullable DirectoryWatcher ownWatcher;
    private final List<FileWatcherRegistration> registrations = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    /**
     * Creates the thread over the file watcher of the context.
     *
     * @param eventPublisher The event publisher
     * @param configuration the configuration
     * @param fileWatcher the file watcher of the context
     * @since 5.3.0
     */
    @Inject
    protected DefaultWatchThread(
            ApplicationEventPublisher eventPublisher,
            FileWatchConfiguration configuration,
            FileWatcher fileWatcher) {
        this.eventPublisher = eventPublisher;
        this.configuration = configuration;
        this.fileWatcher = fileWatcher;
        this.watchService = null;
        this.ownWatcher = null;
    }

    /**
     * Creates a thread that watches with a watcher of its own over the given service, registering directories with
     * {@link #registerPath(Path)} and closing the service with {@link #closeWatchService()}.
     *
     * @param eventPublisher The event publisher
     * @param configuration the configuration
     * @param watchService the watch service
     * @deprecated Use {@link #DefaultWatchThread(ApplicationEventPublisher, FileWatchConfiguration, FileWatcher)}, so
     * that the context has one watcher
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    protected DefaultWatchThread(
            ApplicationEventPublisher eventPublisher,
            FileWatchConfiguration configuration,
            WatchService watchService) {
        this.eventPublisher = eventPublisher;
        this.configuration = configuration;
        this.watchService = watchService;
        DirectoryWatcher watcher = DirectoryWatcher.builder(watchService)
            .registrar((directory, service) -> registerPath(directory))
            .checkInterval(configuration.getCheckInterval())
            .quietPeriod(configuration.getQuietPeriod())
            .closeWatchServiceOnClose(false)
            .build();
        this.ownWatcher = watcher;
        this.fileWatcher = watcher;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    @PostConstruct
    public DefaultWatchThread start() {
        try {
            for (Path path : configuration.getPaths()) {
                if (Files.isDirectory(path)) {
                    registrations.add(fileWatcher.directory(path).watch(this::publish));
                }
            }
            if (ownWatcher != null) {
                ownWatcher.start();
            }
            running = true;
        } catch (RuntimeException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error starting file watch service: {}", e.getMessage(), e);
            }
        }
        return this;
    }

    @Override
    public DefaultWatchThread stop() {
        running = false;
        for (FileWatcherRegistration registration : registrations) {
            registration.close();
        }
        registrations.clear();
        if (ownWatcher != null) {
            ownWatcher.close();
            closeWatchService();
        }
        return this;
    }

    @Override
    @PreDestroy
    public void close() {
        stop();
    }

    /**
     * @return The watch service of a thread made with the deprecated constructor
     * @throws IllegalStateException if the thread uses the file watcher of the context, which owns the service
     * @deprecated The thread registers with the {@link FileWatcher} of the context
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    public WatchService getWatchService() {
        if (watchService == null) {
            throw new IllegalStateException("The watch thread uses the FileWatcher of the context, which owns the watch service");
        }
        return watchService;
    }

    /**
     * Closes the watch service of a thread made with the deprecated constructor.
     *
     * @deprecated The thread registers with the {@link FileWatcher} of the context
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
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
     * Registers a path to watch with the service of a thread made with the deprecated constructor.
     *
     * @param dir The directory to watch
     * @return The watch key
     * @throws IOException if an error occurs.
     * @deprecated The thread registers with the {@link FileWatcher} of the context
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    protected WatchKey registerPath(Path dir) throws IOException {
        return dir.register(getWatchService(),
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
