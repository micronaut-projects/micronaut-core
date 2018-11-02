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

import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Create a Flyway Configuration for each sub-property of flyway.*.
 *
 * @author Iván López
 * @see <a href="https://flywaydb.org/documentation/configfiles">Flyway Config</a>
 * @since 1.1
 */
@EachProperty("flyway")
public class FlywayConfigurationProperties implements Toggleable {

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ASYNC = false;

    private final DataSource dataSource;

    private final String nameQualifier;

    private boolean enabled = DEFAULT_ENABLED;

    private boolean async = DEFAULT_ASYNC;

    private String url;

    private String user;

    private String password;

    private int connectRetries = 0;

    private List<String> initSqls = new ArrayList<>();

    private List<String> schemas = new ArrayList<>();

    private String table = "flyway_schema_history";

    private List<String> locations = new ArrayList<>(Collections.singletonList("classpath:db/migration"));

    private boolean skipDefaultResolvers;

    private String sqlMigrationPrefix = "V";

    private String repeatableSqlMigrationPrefix = "R";

    private String sqlMigrationSeparator = "__";

    private List<String> sqlMigrationSuffixes = new ArrayList<>(Collections.singleton(".sql"));

    private Charset encoding = StandardCharsets.UTF_8;

    private boolean placeholderReplacement = true;

    private Map<String, String> placeholders = new HashMap<>();

    private String placeholderPrefix = "${";

    private String placeholderSuffix = "}";

    private String target;

    private boolean validateOnMigrate = true;

    private boolean cleanOnValidationError;

    private boolean cleanDisabled;

    private String baselineVersion = "1";

    private String baselineDescription = "<< Flyway Baseline >>";

    private boolean baselineOnMigrate;

    private boolean outOfOrder;

    private boolean ignoreMissingMigrations;

    private boolean ignoreIgnoredMigrations;

    private boolean ignorePendingMigrations;

    private boolean ignoreFutureMigrations = true;

    private boolean mixed;

    private boolean group;

    private String installedBy;

    /**
     * @param dataSource DataSource with the same name qualifier.
     * @param name       The name qualifier.
     */
    public FlywayConfigurationProperties(@Nullable @Parameter DataSource dataSource,
                                         @Parameter String name) {
        this.dataSource = dataSource;
        this.nameQualifier = name;
    }

    /**
     * @return The {@link DataSource}
     */
    public DataSource getDataSource() {
        return dataSource;
    }

    /**
     * @return The qualifier associated with this flyway configuration
     */
    public String getNameQualifier() {
        return nameQualifier;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Set whether this flyway configuration is enabled. Default value ({@value #DEFAULT_ENABLED}).
     *
     * @param enabled true if it is enabled
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * @return Whether the flyway migrations should run asynchronously
     */
    public boolean isAsync() {
        return async;
    }

    /**
     * Whether flyway migrations should run asynchronously.
     *
     * @param async true to run flyway migrations asynchronously
     */
    public void setAsync(boolean async) {
        this.async = async;
    }

    /**
     * @return JDBC url of the database to migrate
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param url The JDBC url of the database to migrate
     */
    public void setUrl(String url) {
        this.url = url;
    }

    /**
     * @return The user of the database to migrate
     */
    public String getUser() {
        return user;
    }

    /**
     * @param user The user of the database to migrate
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * @return The password of the database to migrate
     */
    public String getPassword() {
        return password;
    }

    /**
     * @param password The password of the database to migrate
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Whether there is an alternative database configuration for the migration. By default Micronaut will use the
     * {@link DataSource} defined for the application but if both {@code url} and {@code user} are defined, then those
     * will be use for Liquibase.
     *
     * @return true if there is an alternative database configuration
     */
    public boolean hasAlternativeDatabaseConfiguration() {
        return StringUtils.hasText(this.getUrl()) && StringUtils.hasText(this.getUser());
    }

    /**
     * @return The maximum number of retries when attempting to connect to the database
     */
    public int getConnectRetries() {
        return connectRetries;
    }

    /**
     * @param connectRetries The maximum number of retries when attempting to connect to the database
     */
    public void setConnectRetries(int connectRetries) {
        this.connectRetries = connectRetries;
    }

    /**
     * @return The SQL statements to run to initialize a new database connection immediately after opening it
     */
    public List<String> getInitSqls() {
        return initSqls;
    }

    /**
     * @param initSqls The SQL statements to run to initialize a new database connection immediately after opening it
     */
    public void setInitSqls(List<String> initSqls) {
        this.initSqls = initSqls;
    }

    /**
     * @return The schemas managed by Flyway. These schema names are case-sensitive
     */
    public List<String> getSchemas() {
        return schemas;
    }

    /**
     * @param schemas The schemas managed by Flyway
     */
    public void setSchemas(List<String> schemas) {
        this.schemas = schemas;
    }

    /**
     * @return The name of Flyway's schema history table
     */
    public String getTable() {
        return table;
    }

    /**
     * @param table The name of Flyway's schema history table
     */
    public void setTable(String table) {
        this.table = table;
    }

    /**
     * @return The locations to scan recursively for migrations
     */
    public List<String> getLocations() {
        return locations;
    }

    /**
     * @param locations The locations to scan recursively for migrations
     */
    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    /**
     * @return Whether the skip the default built-in resolvers
     */
    public boolean isSkipDefaultResolvers() {
        return skipDefaultResolvers;
    }

    /**
     * @param skipDefaultResolvers Tru to skip the default built-in resolvers
     */
    public void setSkipDefaultResolvers(boolean skipDefaultResolvers) {
        this.skipDefaultResolvers = skipDefaultResolvers;
    }

    /**
     * @return The file name prefix for versioned SQL migrations
     */
    public String getSqlMigrationPrefix() {
        return sqlMigrationPrefix;
    }

    /**
     * @param sqlMigrationPrefix The file name prefix for versioned SQL migrations
     */
    public void setSqlMigrationPrefix(String sqlMigrationPrefix) {
        this.sqlMigrationPrefix = sqlMigrationPrefix;
    }

    /**
     * @return The file name prefix for repeatable SQL migrations
     */
    public String getRepeatableSqlMigrationPrefix() {
        return repeatableSqlMigrationPrefix;
    }

    /**
     * @param repeatableSqlMigrationPrefix The file name prefix for repeatable SQL migrations
     */
    public void setRepeatableSqlMigrationPrefix(String repeatableSqlMigrationPrefix) {
        this.repeatableSqlMigrationPrefix = repeatableSqlMigrationPrefix;
    }

    /**
     * @return The file name separator for Sql migrations
     */
    public String getSqlMigrationSeparator() {
        return sqlMigrationSeparator;
    }

    /**
     * @param sqlMigrationSeparator The file name separator for Sql migrations
     */
    public void setSqlMigrationSeparator(String sqlMigrationSeparator) {
        this.sqlMigrationSeparator = sqlMigrationSeparator;
    }

    /**
     * @return The file name suffixes for SQL migrations
     */
    public List<String> getSqlMigrationSuffixes() {
        return sqlMigrationSuffixes;
    }

    /**
     * @param sqlMigrationSuffixes The file name suffixes for SQL migrations
     */
    public void setSqlMigrationSuffixes(List<String> sqlMigrationSuffixes) {
        this.sqlMigrationSuffixes = sqlMigrationSuffixes;
    }

    /**
     * @return The encoding of SQL migrations
     */
    public Charset getEncoding() {
        return encoding;
    }

    /**
     * @param encoding The encoding of SQL migrations
     */
    public void setEncoding(Charset encoding) {
        this.encoding = encoding;
    }

    /**
     * @return Whether placeholders should be replaced
     */
    public boolean isPlaceholderReplacement() {
        return placeholderReplacement;
    }

    /**
     * @param placeholderReplacement True to replace placeholders
     */
    public void setPlaceholderReplacement(boolean placeholderReplacement) {
        this.placeholderReplacement = placeholderReplacement;
    }

    /**
     * @return Placeholders to replace in Sql migrations
     */
    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    /**
     * @param placeholders Placeholders to replace in Sql migrations
     */
    public void setPlaceholders(Map<String, String> placeholders) {
        this.placeholders = placeholders;
    }

    /**
     * @return The prefix of every placeholder
     */
    public String getPlaceholderPrefix() {
        return placeholderPrefix;
    }

    /**
     * @param placeholderPrefix The prefix of every placeholder
     */
    public void setPlaceholderPrefix(String placeholderPrefix) {
        this.placeholderPrefix = placeholderPrefix;
    }

    /**
     * @return The suffix of every placeholder
     */
    public String getPlaceholderSuffix() {
        return placeholderSuffix;
    }

    /**
     * @param placeholderSuffix The suffix of every placeholder
     */
    public void setPlaceholderSuffix(String placeholderSuffix) {
        this.placeholderSuffix = placeholderSuffix;
    }

    /**
     * @return The target version up to which Flyway should consider migrations
     */
    public String getTarget() {
        return target;
    }

    /**
     * @param target The target version up to which Flyway should consider migrations
     */
    public void setTarget(String target) {
        this.target = target;
    }

    /**
     * @return Whether to automatically call validate or not when running migrate
     */
    public boolean isValidateOnMigrate() {
        return validateOnMigrate;
    }

    /**
     * @param validateOnMigrate True to automatically call validate when running migrate
     */
    public void setValidateOnMigrate(boolean validateOnMigrate) {
        this.validateOnMigrate = validateOnMigrate;
    }

    /**
     * @return Whether to automatically call clean or not when a validation error occurs
     */
    public boolean isCleanOnValidationError() {
        return cleanOnValidationError;
    }

    /**
     * @param cleanOnValidationError True to automatically call clean when a validation error occurs
     */
    public void setCleanOnValidationError(boolean cleanOnValidationError) {
        this.cleanOnValidationError = cleanOnValidationError;
    }

    /**
     * @return Whether to disabled clean
     */
    public boolean isCleanDisabled() {
        return cleanDisabled;
    }

    /**
     * @param cleanDisabled True to disabled clean
     */
    public void setCleanDisabled(boolean cleanDisabled) {
        this.cleanDisabled = cleanDisabled;
    }

    /**
     * @return The version to tag an existing schema with when executing baseline
     */
    public String getBaselineVersion() {
        return baselineVersion;
    }

    /**
     * @param baselineVersion The version to tag an existing schema with when executing baseline
     */
    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    /**
     * @return The description to tag an existing schema with when executing baseline
     */
    public String getBaselineDescription() {
        return baselineDescription;
    }

    /**
     * @param baselineDescription The description to tag an existing schema with when executing baseline
     */
    public void setBaselineDescription(String baselineDescription) {
        this.baselineDescription = baselineDescription;
    }

    /**
     * @return Whether to automatically call baseline when migrate is executed against a non-empty schema with no
     * schema history table
     */
    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    /**
     * @param baselineOnMigrate true to automatically call baseline when migrate is executed against a non-empty schema
     *                          with no schema history table
     */
    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }

    /**
     * @return Allows migrations to be run "out of order"
     */
    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    /**
     * @param outOfOrder True to allow migrations to be run "out of order"
     */
    public void setOutOfOrder(boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
    }

    /**
     * @return Whether to ignore missing migrations when reading the schema history table.
     */
    public boolean isIgnoreMissingMigrations() {
        return ignoreMissingMigrations;
    }

    /**
     * @param ignoreMissingMigrations True to ignore missing migrations when reading the schema history table.
     */
    public void setIgnoreMissingMigrations(boolean ignoreMissingMigrations) {
        this.ignoreMissingMigrations = ignoreMissingMigrations;
    }

    /**
     * @return Whether to ignore ignored migrations when reading the schema history table
     */
    public boolean isIgnoreIgnoredMigrations() {
        return ignoreIgnoredMigrations;
    }

    /**
     * @param ignoreIgnoredMigrations True to ignore ignored migrations when reading the schema history table
     */
    public void setIgnoreIgnoredMigrations(boolean ignoreIgnoredMigrations) {
        this.ignoreIgnoredMigrations = ignoreIgnoredMigrations;
    }

    /**
     * @return Whether to ignore pending migrations when reading the schema history table
     */
    public boolean isIgnorePendingMigrations() {
        return ignorePendingMigrations;
    }

    /**
     * @param ignorePendingMigrations True to ignore pending migrations when reading the schema history table
     */
    public void setIgnorePendingMigrations(boolean ignorePendingMigrations) {
        this.ignorePendingMigrations = ignorePendingMigrations;
    }

    /**
     * @return Whether to ignore future migrations when reading the schema history table
     */
    public boolean isIgnoreFutureMigrations() {
        return ignoreFutureMigrations;
    }

    /**
     * @param ignoreFutureMigrations True to ignore future migrations when reading the schema history table
     */
    public void setIgnoreFutureMigrations(boolean ignoreFutureMigrations) {
        this.ignoreFutureMigrations = ignoreFutureMigrations;
    }

    /**
     * @return Whether to allow mixing transactional and non-transactional statements within the same migration
     */
    public boolean isMixed() {
        return mixed;
    }

    /**
     * @param mixed True to allow mixing transactional and non-transactional statements within the same migration
     */
    public void setMixed(boolean mixed) {
        this.mixed = mixed;
    }

    /**
     * @return Whether to group all pending migrations together in the same transaction when applying them
     */
    public boolean isGroup() {
        return group;
    }

    /**
     * @param group True to group all pending migrations together in the same transaction when applying them
     */
    public void setGroup(boolean group) {
        this.group = group;
    }

    /**
     * @return The username that will be recorded in the schema history table as having applied the migration
     */
    public String getInstalledBy() {
        return installedBy;
    }

    /**
     * @param installedBy The username that will be recorded in the schema history table as having applied the migration
     */
    public void setInstalledBy(String installedBy) {
        this.installedBy = installedBy;
    }
}
