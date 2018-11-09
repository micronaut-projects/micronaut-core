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

import io.micronaut.context.annotation.ConfigurationBuilder;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.Toggleable;
import org.flywaydb.core.api.configuration.FluentConfiguration;

import javax.annotation.Nullable;
import javax.sql.DataSource;

/**
 * Create a Flyway Configuration for each sub-property of flyway.*.
 *
 * @author Iván López
 * @see org.flywaydb.core.api.configuration.FluentConfiguration
 * @since 1.1.0
 */
@EachProperty("flyway")
public class FlywayConfigurationProperties implements Toggleable {

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ENABLED = true;

    @SuppressWarnings("WeakerAccess")
    public static final boolean DEFAULT_ASYNC = false;

    @ConfigurationBuilder(prefixes = "")
    FluentConfiguration fluentConfiguration = new FluentConfiguration();

    private final DataSource dataSource;
    private final String nameQualifier;
    private boolean enabled = DEFAULT_ENABLED;
    private boolean async = DEFAULT_ASYNC;
    private String url;
    private String user;
    private String password;

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
     * @return The flyway configuration builder
     */
    public FluentConfiguration getFluentConfiguration() {
        return fluentConfiguration;
    }

}
