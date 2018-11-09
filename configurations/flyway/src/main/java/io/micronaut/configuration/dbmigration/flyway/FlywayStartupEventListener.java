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

package io.micronaut.configuration.dbmigration.flyway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.runtime.event.annotation.EventListener;
import io.micronaut.scheduling.annotation.Async;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.util.Collection;
import java.util.Optional;

/**
 * Listener for {@link StartupEvent}s to run flyway operations.
 *
 * @author Iván López
 * @since 1.1.0
 */
@Requires(beans = Flyway.class)
@Singleton
class FlywayStartupEventListener {

    private static final Logger LOG = LoggerFactory.getLogger(FlywayStartupEventListener.class);

    private final ApplicationContext applicationContext;
    private final Collection<FlywayConfigurationProperties> flywayConfigurationProperties;

    /**
     * @param applicationContext            The application context
     * @param flywayConfigurationProperties Collection of Flyway configuration properties
     */
    public FlywayStartupEventListener(ApplicationContext applicationContext,
                          Collection<FlywayConfigurationProperties> flywayConfigurationProperties) {
        this.applicationContext = applicationContext;
        this.flywayConfigurationProperties = flywayConfigurationProperties;
    }

    /**
     * Runs Flyway migrations synchronously.
     *
     * @param event Server startup event
     */
    @EventListener
    public void onStartup(StartupEvent event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Executing synchronous flyway migrations");
        }
        run(false);
    }

    /**
     * Runs Flyway migrations asynchronously.
     *
     * @param event Server startup event
     */
    @Async
    @EventListener
    public void onStartupAsync(StartupEvent event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("Executing asynchronous flyway migrations");
        }
        run(true);
    }

    /**
     * Runs Flyway migrations for all the created {@link Flyway} beans.
     *
     * @param async if true only flyway configurations set to async are run.
     */
    public void run(boolean async) {
        flywayConfigurationProperties.stream()
                .filter(c -> c.isAsync() == async)
                .map(c -> applicationContext.findBean(Flyway.class, Qualifiers.byName(c.getNameQualifier())))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .forEach(Flyway::migrate);
    }
}
