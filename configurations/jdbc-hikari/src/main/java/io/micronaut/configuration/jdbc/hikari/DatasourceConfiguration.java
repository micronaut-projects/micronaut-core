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

package io.micronaut.configuration.jdbc.hikari;

import com.zaxxer.hikari.HikariConfig;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.jdbc.BasicJdbcConfiguration;
import io.micronaut.jdbc.CalculatedSettings;

import javax.annotation.PostConstruct;

/**
 * Allows the configuration of Hikari JDBC data sources. All properties on
 * {@link HikariConfig} are available to be configured.
 *
 * If the url, driver class, username, or password are missing, sensible defaults
 * will be provided when possible. If no configuration beyond the datasource name
 * is provided, an in memory datastore will be configured based on the available
 * drivers on the classpath.
 *
 * @author James Kleeh
 * @since 1.0
 */
@EachProperty(value = BasicJdbcConfiguration.PREFIX, primary = "default")
public class DatasourceConfiguration extends HikariConfig implements BasicJdbcConfiguration {

    private CalculatedSettings calculatedSettings;
    private String name;

    /**
     * Constructor.
     * @param name name that comes from properties
     */
    public DatasourceConfiguration(@Parameter String name) {
        super();
        this.name = name;
        this.calculatedSettings = new CalculatedSettings(this);
    }

    /**
     * Hikari validates against the fields instead of using getters so
     * the following is required to populate the calculated values into
     * the fields.
     */
    @PostConstruct
    void postConstruct() {
        if (getConfiguredUrl() == null) {
            setUrl(getUrl());
        }
        if (getConfiguredDriverClassName() == null) {
            setDriverClassName(getDriverClassName());
        }
        if (getConfiguredUsername() == null) {
            setUsername(getUsername());
        }
        if (getConfiguredPassword() == null) {
            setPassword(getPassword());
        }
        if (getConfiguredValidationQuery() == null) {
            setValidationQuery(getValidationQuery());
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getConfiguredUrl() {
        return getJdbcUrl();
    }

    @Override
    public String getUrl() {
        return calculatedSettings.getUrl();
    }

    /**
     * Setter.
     *
     * @param url url of connection
     */
    public void setUrl(String url) {
        setJdbcUrl(url);
    }

    @Override
    public String getConfiguredDriverClassName() {
        return super.getDriverClassName();
    }

    @Override
    public String getDriverClassName() {
        return calculatedSettings.getDriverClassName();
    }

    @Override
    public String getConfiguredUsername() {
        return super.getUsername();
    }

    @Override
    public String getUsername() {
        return calculatedSettings.getUsername();
    }

    @Override
    public String getConfiguredPassword() {
        return super.getPassword();
    }

    @Override
    public String getPassword() {
        return calculatedSettings.getPassword();
    }

    @Override
    public String getConfiguredValidationQuery() {
        return getConnectionTestQuery();
    }

    @Override
    public String getValidationQuery() {
        return calculatedSettings.getValidationQuery();
    }

    /**
     * Setter.
     *
     * @param validationQuery string of query
     */
    public void setValidationQuery(String validationQuery) {
        setConnectionTestQuery(validationQuery);
    }

    /**
     * Get Jndi name.
     * @return jndiName
     */
    public String getJndiName() {
        return getDataSourceJNDI();
    }

    /**
     * Setter.
     * @param jndiName jndi name
     */
    public void setJndiName(String jndiName) {
        setDataSourceJNDI(jndiName);
    }
}
