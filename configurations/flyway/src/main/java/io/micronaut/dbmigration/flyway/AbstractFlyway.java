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

package io.micronaut.dbmigration.flyway;

import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Optional;

/**
 * For each {@link DataSource}, it looks for a flyway configuration ({@link FlywayConfigurationProperties}) with the
 * same name qualifier and runs it.
 *
 * @author Iván López
 * @since 1.1
 */
public abstract class AbstractFlyway {

    private final ApplicationContext applicationContext;
    private final Collection<FlywayConfigurationProperties> flywayConfigurationProperties;

    /**
     * @param applicationContext            The application context
     * @param flywayConfigurationProperties Collection of Flyway configuration properties
     */
    public AbstractFlyway(ApplicationContext applicationContext,
                          Collection<FlywayConfigurationProperties> flywayConfigurationProperties) {
        this.applicationContext = applicationContext;
        this.flywayConfigurationProperties = flywayConfigurationProperties;
    }

    /**
     * Runs Flyway migrations for all the created {@link Flyway} beans
     *
     * @param async if true only flyway configurations set to async are run.
     */
    public void run(boolean async) {

        for (FlywayConfigurationProperties config : flywayConfigurationProperties) {
            if (config.isAsync() != async) {
                continue;
            }

            Optional<Flyway> flyway = applicationContext
                    .findBean(Flyway.class, Qualifiers.byName(config.getNameQualifier()));

            flyway.ifPresent(Flyway::migrate);
        }
    }
}
