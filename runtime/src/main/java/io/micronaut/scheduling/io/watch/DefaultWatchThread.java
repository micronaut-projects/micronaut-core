/*
 * Copyright 2017-2020 original authors
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Singleton;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple watch service that simply stops the server if any changes occur. It is up to an external tool to watch the server.
 *
 * <p>For example with Gradle you use <code>./gradlew run --continuous</code></p>
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
public class DefaultWatchThread implements LifeCycle<DefaultWatchThread> {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultWatchThread.class);
    private final FileWatchConfiguration configuration;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final ApplicationEventPublisher eventPublisher;
    private final WatchService watchService;
    private Collection<WatchKey> watchKeys = new ConcurrentLinkedQueue<>();

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
    }

    @Override
    public boolean isRunning() {
        return active.get();
    }

    @Override
    @PostConstruct
    public DefaultWatchThread start() {
        try {
            final List<Path> paths = configuration.getPaths();
            if (!paths.isEmpty()) {
                for (Path path : paths) {
                    if (path.toFile().exists()) {
                        addWatchDirectory(path);
                    }
                }
            }

            if (!watchKeys.isEmpty()) {
                new Thread(() -> {
                    while (active.get()) {
                        try {
                            WatchKey watchKey = watchService.poll(configuration.getCheckInterval().toMillis(), TimeUnit.MILLISECONDS);
                            if (watchKey != null && watchKeys.contains(watchKey)) {
                                List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                                for (WatchEvent<?> watchEvent : watchEvents) {
                                    WatchEvent.Kind<?> kind = watchEvent.kind();
                                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                                        if (LOG.isWarnEnabled()) {
                                            LOG.warn("WatchService Overflow occurred");
                                        }
                                    } else {
                                        final Object context = watchEvent.context();
                                        if (context instanceof Path) {

                                            if (LOG.isDebugEnabled()) {
                                                LOG.debug("File at path {} changed. Firing change event: {}", context, kind);
                                            }
                                            eventPublisher.publishEvent(new FileChangedEvent(
                                                    (Path) context,
                                                    kind
                                            ));
                                        }
                                    }
                                }
                                watchKey.reset();
                            }
                        } catch (InterruptedException | ClosedWatchServiceException e) {
                            // ignore
                        }
                    }
                }, "micronaut-filewatch-thread").start();
            }
        } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
                LOG.error("Error starting file watch service: " + e.getMessage(), e);
            }
        }
        return this;
    }

    @Override
    public DefaultWatchThread stop() {
        active.set(false);
        closeWatchService();
        return this;
    }

    @Override
    @PreDestroy
    public void close() {
        stop();
    }

    /**
     * @return The watch service used.
     */
    public @NonNull WatchService getWatchService() {
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
                LOG.error("Error stopping file watch service: " + e.getMessage(), e);
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
    protected @NonNull WatchKey registerPath(@NonNull Path dir) throws IOException {
        return dir.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
        );
    }

    private boolean isValidDirectoryToMonitor(File file) {
        return file.isDirectory() && !file.isHidden() && !file.getName().startsWith(".");
    }

    private Path addWatchDirectory(Path p) throws IOException {
        return Files.walkFileTree(p, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException {

                if (!isValidDirectoryToMonitor(dir.toFile())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                WatchKey watchKey = registerPath(dir);
                watchKeys.add(watchKey);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
