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

package io.micronaut.dbmigration.flyway.management.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationType;

import java.time.Instant;
import java.util.Date;

/**
 * A Flyway migration.
 *
 * @author Iván López
 * @see MigrationInfo
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FlywayMigration {

    private MigrationType type;

    private Integer checksum;

    private String version;

    private String description;

    private String script;

    private MigrationState state;

    private String installedBy;

    private Instant installedOn;

    private Integer installedRank;

    private Integer executionTime;

    /**
     * Default constructor.
     */
    public FlywayMigration() {
    }

    public FlywayMigration(MigrationInfo migrationInfo) {
        this.type = migrationInfo.getType();
        this.checksum = migrationInfo.getChecksum();
        this.version = nullSafeToString(migrationInfo.getVersion());
        this.description = migrationInfo.getDescription();
        this.script = migrationInfo.getScript();
        this.state = migrationInfo.getState();
        this.installedBy = migrationInfo.getInstalledBy();
        this.installedOn = nullSafeToInstant(migrationInfo.getInstalledOn());
        this.installedRank = migrationInfo.getInstalledRank();
        this.executionTime = migrationInfo.getExecutionTime();
    }

    /**
     * @return The {@link MigrationType} of the migration
     */
    public MigrationType getType() {
        return type;
    }

    /**
     * @return The migration checksum
     */
    public Integer getChecksum() {
        return checksum;
    }

    /**
     * @return The schema version after the migration is complete
     */
    public String getVersion() {
        return version;
    }

    /**
     * @return The description of the migration
     */
    public String getDescription() {
        return description;
    }

    /**
     * @return The name of the script to execute for this migration, relative to its classpath or filesystem location.
     */
    public String getScript() {
        return script;
    }

    /**
     * @return The {@link MigrationState} of the migration
     */
    public MigrationState getState() {
        return state;
    }

    /**
     * @return The user that installed this migration
     */
    public String getInstalledBy() {
        return installedBy;
    }

    /**
     * @return The timestamp when this migration was installed
     */
    public Instant getInstalledOn() {
        return installedOn;
    }

    /**
     * @return The rank of this installed migration
     */
    public Integer getInstalledRank() {
        return installedRank;
    }

    /**
     * @return The execution time (in millis) of this migration
     */
    public Integer getExecutionTime() {
        return executionTime;
    }

    private String nullSafeToString(Object obj) {
        return (obj != null) ? obj.toString() : null;
    }

    private Instant nullSafeToInstant(Date date) {
        return (date != null) ? Instant.ofEpochMilli(date.getTime()) : null;
    }
}
