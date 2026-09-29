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
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.scheduling.io.watch.DirectoryWatcher;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchService;

/**
 * The watch service the launcher watches with. On macOS the JDK's service polls, every ten seconds
 * by default, so the native FSEvents service of {@code micronaut-runtime-osx} is used when that
 * module is on the development runtime classpath, which the build plugins put there on macOS;
 * without it the JDK's service is registered at its highest sensitivity, two seconds.
 *
 * @param service The service
 * @param registrar How directories are registered with it
 * @param closeAction What closing the watcher does with the service, or null for closing it
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
@NullMarked
record DevWatchService(WatchService service, DirectoryWatcher.WatchKeyRegistrar registrar, @Nullable Runnable closeAction) {

    private static final Logger LOG = LoggerFactory.getLogger(DevWatchService.class);
    private static final String MAC_SERVICE = "io.methvin.watchservice.MacOSXListeningWatchService";
    private static final String SENSITIVITY_MODIFIER = "com.sun.nio.file.SensitivityWatchEventModifier";

    /**
     * @return The best service this JVM offers
     * @throws IOException if no service can be created
     */
    static DevWatchService create() throws IOException {
        ClassLoader loader = DevWatchService.class.getClassLoader();
        if (System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac") && ClassUtils.isPresent(MAC_SERVICE, loader)) {
            try {
                return MacOs.create();
            } catch (Exception | LinkageError e) {
                LOG.warn("The native macOS watch service could not be started ({}): polling instead", e.getMessage());
            }
        }
        WatchService service = FileSystems.getDefault().newWatchService();
        DirectoryWatcher.WatchKeyRegistrar registrar = ClassUtils.isPresent(SENSITIVITY_MODIFIER, loader) ? HighSensitivity.registrar() : DirectoryWatcher.defaultRegistrar();
        return new DevWatchService(service, registrar, null);
    }

    /**
     * Loaded only when the native macOS service is present.
     */
    private static final class MacOs {
        static DevWatchService create() throws IOException {
            io.methvin.watchservice.MacOSXListeningWatchService service = new io.methvin.watchservice.MacOSXListeningWatchService();
            DirectoryWatcher.WatchKeyRegistrar registrar = (directory, watchService) -> new io.methvin.watchservice.WatchablePath(directory).register(
                watchService, StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY);
            // closing the native service has crashed the JVM; micronaut-runtime-osx leaves it open as well
            return new DevWatchService(service, registrar, () -> LOG.debug("The native macOS watch service is left open"));
        }
    }

    /**
     * Loaded only when the modifier exists.
     */
    private static final class HighSensitivity {
        static DirectoryWatcher.WatchKeyRegistrar registrar() {
            return (directory, service) -> directory.register(
                service,
                new java.nio.file.WatchEvent.Kind<?>[] {StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY},
                com.sun.nio.file.SensitivityWatchEventModifier.HIGH
            );
        }
    }
}
