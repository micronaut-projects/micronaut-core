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

package io.micronaut.configuration.jdbc.dbcp;

import io.micronaut.configuration.jdbc.dbcp.metadata.DbcpDataSourcePoolMetadata;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.apache.commons.dbcp2.BasicDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Optional;

/**
 * Creates a dbcp data source for each configuration bean.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class DatasourceFactory {

    private static final Logger LOG = LoggerFactory.getLogger(DatasourceFactory.class);

    /**
     * Method to create a metadata object that allows pool value lookup for each datasource object.
     *
     * @return a {@link DataSourcePoolMetadataProvider}
     */
    @EachBean(DataSource.class)
    public DataSourcePoolMetadata dbcpDataSourcePoolMetadata(
            @Parameter String dataSourceName,
            DataSource dataSource) {
        DbcpDataSourcePoolMetadata dbcpDataSourcePoolMetadata = null;

        if (dataSource instanceof BasicDataSource) {
            dbcpDataSourcePoolMetadata = new DbcpDataSourcePoolMetadata((BasicDataSource) dataSource);
        } else if (isDelegatingDataSource(dataSource)) {
            dbcpDataSourcePoolMetadata = getDataSource(dataSource).map(DbcpDataSourcePoolMetadata::new).orElse(null);
        }
        return dbcpDataSourcePoolMetadata;
    }

    /**
     * Retrieve the unwrapped datasource if it has been wrapped in a spring transactional aware datasource.
     *
     * @param delegatingDataSource a potentially wrapped datasource
     * @return the unwrapped datasource or null if not  spring transactional aware datasource
     */
    private Optional<BasicDataSource> getDataSource(DataSource delegatingDataSource) {
        BasicDataSource dataSource = null;

        try {
            Field targetDataSource = delegatingDataSource.getClass().getSuperclass().getDeclaredField("targetDataSource");
            targetDataSource.setAccessible(true);
            dataSource = (BasicDataSource) targetDataSource.get(delegatingDataSource);
        } catch (NoSuchFieldException | IllegalAccessException | NullPointerException ignore) {
            LOG.debug("Data source is not of type BasicDataSource or DelegatingDataSource, metrics will not be wired.");
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
            LOG.debug("Data source is not of type BasicDataSource or DelegatingDataSource, metrics will not be wired.");
        }
        return isDelegatingDataSource;
    }
}
