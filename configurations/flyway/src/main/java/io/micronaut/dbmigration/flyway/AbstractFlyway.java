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

import static io.micronaut.core.util.CollectionUtils.toStringArray;
import static io.micronaut.core.util.StringUtils.hasText;

import io.micronaut.core.util.CollectionUtils;
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
                if (config.getDataSource() == null && !config.hasAlternativeDatabaseConfiguration()) {
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
                runFlywayWithConfig(config);
            }
        }
    }

    /**
     * Runs Flyway migrations.
     *
     * @param flywayConfig Flyway configuration
     */
    protected void runFlywayWithConfig(FlywayConfigurationProperties flywayConfig) {
        try {
            FluentConfiguration fluentConfiguration = new FluentConfiguration();
            configureDatabase(fluentConfiguration, flywayConfig);
            configureProperties(fluentConfiguration, flywayConfig);

            Flyway flyway = fluentConfiguration.load();
            flyway.migrate();
        } catch (FlywayException e) {
            LOG.error("FlywayException: ", e);
        }
    }

    private void configureDatabase(FluentConfiguration fluentConfiguration, FlywayConfigurationProperties flywayConfig) {
        if (flywayConfig.hasAlternativeDatabaseConfiguration()) {
            fluentConfiguration.dataSource(flywayConfig.getUrl(), flywayConfig.getUser(), flywayConfig.getPassword());
        } else {
            fluentConfiguration.dataSource(flywayConfig.getDataSource());
        }

        if (!CollectionUtils.isEmpty(flywayConfig.getInitSqls())) {
            String initSql = CollectionUtils.toString("\n", flywayConfig.getInitSqls());
            fluentConfiguration.initSql(initSql);
        }
    }

    private void configureProperties(FluentConfiguration fluentConfiguration, FlywayConfigurationProperties flywayConfig) {
        fluentConfiguration.connectRetries(flywayConfig.getConnectRetries());
        fluentConfiguration.schemas(toStringArray(flywayConfig.getSchemas()));
        fluentConfiguration.table(flywayConfig.getTable());
        fluentConfiguration.locations(toStringArray(flywayConfig.getLocations()));
        fluentConfiguration.skipDefaultResolvers(flywayConfig.isSkipDefaultResolvers());
        fluentConfiguration.sqlMigrationPrefix(flywayConfig.getSqlMigrationPrefix());
        fluentConfiguration.repeatableSqlMigrationPrefix(flywayConfig.getRepeatableSqlMigrationPrefix());
        fluentConfiguration.sqlMigrationSeparator(flywayConfig.getSqlMigrationSeparator());
        fluentConfiguration.sqlMigrationSuffixes(toStringArray(flywayConfig.getSqlMigrationSuffixes()));
        fluentConfiguration.encoding(flywayConfig.getEncoding());
        fluentConfiguration.placeholderReplacement(flywayConfig.isPlaceholderReplacement());
        fluentConfiguration.placeholders(flywayConfig.getPlaceholders());
        fluentConfiguration.placeholderPrefix(flywayConfig.getPlaceholderPrefix());
        fluentConfiguration.placeholderSuffix(flywayConfig.getPlaceholderSuffix());
        if (hasText(flywayConfig.getTarget())) {
            fluentConfiguration.target(flywayConfig.getTarget());
        }
        fluentConfiguration.validateOnMigrate(flywayConfig.isValidateOnMigrate());
        fluentConfiguration.cleanOnValidationError(flywayConfig.isCleanOnValidationError());
        fluentConfiguration.cleanDisabled(flywayConfig.isCleanDisabled());
        fluentConfiguration.baselineVersion(flywayConfig.getBaselineVersion());
        fluentConfiguration.baselineDescription(flywayConfig.getBaselineDescription());
        fluentConfiguration.baselineOnMigrate(flywayConfig.isBaselineOnMigrate());
        fluentConfiguration.outOfOrder(flywayConfig.isOutOfOrder());
        fluentConfiguration.ignoreMissingMigrations(flywayConfig.isIgnoreMissingMigrations());
        fluentConfiguration.ignoreIgnoredMigrations(flywayConfig.isIgnoreIgnoredMigrations());
        fluentConfiguration.ignorePendingMigrations(flywayConfig.isIgnorePendingMigrations());
        fluentConfiguration.ignoreFutureMigrations(flywayConfig.isIgnoreFutureMigrations());
        fluentConfiguration.mixed(flywayConfig.isMixed());
        fluentConfiguration.group(flywayConfig.isGroup());
        fluentConfiguration.installedBy(flywayConfig.getInstalledBy());
    }
}
