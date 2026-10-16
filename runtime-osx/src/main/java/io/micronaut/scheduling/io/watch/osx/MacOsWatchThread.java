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
package io.micronaut.scheduling.io.watch.osx;

import com.sun.jna.Library;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;
import io.micronaut.context.annotation.Parallel;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.io.watch.DefaultWatchThread;
import io.micronaut.scheduling.io.watch.FileWatchCondition;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import io.micronaut.scheduling.io.watch.FileWatcher;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Replaces the {@link DefaultWatchThread} for Mac OS X to use native file watch.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Replaces(DefaultWatchThread.class)
@Parallel
@Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(condition = FileWatchCondition.class)
@Requires(classes = {MacOSXListeningWatchService.class, Library.class})
@Singleton
public class MacOsWatchThread extends DefaultWatchThread {

    /**
     * Creates the thread over the file watcher of the context, which registers with the native service through
     * {@link MacOsFileWatcher}.
     *
     * @param eventPublisher The event publisher
     * @param configuration the configuration
     * @param fileWatcher the file watcher of the context
     * @since 5.3.0
     */
    @Inject
    public MacOsWatchThread(
        ApplicationEventPublisher eventPublisher,
        FileWatchConfiguration configuration,
        FileWatcher fileWatcher) {
        super(eventPublisher, configuration, fileWatcher);
    }

    /**
     * Creates a thread that watches the native service with a watcher of its own.
     *
     * @param eventPublisher The event publisher
     * @param configuration the configuration
     * @param watchService the watch service
     * @deprecated Use {@link #MacOsWatchThread(ApplicationEventPublisher, FileWatchConfiguration, FileWatcher)}, so
     * that the context has one watcher
     */
    @Deprecated(since = "5.3.0", forRemoval = true)
    public MacOsWatchThread(
        ApplicationEventPublisher eventPublisher,
        FileWatchConfiguration configuration,
        WatchService watchService) {
        super(eventPublisher, configuration, watchService);
    }

    @Override
    @Deprecated(since = "5.3.0", forRemoval = true)
    protected WatchKey registerPath(Path dir) throws IOException {
        WatchablePath watchPath = new WatchablePath(dir);
        return watchPath.register(
            getWatchService(),
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
    }

    @Override
    @Deprecated(since = "5.3.0", forRemoval = true)
    protected void closeWatchService() {
        // no-op - for some reason this causes a JVM crash if not overridden
    }
}
