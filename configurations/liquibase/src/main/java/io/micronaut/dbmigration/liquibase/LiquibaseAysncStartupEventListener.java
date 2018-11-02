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

package io.micronaut.dbmigration.liquibase;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.StartupEvent;
import io.micronaut.scheduling.annotation.Async;
import liquibase.Liquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micronaut.runtime.event.annotation.EventListener;

import javax.inject.Singleton;
import java.util.Collection;

/**
 * Asynchronous listener for  {@link io.micronaut.context.event.StartupEvent} to run liquibase operations.
 *
 * @author Sergio del Amo
 * @since 1.1
 */
@Requires(classes = Liquibase.class)
@Singleton
class LiquibaseAysncStartupEventListener extends AbstractLiquibase {
    private static final Logger LOG = LoggerFactory.getLogger(LiquibaseAysncStartupEventListener.class);

    /**
     * @param resourceAccessor                 An implementation of {@link liquibase.resource.ResourceAccessor}.
     * @param liquibaseConfigurationProperties Collection of Liquibase Configurations
     */
    public LiquibaseAysncStartupEventListener(ResourceAccessor resourceAccessor, Collection<LiquibaseConfigurationProperties> liquibaseConfigurationProperties) {
        super(resourceAccessor, liquibaseConfigurationProperties);
    }

    /**
     * Runs Liquibase for the datasource where there is a liquibase configuration available.
     *
     * @param event Server startup event
     */
    @EventListener
    @Async
    public void onStartup(StartupEvent event) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("executing liquibase async event listener");
        }
        run(true);
    }
}
