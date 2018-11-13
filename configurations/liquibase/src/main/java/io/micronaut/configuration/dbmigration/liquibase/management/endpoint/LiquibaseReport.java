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

package io.micronaut.configuration.dbmigration.liquibase.management.endpoint;

import liquibase.changelog.RanChangeSet;

import java.util.List;

/**
 * Liquibase report for one datasource.
 *
 * @author Iván López
 * @author Sergio del Amo
 * @since 1.1
 */
public class LiquibaseReport {

    private String name;
    private List<RanChangeSet> changeSets;

    /**
     * @param name       The name of the data source
     * @param changeSets The list of changes
     */
    public LiquibaseReport(String name, List<RanChangeSet> changeSets) {
        this.name = name;
        this.changeSets = changeSets;
    }

    /**
     * @return The name of the data source
     */
    public String getName() {
        return this.name;
    }

    /**
     * @return The list of change sets
     */
    public List<RanChangeSet> getChangeSets() {
        return changeSets;
    }
}
