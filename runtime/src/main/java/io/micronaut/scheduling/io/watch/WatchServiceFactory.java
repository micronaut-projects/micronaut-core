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

package io.micronaut.scheduling.io.watch;

import com.sun.jna.Library;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.WatchService;

/**
 * A factory that creates the {@link WatchService}. For Mac OS X this class will try to instantiate
 * {@link MacOSXListeningWatchService} otherwise fall back to the default with a warning.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(property = FileWatchConfiguration.PATHS)
@Factory
public class WatchServiceFactory {
    private static final Logger LOG = LoggerFactory.getLogger(WatchServiceFactory.class);

    /**
     * The default {@link WatchService}.
     *
     * @return The watch service to use.
     * @throws IOException if an error occurs creating the watch service
     */
    @Bean(preDestroy = "close")
    @Singleton
    @Requires(missing = MacOSXListeningWatchService.class)
    @Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = FileWatchConfiguration.PATHS)
    @Primary
    WatchService watchService() throws IOException {
        String name = System.getProperty("os.name").toLowerCase();
        boolean isMacOS = "Mac OS X".equalsIgnoreCase(name) || "Darwin".equalsIgnoreCase(name);
        if (isMacOS) {
            LOG.warn("Using default File WatchService on OS X is slow. Consider adding 'io.methvin:directory-watcher' and 'net.java.dev.jna:jna' dependencies to use native file watch");
        }
        return FileSystems.getDefault().newWatchService();
    }

    /**
     * The default {@link WatchService}.
     *
     * @return The watch service to use.
     * @throws IOException if an error occurs creating the watch service
     */
    @Bean(preDestroy = "close")
    @Singleton
    @Requires(classes = {MacOSXListeningWatchService.class, Library.class})
    @Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = FileWatchConfiguration.PATHS)
    @Primary
    WatchService macWatchService() throws IOException {
        try {
            return new MacOSXListeningWatchService();
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to create Mac OS X specific watch service. Falling back to default polling strategy: " + e.getMessage(), e);
            }
            return watchService();
        }
    }
}
