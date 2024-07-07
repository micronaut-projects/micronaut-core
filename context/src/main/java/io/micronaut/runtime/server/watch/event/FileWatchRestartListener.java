/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.runtime.server.watch.event;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.core.util.StringUtils;
import io.micronaut.runtime.EmbeddedApplication;
import io.micronaut.scheduling.io.watch.FileWatchConfiguration;
import io.micronaut.scheduling.io.watch.event.FileChangedEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener that stops the server if a file changes. Relies on external service like {@code gradle run --continuous} or Kubernetes replication controller is required to restart the container.
 *
 * <p>The {@link FileWatchConfiguration#RESTART} property should be set to true to active.</p>
 *
 * @author graemerocher
 * @since 1.1.0
 */
@Singleton
@Requires(beans = EmbeddedApplication.class)
@Requires(property = FileWatchConfiguration.RESTART, value = StringUtils.TRUE, defaultValue = StringUtils.FALSE)
public class FileWatchRestartListener implements ApplicationEventListener<FileChangedEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(FileWatchRestartListener.class);

    private final EmbeddedApplication<?> embeddedApplication;

    /**
     * Default constructor.
     *
     * @param embeddedApplication The embedded application
     */
    public FileWatchRestartListener(EmbeddedApplication<?> embeddedApplication) {
        this.embeddedApplication = embeddedApplication;
    }

    @Override
    public void onApplicationEvent(FileChangedEvent event) {
        embeddedApplication.stop();
        if (LOG.isInfoEnabled()) {
            LOG.info("Shutting down server following file change.");
        }
        System.exit(0);
    }
}
