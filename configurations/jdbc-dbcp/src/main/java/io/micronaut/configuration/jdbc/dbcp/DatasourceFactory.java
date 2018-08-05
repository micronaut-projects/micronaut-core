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
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.sql.DataSource;

/**
 * Creates a dbcp data source for each configuration bean.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class DatasourceFactory {

    /**
     * Method to create a metadata object that allows pool value lookup for each datasource object.
     *
     * @return a {@link DataSourcePoolMetadataProvider}
     */
    @EachBean(DataSource.class)
    public DataSourcePoolMetadata dbcpDataSourcePoolMetadata(
            @Parameter String dataSourceName,
            DataSource dataSource) {
        if (dataSource instanceof BasicDataSource) {
            return new DbcpDataSourcePoolMetadata((BasicDataSource) dataSource);
        } else if ((dataSource instanceof DelegatingDataSource && ((DelegatingDataSource) dataSource).getTargetDataSource() instanceof BasicDataSource)) {
            return new DbcpDataSourcePoolMetadata((BasicDataSource) ((DelegatingDataSource) dataSource).getTargetDataSource());
        }
        return null;
    }
}
