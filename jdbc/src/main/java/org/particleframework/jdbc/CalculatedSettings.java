/*
 * Copyright 2017 original authors
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
package org.particleframework.jdbc;

import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.util.StringUtils;

import java.util.Optional;

public class CalculatedSettings {

    private String calculatedDriverClassName;
    private String calculatedUrl;
    private String calculatedUsername;
    private String calculatedPassword;
    private String calculatedValidationQuery;
    private Optional<JdbcDatabaseManager.EmbeddedJdbcDatabase> embeddedDatabaseConnection;
    private BasicConfiguration basicConfiguration;

    public CalculatedSettings(BasicConfiguration basicConfiguration) {
        this.basicConfiguration = basicConfiguration;
        embeddedDatabaseConnection = JdbcDatabaseManager.get(this.getClass().getClassLoader());
    }

    public String getDriverClassName() {
        final String driverClassName = basicConfiguration.getConfiguredDriverClassName();
        if (calculatedDriverClassName == null || StringUtils.hasText(driverClassName)) {
            if (StringUtils.hasText(driverClassName)) {
                if (!driverClassIsLoadable(driverClassName)) {
                    throw new ConfigurationException(String.format("Error configuring data source '%s'. The driver class was not found on the classpath", basicConfiguration.getName()));
                }
                calculatedDriverClassName = driverClassName;
            } else {
                final String url = basicConfiguration.getUrl();
                if (StringUtils.hasText(url)) {
                    JdbcDatabaseManager.findDatabase(url).ifPresent(db ->
                            calculatedDriverClassName = db.getDriverClassName());
                }

                if (!StringUtils.hasText(calculatedDriverClassName) && embeddedDatabaseConnection.isPresent()) {
                    calculatedDriverClassName = this.embeddedDatabaseConnection.get().getDriverClassName();
                }

                if (!StringUtils.hasText(calculatedDriverClassName)) {
                    throw new ConfigurationException(String.format("Error configuring data source '%s'. No driver class name specified", basicConfiguration.getName()));
                }

            }
        }

        return calculatedDriverClassName;
    }


    private boolean driverClassIsLoadable(String className) {
        return ClassUtils.isPresent(className, null);
    }

    public String getUrl() throws ConfigurationException {
        final String url = basicConfiguration.getConfiguredUrl();
        if (calculatedUrl == null || StringUtils.hasText(url)) {
            calculatedUrl = url;
            if (!StringUtils.hasText(calculatedUrl) && embeddedDatabaseConnection.isPresent()) {
                calculatedUrl = embeddedDatabaseConnection.get().getUrl(basicConfiguration.getName());
                if (!StringUtils.hasText(calculatedUrl)) {
                    throw new ConfigurationException(String.format("Error configuring data source '%s'. No URL specified", basicConfiguration.getName()));
                }
            }
        }

        return calculatedUrl;
    }

    public String getUsername() {
        final String username = basicConfiguration.getConfiguredUsername();
        if (calculatedUsername == null || StringUtils.hasText(username)) {
            calculatedUsername = username;
            if (!StringUtils.hasText(calculatedUsername) && JdbcDatabaseManager.isEmbedded(basicConfiguration.getDriverClassName())) {
                calculatedUsername = "sa";
            }
        }

        return calculatedUsername;
    }

    public String getPassword() {
        final String password = basicConfiguration.getConfiguredPassword();
        if (calculatedPassword == null || StringUtils.hasText(password)) {
            calculatedPassword = password;
            if (!StringUtils.hasText(calculatedPassword) && JdbcDatabaseManager.isEmbedded(basicConfiguration.getDriverClassName())) {
                calculatedPassword = "";
            }
        }

        return calculatedPassword;
    }

    public String getValidationQuery() {
        final String validationQuery = basicConfiguration.getConfiguredValidationQuery();
        if (calculatedValidationQuery == null || StringUtils.hasText(validationQuery)) {
            calculatedValidationQuery = validationQuery;
            if (!StringUtils.hasText(calculatedValidationQuery)) {
                JdbcDatabaseManager.findDatabase(getUrl()).ifPresent(db ->
                    calculatedValidationQuery = db.getValidationQuery());
            }
        }

        return calculatedValidationQuery;
    }

}
