/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.runtime.server.restart;

import io.micronaut.context.LifeCycle;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.server.EmbeddedServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple watch service that simply stops the server if any changes occur. It is up to an external tool to restart the server.
 *
 * <p>For example with Gradle you use <code>./gradlew run --continuous</code></p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Requires(property = DevelopmentRestartWatch.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
@Requires(beans = EmbeddedServer.class)
@Context
public class DevelopmentRestartWatch implements LifeCycle<DevelopmentRestartWatch> {
    /**
     * Setting to enable and disable server watch.
     */
    public static final String ENABLED = "micronaut.server.watch.enabled";

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentRestartWatch.class);
    private WatchService watchService;
    private Collection<WatchKey> watchKeys = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final EmbeddedServer embeddedServer;
    private long sleepTime = 500;

    /**
     * Default constructor.
     *
     * @param embeddedServer The embedded server
     */
    public DevelopmentRestartWatch(EmbeddedServer embeddedServer) {
        this.embeddedServer = embeddedServer;
    }

    @Override
    public boolean isRunning() {
        return active.get();
    }

    @Override
    @PostConstruct
    public DevelopmentRestartWatch start() {
        try {

            File dir = new File("src/main");
            if (dir.exists()) {
                this.watchService = FileSystems.getDefault().newWatchService();
                final Path p = dir.toPath();
                addWatchDirectory(p);
            }
            new Thread(() -> {
                while (active.get()) {
                    try {
                        WatchKey watchKey = watchService.poll(sleepTime, TimeUnit.MILLISECONDS);
                        if (watchKey != null && watchKeys.contains(watchKey)) {
                            List<WatchEvent<?>> watchEvents = watchKey.pollEvents();
                            for (WatchEvent<?> watchEvent : watchEvents) {
                                WatchEvent.Kind<?> kind = watchEvent.kind();
                                if (kind == StandardWatchEventKinds.OVERFLOW) {
                                    if (LOG.isWarnEnabled()) {
                                        LOG.warn("WatchService Overflow occurred");
                                    }
                                    continue;
                                }
                                if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                                    active.set(false);
                                    embeddedServer.stop();
                                }
                            }
                            watchKey.reset();
                        }
                    } catch (InterruptedException | ClosedWatchServiceException e) {
                        // ignore
                    }
                }
                try {
                    watchService.close();
                } catch (IOException e) {
                    LOG.debug("Exception while closing watchService", e);
                }
            }, "micronaut-development-filewatch").start();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    @PreDestroy
    public DevelopmentRestartWatch stop() {
        active.set(false);
        return this;
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
                WatchKey watchKey = dir.register(watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
                watchKeys.add(watchKey);
                return FileVisitResult.CONTINUE;
            }
        });
    }

}
