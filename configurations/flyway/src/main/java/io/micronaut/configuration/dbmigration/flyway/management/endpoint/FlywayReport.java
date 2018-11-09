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

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.flywaydb.core.api.MigrationInfo;

import java.util.List;

/**
 * Flyway report for one datasource.
 *
 * @author Iván López
 * @since 1.1.0
 */
public class FlywayReport {

    private final String name;
    private final List<MigrationInfo> migrations;

    /**
     * @param name       The name of the data source
     * @param changeSets The list of changes
     */
    public FlywayReport(String name, List<MigrationInfo> changeSets) {
        this.name = name;
        this.migrations = changeSets;
    }

    /**
     * @return The name of the data source
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return The list of change migrations
     */
    @JsonSerialize(contentAs = MigrationInfo.class)
    public List<MigrationInfo> getMigrations() {
        return migrations;
    }
}
