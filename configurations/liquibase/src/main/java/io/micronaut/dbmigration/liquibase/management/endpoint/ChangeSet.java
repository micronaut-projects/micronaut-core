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

package io.micronaut.dbmigration.liquibase.management.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import liquibase.changelog.RanChangeSet;

import java.time.Instant;
import java.util.Set;

/**
 * A Liquibase change set.
 *
 * @author Iván López
 * @author Sergio del Amo
 * @since 1.1
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ChangeSet {

    private String author;

    private String changeLog;

    private String comments;

    private Set<String> contexts;

    private Instant dateExecuted;

    private String deploymentId;

    private String description;

    private liquibase.changelog.ChangeSet.ExecType execType;

    private String id;

    private Set<String> labels;

    private String checksum;

    private Integer orderExecuted;

    private String tag;

    public ChangeSet() {

    }

    /**
     * @param ranChangeSet The liquibase changeset
     */
    public ChangeSet(RanChangeSet ranChangeSet) {
        this.author = ranChangeSet.getAuthor();
        this.changeLog = ranChangeSet.getChangeLog();
        this.comments = ranChangeSet.getComments();
        this.contexts = ranChangeSet.getContextExpression().getContexts();
        this.dateExecuted = Instant.ofEpochMilli(ranChangeSet.getDateExecuted().getTime());
        this.deploymentId = ranChangeSet.getDeploymentId();
        this.description = ranChangeSet.getDescription();
        this.execType = ranChangeSet.getExecType();
        this.id = ranChangeSet.getId();
        this.labels = ranChangeSet.getLabels().getLabels();
        this.checksum = ((ranChangeSet.getLastCheckSum() != null) ? ranChangeSet.getLastCheckSum().toString() : null);
        this.orderExecuted = ranChangeSet.getOrderExecuted();
        this.tag = ranChangeSet.getTag();
    }

    /**
     * @return The author of the change
     */
    public String getAuthor() {
        return this.author;
    }

    /**
     * @return The changelog
     */
    public String getChangeLog() {
        return this.changeLog;
    }

    /**
     * @return The comments
     */
    public String getComments() {
        return this.comments;
    }

    /**
     * @return The contexts
     */
    public Set<String> getContexts() {
        return this.contexts;
    }

    /**
     * @return The execution date for the changeset
     */
    public Instant getDateExecuted() {
        return this.dateExecuted;
    }

    /**
     * @return The deployment id
     */
    public String getDeploymentId() {
        return this.deploymentId;
    }

    /**
     * @return The descriptions
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * @return The {@link liquibase.changelog.ChangeSet.ExecType}
     */
    public liquibase.changelog.ChangeSet.ExecType getExecType() {
        return this.execType;
    }

    /**
     * @return The changeset id
     */
    public String getId() {
        return this.id;
    }

    /**
     * @return The labels
     */
    public Set<String> getLabels() {
        return this.labels;
    }

    /**
     * @return The checksum
     */
    public String getChecksum() {
        return this.checksum;
    }

    /**
     * @return The order in which the migration has been executed
     */
    public Integer getOrderExecuted() {
        return this.orderExecuted;
    }

    /**
     * @return The tag
     */
    public String getTag() {
        return this.tag;
    }
}
