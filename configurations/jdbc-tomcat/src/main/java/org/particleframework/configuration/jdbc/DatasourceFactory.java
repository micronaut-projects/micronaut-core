package org.particleframework.configuration.jdbc;

import org.apache.tomcat.jdbc.pool.DataSource;
import org.particleframework.context.annotation.Bean;
import org.particleframework.context.annotation.Factory;
import org.particleframework.context.annotation.ForEach;

@Factory
public class DatasourceFactory {

    @ForEach(DatasourceConfiguration.class)
    public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
        return new DataSource(datasourceConfiguration);
    }
}