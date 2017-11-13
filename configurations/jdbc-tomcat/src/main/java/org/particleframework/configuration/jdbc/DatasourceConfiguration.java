package org.particleframework.configuration.jdbc;

import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.ForEach;
import org.particleframework.context.exceptions.ConfigurationException;
import org.particleframework.core.reflect.ClassUtils;
import org.particleframework.core.util.StringUtils;
import org.particleframework.jdbc.BasicConfiguration;
import org.particleframework.jdbc.CalculatedSettings;
import org.particleframework.jdbc.DatabaseDriver;
import org.particleframework.jdbc.EmbeddedDatabaseConnection;

import javax.annotation.PostConstruct;

@ForEach(property = "datasources", primary = "default")
public class DatasourceConfiguration extends PoolProperties implements BasicConfiguration {

    private CalculatedSettings calculatedSettings;

    public DatasourceConfiguration(@Argument String name) {
        super();
        this.setName(name);
        this.calculatedSettings = new CalculatedSettings(this);
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

    public String getJndiName() {
        return getDataSourceJNDI();
    }

    public void setJndiName(String jndiName) {
        setDataSourceJNDI(jndiName);
    }
}
