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
package org.particleframework.configuration.jdbc.dbcp;

import org.apache.commons.dbcp2.BasicDataSource;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.jdbc.BasicJdbcConfiguration;
import org.particleframework.jdbc.CalculatedSettings;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.SQLException;

/**
 * Allows the configuration of Apache DBCP JDBC data sources. All properties on
 * {@link BasicDataSource} are available to be configured.
 *
 * If the url, driver class, username, or password are missing, sensible defaults
 * will be provided when possible. If no configuration beyond the datasource name
 * is provided, an in memory datastore will be configured based on the available
 * drivers on the classpath.
 *
 * @author James Kleeh
 * @since 1.0
 */
@ForEach(property = "datasources", primary = "default")
public class DatasourceConfiguration extends BasicDataSource implements BasicJdbcConfiguration {

    private final CalculatedSettings calculatedSettings;
    private final String name;

    public DatasourceConfiguration(@Argument String name) {
        super();
        this.name = name;
        this.calculatedSettings = new CalculatedSettings(this);
    }

    /**
     *  Apache DBCP uses the fields instead of using getters to create a
     *  connection, so the following is required to populate the calculated
     *  values into the fields.
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

    @PreDestroy
    void preDestroy() throws SQLException {
        this.close();
    }

    public String getName() {
        return this.name;
    }

    @Override
    public String getDriverClassName() {
        return calculatedSettings.getDriverClassName();
    }

    @Override
    public String getConfiguredDriverClassName() {
        return super.getDriverClassName();
    }

    @Override
    public String getUrl() {
        return calculatedSettings.getUrl();
    }

    @Override
    public String getConfiguredUrl() {
        return super.getUrl();
    }

    @Override
    public String getUsername() {
        return calculatedSettings.getUsername();
    }

    @Override
    public String getConfiguredUsername() {
        return super.getUsername();
    }

    @Override
    public String getPassword() {
        return calculatedSettings.getPassword();
    }

    @Override
    public String getConfiguredPassword() {
        return super.getPassword();
    }

    @Override
    public String getValidationQuery() {
        return calculatedSettings.getValidationQuery();
    }

    @Override
    public String getConfiguredValidationQuery() {
        return super.getValidationQuery();
    }

}
