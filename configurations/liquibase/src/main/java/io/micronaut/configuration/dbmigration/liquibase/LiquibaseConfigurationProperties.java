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

package io.micronaut.configuration.dbmigration.liquibase;

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.io.File;
import java.util.Map;

/**
 * Create a Liquibase Configuration for each sub-property of liquibase.*.
 *
 * @author Sergio del Amo
 * @since 1.1
 */
@EachProperty("liquibase")
public class LiquibaseConfigurationProperties implements Toggleable {

    /**
     * The default enable value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    /**
     * The default dropFirst value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_DROPFIRST = false;

    /**
     * The default testRollbackOnUpdate value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_TESTROLLBACKONUPDATE = false;

    /**
     * The default ignoreClasspathPrefix value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_IGNORECLASSPATHPREFIX = true;

    /**
     * The default async value.
     */
    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ASYNC = false;

    private boolean async = DEFAULT_ASYNC;

    private boolean enabled = DEFAULT_ENABLED;

    private String changeLog;

    private String defaultSchema;

    private String liquibaseSchema;

    private boolean dropFirst = DEFAULT_DROPFIRST;

    private String liquibaseTablespace;

    private String databaseChangeLogTable;

    private String databaseChangeLogLockTable;

    private String tag;

    private String contexts;

    private String labels;

    private boolean testRollbackOnUpdate = DEFAULT_TESTROLLBACKONUPDATE;

    /**
     * Ignores classpath prefix during changeset comparison.
     * This is particularly useful if Liquibase is run in different ways.
     *
     * For instance, if Maven plugin is used to run changesets, as in:
     * <code>
     *      &lt;configuration&gt;
     *          ...
     *          &lt;changeLogFile&gt;path/to/changelog&lt;/changeLogFile&gt;
     *      &lt;/configuration&gt;
     * </code>
     *
     * {@link liquibase.Liquibase.Liquibase#listUnrunChangeSets(liquibase.Liquibase.Contexts, )} will
     * always, by default, return changesets, regardless of their
     * execution by Maven.
     * Maven-executed changeset path name are not be prepended by
     * "classpath:" whereas the ones parsed via AbstractLiquibase are.
     *
     * To avoid this issue, just set ignoreClasspathPrefix to true.
     */
    private boolean ignoreClasspathPrefix = DEFAULT_IGNORECLASSPATHPREFIX;

    private String rollbackFilePath;

    private Map<String, String> parameters;

    private final DataSource dataSource;

    private final String nameQualifier;

    /**
     * @param dataSource DataSource with the same name qualifier.
     * @param name       name qualifier.
     */
    public LiquibaseConfigurationProperties(@Nullable @Parameter DataSource dataSource,
                                            @Parameter String name) {
        this.dataSource = dataSource;
        this.nameQualifier = name;
    }

    /**
     * Whether rollback should be tested before update is performed. Default value ({@value #DEFAULT_TESTROLLBACKONUPDATE}).
     *
     * @param testRollbackOnUpdate Whether rollback should be tested before update is performed.
     */
    public void setTestRollbackOnUpdate(boolean testRollbackOnUpdate) {
        this.testRollbackOnUpdate = testRollbackOnUpdate;
    }

    /**
     * Returns whether a rollback should be tested at update time or not.
     *
     * @return Whether a rollback should be tested at update time or not.
     */
    public boolean isTestRollbackOnUpdate() {
        return testRollbackOnUpdate;
    }

    /**
     * @return true if classpath prefix should be ignored during changeset comparison
     */
    public boolean isIgnoreClasspathPrefix() {
        return ignoreClasspathPrefix;
    }

    /**
     * Ignores classpath prefix during changeset comparison. Default value ({@value #DEFAULT_IGNORECLASSPATHPREFIX}).
     *
     * @param ignoreClasspathPrefix Sets whether to ignore the classpath prefix during changeset comparison.
     */
    public void setIgnoreClasspathPrefix(boolean ignoreClasspathPrefix) {
        this.ignoreClasspathPrefix = ignoreClasspathPrefix;
    }

    /**
     * Name of table to use for tracking concurrent Liquibase usage.
     *
     * @return the name of table to use for tracking concurrent Liquibase usage.
     */
    public String getDatabaseChangeLogLockTable() {
        return databaseChangeLogLockTable;
    }

    /**
     * Name of table to use for tracking concurrent Liquibase usage.
     *
     * @param databaseChangeLogLockTable the name of table to use for tracking concurrent Liquibase usage.
     */
    public void setDatabaseChangeLogLockTable(String databaseChangeLogLockTable) {
        this.databaseChangeLogLockTable = databaseChangeLogLockTable;
    }

    /**
     * Name of table to use for tracking change history.
     *
     * @return the name of table to use for tracking change history.
     */
    public String getDatabaseChangeLogTable() {
        return databaseChangeLogTable;
    }

    /**
     * Name of table to use for tracking change history.
     *
     * @param databaseChangeLogTable the name of table to use for tracking change history.
     */
    public void setDatabaseChangeLogTable(String databaseChangeLogTable) {
        this.databaseChangeLogTable = databaseChangeLogTable;
    }

    /**
     * Tablespace to use for Liquibase objects.
     *
     * @return the tablespace to use for Liquibase objects.
     */
    public String getLiquibaseTablespace() {
        return liquibaseTablespace;
    }

    /**
     * Tablespace to use for Liquibase objects.
     *
     * @param liquibaseTablespace the tablespace to use for Liquibase objects.
     */
    public void setLiquibaseTablespace(String liquibaseTablespace) {
        this.liquibaseTablespace = liquibaseTablespace;
    }

    /**
     * Schema to use for Liquibase objects.
     *
     * @return Schema to use for Liquibase objects.
     */
    public String getLiquibaseSchema() {
        return liquibaseSchema;
    }

    /**
     * Schema to use for Liquibase objects.
     *
     * @param liquibaseSchema Schema to use for Liquibase objects.
     */
    public void setLiquibaseSchema(String liquibaseSchema) {
        this.liquibaseSchema = liquibaseSchema;
    }

    /**
     * @return the liquibase changelog
     */
    public String getChangeLog() {
        return this.changeLog;
    }

    /**
     * Change log configuration path.
     *
     * @param changeLog sets the change log configuration path.
     */
    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    /**
     * Path to file to which rollback SQL is written when an update is performed.
     *
     * @param rollbackFilePath Path to file to which rollback SQL is written when an update is performed.
     */
    public void setRollbackFilePath(String rollbackFilePath) {
        this.rollbackFilePath = rollbackFilePath;
    }

    /**
     * @return the path to file to which rollback SQL is written when an update is performed.
     */
    public String getRollbackFilePath() {
        return this.rollbackFilePath;
    }

    /**
     * @return the file to which rollback SQL is written when an update is performed.
     */
    public File getRollbackFile() {
        if (this.rollbackFilePath == null) {
            return null;
        }
        return new File(this.rollbackFilePath);
    }

    /**
     * @return true if database schema should be drop before running liquibase operations.
     */
    public boolean isDropFirst() {
        return dropFirst;
    }

    /**
     * Whether to first drop the database schema. Default value ({@value #DEFAULT_DROPFIRST}).
     *
     * @param dropFirst True to drop the schema.
     */
    public void setDropFirst(boolean dropFirst) {
        this.dropFirst = dropFirst;
    }

    /**
     * @return the default database schema.
     */
    public String getDefaultSchema() {
        return defaultSchema;
    }

    /**
     * Default database schema.
     *
     * @param defaultSchema Sets the default database schema.
     */
    public void setDefaultSchema(String defaultSchema) {
        this.defaultSchema = defaultSchema;
    }

    /**
     * @return the change log parameters.
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * Change log parameters.
     *
     * @param parameters Change log parameters
     */
    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    /**
     * @return the liquibase tag.
     */
    public String getTag() {
        return tag;
    }

    /**
     * @param tag a tag.
     */
    public void setTag(String tag) {
        this.tag = tag;
    }

    /**
     * Comma-separated list of runtime contexts to use.
     *
     * @return a comma-separated list of runtime contexts to use.
     */
    public String getContexts() {
        return contexts;
    }

    /**
     * Comma-separated list of runtime contexts to use.
     *
     * @param contexts a comma-separated list of runtime contexts to use.
     */
    public void setContexts(String contexts) {
        this.contexts = contexts;
    }

    /**
     * Comma-separated list of runtime labels to use.
     *
     * @return A Comma-separated list of runtime labels to use
     */
    public String getLabels() {
        return labels;
    }

    /**
     * Comma-separated list of runtime labels to use.
     *
     * @param labels A Comma-separated list of runtime labels to use
     */
    public void setLabels(String labels) {
        this.labels = labels;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether this liquibase configuration is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled True if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return name qualifier associated with this liquibase configuration.
     */
    public String getNameQualifier() {
        return nameQualifier;
    }

    /**
     * @return The DataSource qualified with the same name.
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * Whether liquibase operations should be run asynchronously.
     *
     * @param async true to run liquibase operations asynchronously
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    /**
     * @return true if liquibase operations should be run asynchronously.
     */
    public boolean isAsync() {
        return async;
    }
}
