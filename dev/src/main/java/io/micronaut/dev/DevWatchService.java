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
 * The watch service the launcher watches with. On macOS the JDK's service polls, every ten seconds by default and
 * every two at its highest sensitivity, so the native FSEvents service of {@code micronaut-runtime-osx} is used when
 * that module is on the development runtime classpath, which the build plugins put there on macOS. Without it, and in a
 * native image, which cannot load that module's service at runtime because it uses JNA, the launcher compares the
 * watched directories every {@value #POLL_MILLIS} milliseconds ({@link PollingWatchService}). Elsewhere the JDK's
 * service is native, and is registered at its highest sensitivity.
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
     * How often the launcher compares the watched directories on macOS without the native service, in milliseconds.
     */
    static final int POLL_MILLIS = 250;

    private static final Logger LOG = LoggerFactory.getLogger(DevWatchService.class);
    private static final String MAC_SERVICE = "io.methvin.watchservice.MacOSXListeningWatchService";

    /**
     * @return The best service this JVM offers
     * @throws IOException if no service can be created
     */
    static DevWatchService create() throws IOException {
        boolean mac = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac");
        return create(mac, NativeImageUtils.inImageRuntimeCode(), ClassUtils.isPresent(MAC_SERVICE, DevWatchService.class.getClassLoader()));
    }

    /**
     * The service for a platform.
     *
     * @param mac Whether the platform is macOS
     * @param nativeImage Whether this runs in a native image, which cannot load the native macOS service
     * @param macServicePresent Whether the native macOS service is on the classpath
     * @return The service
     * @throws IOException if no service can be created
     */
    static DevWatchService create(boolean mac, boolean nativeImage, boolean macServicePresent) throws IOException {
        if (mac) {
            if (!nativeImage && macServicePresent) {
                try {
                    return MacOs.create();
                } catch (Exception | LinkageError e) {
                    LOG.warn("The native macOS watch service could not be started ({}): polling instead", e.getMessage());
                }
            }
            // the JDK's own service would notice a save two seconds later at best
            return polling();
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
     * @return A service comparing the watched directories every {@value #POLL_MILLIS} milliseconds
     */
    static DevWatchService polling() {
        PollingWatchService polling = new PollingWatchService(java.time.Duration.ofMillis(POLL_MILLIS));
        return new DevWatchService(polling, (directory, service) -> polling.register(directory), null);
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
