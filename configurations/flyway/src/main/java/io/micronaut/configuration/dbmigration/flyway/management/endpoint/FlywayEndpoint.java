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

package io.micronaut.configuration.dbmigration.flyway.management.endpoint;

import io.micronaut.configuration.dbmigration.flyway.FlywayConfigurationProperties;
import io.micronaut.context.ApplicationContext;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import io.reactivex.Single;
import org.flywaydb.core.Flyway;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides a flyway endpoint to get all the migrations applied.
 *
 * @author Iván López
 * @since 1.1
 */
@Endpoint(id = FlywayEndpoint.NAME)
public class FlywayEndpoint {

    /**
     * Endpoint name.
     */
    public static final String NAME = "flyway";

    private final ApplicationContext applicationContext;
    private final Collection<FlywayConfigurationProperties> flywayConfigurationProperties;

    /**
     * Constructor.
     *
     * @param flywayConfigurationProperties Collection of Flyway Configurations
     */
    public FlywayEndpoint(ApplicationContext applicationContext,
                          Collection<FlywayConfigurationProperties> flywayConfigurationProperties) {
        this.applicationContext = applicationContext;
        this.flywayConfigurationProperties = flywayConfigurationProperties;
    }

    /**
     * @return A list of Flyway migrations per active configuration
     */
    @Read
    public Single<List<FlywayReport>> flywayMigrations() {
        List<FlywayReport> reports = new ArrayList<>();

        if (flywayConfigurationProperties != null) {
            for (FlywayConfigurationProperties config : flywayConfigurationProperties) {

                if (config.isEnabled()) {

                    Optional<Flyway> flywayBean = applicationContext
                            .findBean(Flyway.class, Qualifiers.byName(config.getNameQualifier()));

                    flywayBean.ifPresent(flyway -> reports.add(
                            new FlywayReport(
                                    config.getNameQualifier(),
                                    Stream.of(flyway.info().all())
                                            .map(FlywayMigration::new)
                                            .collect(Collectors.toList())
                            ))
                    );
                }
            }
        }

        return Single.just(reports);
    }
}
