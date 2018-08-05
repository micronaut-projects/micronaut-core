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

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.configuration.jdbc.hikari.metadata.HikariDataSourcePoolMetadata;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DelegatingDataSource;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a Hikari data source for each configuration bean.
 *
 * @author James Kleeh
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class DatasourceFactory implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DatasourceFactory.class);
    private List<HikariUrlDataSource> dataSources = new ArrayList<>(2);


    private MeterRegistry meterRegistry;

    public DatasourceFactory(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * @param datasourceConfiguration A {@link DatasourceConfiguration}
     * @return A {@link HikariUrlDataSource}
     */
    @EachBean(DatasourceConfiguration.class)
    public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
        HikariUrlDataSource ds = new HikariUrlDataSource(datasourceConfiguration);
        if(this.meterRegistry != null){
            ds.setMetricRegistry(this.meterRegistry);
        }
        dataSources.add(ds);
        return ds;
    }

    /**
     * Method to create a metadata object that allows pool value lookup for each datasource object.
     *
     * @param dataSourceName The name of the datasource
     * @param dataSource     The datasource
     * @return a {@link io.micronaut.jdbc.metadata.DataSourcePoolMetadataProvider}
     */
    @EachBean(DataSource.class)
    public DataSourcePoolMetadata hikariDataSourcePoolMetadata(
            @Parameter String dataSourceName,
            DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            return new HikariDataSourcePoolMetadata((HikariDataSource) dataSource);
        } else if ((dataSource instanceof DelegatingDataSource && ((DelegatingDataSource) dataSource).getTargetDataSource() instanceof HikariDataSource)) {
            return new HikariDataSourcePoolMetadata((HikariDataSource) ((DelegatingDataSource) dataSource).getTargetDataSource());
        }
        return null;
    }

    @Override
    @PreDestroy
    public void close() {
        for (HikariUrlDataSource dataSource : dataSources) {
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
