package org.particleframework.jdbc;

import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.util.StringUtils;


public class CalculatedSettings {

    private String calculatedDriverClassName;
    private String calculatedUrl;
    private String calculatedUsername;
    private String calculatedPassword;
    private EmbeddedDatabaseConnection embeddedDatabaseConnection = EmbeddedDatabaseConnection.NONE;
    private BasicConfiguration basicConfiguration;

    public CalculatedSettings(BasicConfiguration basicConfiguration) {
        this.basicConfiguration = basicConfiguration;
        embeddedDatabaseConnection = EmbeddedDatabaseConnection.get(this.getClass().getClassLoader());
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
                    calculatedDriverClassName = DatabaseDriver.fromJdbcUrl(url).getDriverClassName();
                }

                if (!StringUtils.hasText(calculatedDriverClassName)) {
                    calculatedDriverClassName = this.embeddedDatabaseConnection.getDriverClassName();
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
            if (!StringUtils.hasText(calculatedUrl)) {
                calculatedUrl = this.embeddedDatabaseConnection.getUrl(basicConfiguration.getName());
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
            if (!StringUtils.hasText(calculatedUsername) && EmbeddedDatabaseConnection.isEmbedded(basicConfiguration.getDriverClassName())) {
                calculatedUsername = "sa";
            }
        }

        return calculatedUsername;
    }

    public String getPassword() {
        final String password = basicConfiguration.getConfiguredPassword();
        if (calculatedPassword == null || StringUtils.hasText(password)) {
            calculatedPassword = password;
            if (!StringUtils.hasText(calculatedPassword) && EmbeddedDatabaseConnection.isEmbedded(basicConfiguration.getDriverClassName())) {
                calculatedPassword = "";
            }
        }

        return calculatedPassword;
    }

}
