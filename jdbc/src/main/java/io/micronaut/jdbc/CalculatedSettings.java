/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jdbc;

import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.StringUtils;

import java.util.Optional;

/**
 * A class used to fill in the missing gaps of information needed
 * to successfully configure a data source.
 *
 * @author James Kleeh
 * @since 1.0
 */
public class CalculatedSettings {

    private String calculatedDriverClassName;
    private String calculatedUrl;
    private String calculatedUsername;
    private String calculatedPassword;
    private String calculatedValidationQuery;
    private Optional<JdbcDatabaseManager.EmbeddedJdbcDatabase> embeddedDatabaseConnection;
    private BasicJdbcConfiguration basicJdbcConfiguration;

    /**
     * @param basicJdbcConfiguration The basic jdbc configuration
     */
    public CalculatedSettings(BasicJdbcConfiguration basicJdbcConfiguration) {
        this.basicJdbcConfiguration = basicJdbcConfiguration;
        embeddedDatabaseConnection = JdbcDatabaseManager.get(this.getClass().getClassLoader());
    }

    /**
     * @param basicJdbcConfiguration The basic jdbc configuration
     * @param classLoader            The classloader to get the embedded database connection from
     */
    public CalculatedSettings(BasicJdbcConfiguration basicJdbcConfiguration, ClassLoader classLoader) {
        this.basicJdbcConfiguration = basicJdbcConfiguration;
        embeddedDatabaseConnection = JdbcDatabaseManager.get(classLoader);
    }

    /**
     * Determines the driver class name based on the configured value. If not
     * configured, determine the driver class name based on the URL. If the
     * URL is not configured, look for an embedded database driver on the
     * classpath.
     *
     * @return The calculated driver class name
     */
    public String getDriverClassName() {
        final String driverClassName = basicJdbcConfiguration.getConfiguredDriverClassName();
        if (calculatedDriverClassName == null || StringUtils.hasText(driverClassName)) {
            if (StringUtils.hasText(driverClassName)) {
                if (!driverClassIsPresent(driverClassName)) {
                    throw new ConfigurationException(String.format("Error configuring data source '%s'. The driver class '%s' was not found on the classpath", basicJdbcConfiguration.getName(), driverClassName));
                }
                calculatedDriverClassName = driverClassName;
            } else {
                final String url = basicJdbcConfiguration.getUrl();
                if (StringUtils.hasText(url)) {
                    JdbcDatabaseManager.findDatabase(url).ifPresent(db ->
                        calculatedDriverClassName = db.getDriverClassName());
                }

                if (!StringUtils.hasText(calculatedDriverClassName) && embeddedDatabaseConnection.isPresent()) {
                    calculatedDriverClassName = this.embeddedDatabaseConnection.get().getDriverClassName();
                }

                if (!StringUtils.hasText(calculatedDriverClassName)) {
                    throw new ConfigurationException(String.format("Error configuring data source '%s'. No driver class name specified", basicJdbcConfiguration.getName()));
                }
            }
        }

        return calculatedDriverClassName;
    }

    /**
     * Determines the URL based on the configured value. If the URL is
     * not configured, search for an embedded database driver on the
     * classpath and retrieve a default URL for it.
     *
     * @return The calculated URL
     */
    public String getUrl() {
        final String url = basicJdbcConfiguration.getConfiguredUrl();
        if (calculatedUrl == null || StringUtils.hasText(url)) {
            calculatedUrl = url;
            if (!StringUtils.hasText(calculatedUrl) && embeddedDatabaseConnection.isPresent()) {
                calculatedUrl = embeddedDatabaseConnection.get().getUrl(basicJdbcConfiguration.getName());
            }
            if (!StringUtils.hasText(calculatedUrl)) {
                throw new ConfigurationException(String.format("Error configuring data source '%s'. No URL specified", basicJdbcConfiguration.getName()));
            }
        }

        return calculatedUrl;
    }

    /**
     * Determines the username based on the configured value. If the
     * username is not configured and an embedded database driver is
     * on the classpath, return 'sa'.
     *
     * @return The calculated username
     */
    public String getUsername() {
        final String username = basicJdbcConfiguration.getConfiguredUsername();
        if (calculatedUsername == null || StringUtils.hasText(username)) {
            calculatedUsername = username;
            if (!StringUtils.hasText(calculatedUsername) && JdbcDatabaseManager.isEmbedded(basicJdbcConfiguration.getDriverClassName())) {
                calculatedUsername = "sa";
            }
        }

        return calculatedUsername;
    }

    /**
     * Determines the password based on the configured value. If the
     * password is not configured and an embedded database driver is
     * on the classpath, return an empty string.
     *
     * @return The calculated password
     */
    public String getPassword() {
        final String password = basicJdbcConfiguration.getConfiguredPassword();
        if (calculatedPassword == null || StringUtils.hasText(password)) {
            calculatedPassword = password;
            if (!StringUtils.hasText(calculatedPassword) && JdbcDatabaseManager.isEmbedded(basicJdbcConfiguration.getDriverClassName())) {
                calculatedPassword = "";
            }
        }

        return calculatedPassword;
    }

    /**
     * Determines the validation query based on the configured value. If the
     * validation query is not configured, search pre-defined databases for
     * a match based on the URL. If a match is found, return the defined
     * validation query for that database.
     *
     * @return The calculated validation query
     */
    public String getValidationQuery() {
        final String validationQuery = basicJdbcConfiguration.getConfiguredValidationQuery();
        if (calculatedValidationQuery == null || StringUtils.hasText(validationQuery)) {
            calculatedValidationQuery = validationQuery;
            if (!StringUtils.hasText(calculatedValidationQuery)) {
                JdbcDatabaseManager.findDatabase(getUrl()).ifPresent(db ->
                    calculatedValidationQuery = db.getValidationQuery());
            }
        }

        return calculatedValidationQuery;
    }

    private boolean driverClassIsPresent(String className) {
        return ClassUtils.isPresent(className, this.getClass().getClassLoader());
    }
}
