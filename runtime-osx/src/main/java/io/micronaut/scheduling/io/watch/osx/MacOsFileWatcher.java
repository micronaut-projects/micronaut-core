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
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.scheduling.io.watch.DefaultFileWatcher;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;

/**
 * The file watcher of the context over the native macOS service, which it registers directories with as
 * {@link WatchablePath}s and leaves open when it closes, because closing it has crashed the JVM.
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@Singleton
@Replaces(DefaultFileWatcher.class)
@Requires(classes = {MacOSXListeningWatchService.class, Library.class})
@Requires(beans = WatchService.class)
public class MacOsFileWatcher extends DefaultFileWatcher {

    /**
     * @param watchServices The watch service, obtained by the first registration
     * @param configuration The configuration, if the watch paths are set
     */
    public MacOsFileWatcher(BeanProvider<WatchService> watchServices, @Nullable FileWatchConfiguration configuration) {
        super(watchServices, configuration);
    }

    @Override
    protected DirectoryWatcher.Builder configure(DirectoryWatcher.Builder builder, WatchService watchService) {
        if (!(watchService instanceof MacOSXListeningWatchService)) {
            // the factory fell back to the JDK's service, which is registered and closed as usual
            return builder;
        }
        return builder
            .registrar((directory, service) -> new WatchablePath(directory).register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY
            ))
            .closeWatchServiceOnClose(false);
    }
}
