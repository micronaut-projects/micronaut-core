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
package io.micronaut.scheduling.io.watch.osx;

import com.sun.jna.Library;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.micronaut.context.annotation.*;
import io.micronaut.core.util.StringUtils;
import io.micronaut.scheduling.io.watch.FileWatchCondition;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import io.micronaut.scheduling.io.watch.WatchServiceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.WatchService;

/**
 * A factory that creates the {@link WatchService}. For Mac OS X this class will try to instantiate
 * {@link MacOSXListeningWatchService} otherwise fall back to the default with a warning.
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
@Requires(condition = FileWatchCondition.class)
@Requires(classes = {MacOSXListeningWatchService.class, Library.class})
@Factory
public class MacOsWatchServiceFactory {

    protected static final Logger LOG = LoggerFactory.getLogger(WatchServiceFactory.class);

    /**
     * The default {@link WatchService}.
     *
     * @return The watch service to use.
     * @throws IOException if an error occurs creating the watch service
     */
    @Bean(preDestroy = "close")
    @Prototype
    @Requires(classes = {MacOSXListeningWatchService.class, Library.class})
    @Requires(property = FileWatchConfiguration.ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = FileWatchConfiguration.PATHS)
    @Primary
    protected WatchService macWatchService() throws IOException {
        try {
            return new MacOSXListeningWatchService();
        } catch (Exception e) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("Unable to create Mac OS X specific watch service. Falling back to default polling strategy: " + e.getMessage(), e);
            }
            return new WatchServiceFactory().watchService();
        }
    }
}
