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

package io.micronaut.configuration.jdbc.tomcat;

import io.micronaut.configuration.jdbc.tomcat.metadata.TomcatDataSourcePoolMetadata;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Creates a tomcat data source for each configuration bean.
 *
 * @author James Kleeh
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class DatasourceFactory implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatasourceFactory.class);
    private List<org.apache.tomcat.jdbc.pool.DataSource> dataSources = new ArrayList<>(2);

    /**
     * @param datasourceConfiguration A {@link DatasourceConfiguration}
     * @return An Apache Tomcat {@link DataSource}
     */
    @EachBean(DatasourceConfiguration.class)
    public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource(datasourceConfiguration);
        dataSources.add(ds);
        return ds;
    }

    /**
     * Method to create a metadata object that allows pool value lookup for each datasource object.
     *
     * @param dataSourceName The name of the datasource
     * @param dataSource     The datasource
     * @return a {@link TomcatDataSourcePoolMetadata}
     */
    @EachBean(DataSource.class)
    @Requires(beans = {DatasourceConfiguration.class})
    public TomcatDataSourcePoolMetadata tomcatPoolDataSourceMetadataProvider(
            @Parameter String dataSourceName,
            DataSource dataSource) {

        TomcatDataSourcePoolMetadata dataSourcePoolMetadata = null;

        if (dataSource instanceof org.apache.tomcat.jdbc.pool.DataSource) {
            dataSourcePoolMetadata = new TomcatDataSourcePoolMetadata((org.apache.tomcat.jdbc.pool.DataSource) dataSource);
        } else if (isDelegatingDataSource(dataSource)) {
            dataSourcePoolMetadata = getDataSource(dataSource).map(TomcatDataSourcePoolMetadata::new).orElse(null);
        }
        return dataSourcePoolMetadata;
    }

    /**
     * Retrieve the unwrapped datasource if it has been wrapped in a spring transactional aware datasource.
     *
     * @param delegatingDataSource a potentially wrapped datasource
     * @return the unwrapped datasource or null if not  spring transactional aware datasource
     */
    private Optional<org.apache.tomcat.jdbc.pool.DataSource> getDataSource(DataSource delegatingDataSource) {
        org.apache.tomcat.jdbc.pool.DataSource dataSource = null;

        try {
            Field targetDataSource = delegatingDataSource.getClass().getSuperclass().getDeclaredField("targetDataSource");
            targetDataSource.setAccessible(true);
            dataSource = (org.apache.tomcat.jdbc.pool.DataSource) targetDataSource.get(delegatingDataSource);
        } catch (NoSuchFieldException | IllegalAccessException | NullPointerException ignore) {
            LOG.debug("Data source is not of type org.apache.tomcat.jdbc.pool.DataSource or DelegatingDataSource, metrics will not be wired.");
        }

        return Optional.ofNullable(dataSource);
    }

    /**
     * Check for whether the datasource has been wrapped in a spring transactional aware datasource.
     *
     * @param dataSource The datasource to check for wrapping
     * @return boolean if the datasource is wrapped
     */
    private boolean isDelegatingDataSource(DataSource dataSource) {
        boolean isDelegatingDataSource = false;
        try {
            isDelegatingDataSource = dataSource.getClass().getSuperclass().getDeclaredField("targetDataSource") != null;
        } catch (NoSuchFieldException | NullPointerException ignore) {
            LOG.debug("Data source is not of type org.apache.tomcat.jdbc.pool.DataSource or DelegatingDataSource, metrics will not be wired.");
        }
        return isDelegatingDataSource;
    }

    @Override
    @PreDestroy
    public void close() {
        for (org.apache.tomcat.jdbc.pool.DataSource dataSource : dataSources) {
            try {
                dataSource.close();
            } catch (Exception e) {
                if (LOG.isWarnEnabled()) {
                    LOG.warn("Error closing data source [" + dataSource + "]: " + e.getMessage(), e);
                }
            }
        }
    }

}
