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
import io.micronaut.core.util.NativeImageUtils;
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
 * without it the JDK's service is registered at its highest sensitivity, two seconds. A native image cannot load
 * that module's service, which uses JNA, at runtime: on macOS it compares the watched directories every
 * {@value #NATIVE_POLL_MILLIS} milliseconds instead ({@link PollingWatchService}).
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

    /**
     * How often a native image on macOS compares the watched directories, in milliseconds.
     */
    static final int NATIVE_POLL_MILLIS = 250;

    private static final Logger LOG = LoggerFactory.getLogger(DevWatchService.class);
    private static final String MAC_SERVICE = "io.methvin.watchservice.MacOSXListeningWatchService";

    /**
     * @return The best service this JVM offers
     * @throws IOException if no service can be created
     */
    static DevWatchService create() throws IOException {
        ClassLoader loader = DevWatchService.class.getClassLoader();
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        if (mac && NativeImageUtils.inImageRuntimeCode()) {
            PollingWatchService polling = new PollingWatchService(java.time.Duration.ofMillis(NATIVE_POLL_MILLIS));
            return new DevWatchService(polling, (directory, service) -> polling.register(directory), null);
        }
        if (mac && ClassUtils.isPresent(MAC_SERVICE, loader)) {
            try {
                return MacOs.create();
            } catch (Exception | LinkageError e) {
                LOG.warn("The native macOS watch service could not be started ({}): polling instead", e.getMessage());
            }
        }
        WatchService service = FileSystems.getDefault().newWatchService();
        DirectoryWatcher.WatchKeyRegistrar registrar;
        try {
            // linked rather than looked up by name: a native image finds by name only the classes registered for reflection
            registrar = HighSensitivity.registrar();
        } catch (LinkageError e) {
            registrar = DirectoryWatcher.defaultRegistrar();
        }
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
            // resolved now, so that a JDK without the modifier fails here rather than at the first registration
            java.nio.file.WatchEvent.Modifier high = com.sun.nio.file.SensitivityWatchEventModifier.HIGH;
            return (directory, service) -> directory.register(
                service,
                new java.nio.file.WatchEvent.Kind<?>[] {StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE, StandardWatchEventKinds.ENTRY_MODIFY},
                high
            );
        }
    }
}
