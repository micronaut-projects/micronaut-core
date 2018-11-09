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
        FluentConfiguration fluentConfiguration = config.fluentConfiguration;
        if (config.hasAlternativeDatabaseConfiguration()) {
            fluentConfiguration.dataSource(config.getUrl(), config.getUser(), config.getPassword());
        } else {
            fluentConfiguration.dataSource(config.getDataSource());
        }

        return fluentConfiguration.load();
    }

}
