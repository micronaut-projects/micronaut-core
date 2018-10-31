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

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.Collection;

/**
 * For each {@link DataSource}, it looks for a flyway configuration ({@link FlywayConfigurationProperties}) with the
 * same name qualifier and runs it.
 *
 * @author Iván López
 * @since 1.1
 */
public abstract class AbstractFlyway {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractFlyway.class);

    private final Collection<FlywayConfigurationProperties> properties;

    /**
     * @param properties Collection of Flyway Configurations
     */
    public AbstractFlyway(Collection<FlywayConfigurationProperties> properties) {
        this.properties = properties;
    }

    /**
     * Runs Flyway for the datasource where there is a flyway configuration available.
     *
     * @param async if true only flyway configurations set to async are run.
     */
    public void run(boolean async) {
        if (properties != null) {
            for (FlywayConfigurationProperties config : properties) {
                DataSource dataSource = config.getDataSource();
                if (dataSource == null) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Flyway not run for identifier \"{}\" because no data source found", config.getNameQualifier());
                    }
                    continue;
                }
                if (!config.isEnabled()) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Flyway not run for identifier \"{}\" because flyway configuration is disabled", config.getNameQualifier());
                    }
                    continue;
                }
                if (config.isAsync() != async) {
                    continue;
                }
                if (LOG.isInfoEnabled()) {
                    LOG.info("Executing Flyway operations for identifier \"{}\" {}", config.getNameQualifier(), async ? "asynchronously" : "synchronously");
                }
                runFlywayForDataSourceWithConfig(dataSource, config);
            }
        }
    }

    /**
     * Runs Flyway migrations.
     *
     * @param dataSource The DataSource
     * @param config     Flyway configuration
     */
    protected void runFlywayForDataSourceWithConfig(DataSource dataSource, FlywayConfigurationProperties config) {
        try {
            FluentConfiguration configuration = new FluentConfiguration();
            configuration.dataSource(dataSource);

            Flyway flyway = configuration.load();
            flyway.migrate();
        } catch (FlywayException e) {
            LOG.error("FlywayException: ", e);
        }
    }
}
