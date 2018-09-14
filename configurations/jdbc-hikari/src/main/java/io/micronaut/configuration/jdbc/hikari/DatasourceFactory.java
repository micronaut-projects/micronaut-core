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

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS;

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

    private ApplicationContext applicationContext;

    /**
     * Default constructor.
     * @param applicationContext The application context
     */
    public DatasourceFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Method to wire up all the HikariCP connections based on the {@link DatasourceConfiguration}.
     * If a {@link MeterRegistry} bean exists then the registry will be added to the datasource.
     *
     * @param datasourceConfiguration A {@link DatasourceConfiguration}
     * @return A {@link HikariUrlDataSource}
     */
    @EachBean(DatasourceConfiguration.class)
    public DataSource dataSource(DatasourceConfiguration datasourceConfiguration) {
        HikariUrlDataSource ds = new HikariUrlDataSource(datasourceConfiguration);
        addMeterRegistry(ds);
        dataSources.add(ds);
        return ds;
    }

    private void addMeterRegistry(HikariUrlDataSource ds) {
        try {
            MeterRegistry meterRegistry = getMeterRegistry();
            if (ds != null && meterRegistry != null &&
                    this.applicationContext
                            .getProperty(MICRONAUT_METRICS_BINDERS + ".jdbc.enabled",
                                    boolean.class).orElse(true)) {
                ds.setMetricRegistry(meterRegistry);
            }
        } catch (NoClassDefFoundError ignore) {
            LOG.info("Could not wire metrics to HikariCP as there is no class of type MeterRegistry on the classpath, io.micronaut.configuration:micrometer-core library missing.");
        }
    }

    private MeterRegistry getMeterRegistry() {
        return this.applicationContext.containsBean(MeterRegistry.class) ?
                this.applicationContext.getBean(MeterRegistry.class) : null;
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
