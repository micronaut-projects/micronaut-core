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

import static io.micronaut.core.util.CollectionUtils.toStringArray;
import static io.micronaut.core.util.StringUtils.hasText;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.CollectionUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;

/**
 * Factory used to create {@link Flyway} beans with the configuration defined in {@link FlywayConfigurationProperties}.
 *
 * @author Iván López
 * @since 1.1
 */
@Factory
public class FlywayFactory {

    /**
     * Creates a {@link Flyway} bean per datasource if the configuration is correct.
     *
     * @param config The Flyway configuration
     * @return The Flyway bean configured
     */
    @Requires(condition = FlywayCondition.class)
    @EachBean(FlywayConfigurationProperties.class)
    public Flyway flyway(FlywayConfigurationProperties config) {
        FluentConfiguration fluentConfiguration = new FluentConfiguration();
        configureDatabase(fluentConfiguration, config);
        configureProperties(fluentConfiguration, config);

        return fluentConfiguration.load();
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
