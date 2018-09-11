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

package io.micronaut.configuration.metrics.binder.datasource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micronaut.jdbc.metadata.DataSourcePoolMetadata;

import javax.sql.DataSource;
import javax.validation.constraints.NotNull;
import java.util.function.Function;

/**
 * A {@link MeterBinder} for a {@link DataSource}.
 *
 * @author Jon Schneider
 * @author Phillip Webb
 * @author Christian Oestreich
 * @since 1.0.0
 */
public class DataSourcePoolMetricsBinder implements MeterBinder {

    private final DataSource dataSource;

    private final DataSourcePoolMetadata metadataProvider;

    private final Iterable<Tag> tags;

    /**
     * Constructor for creaging data source pool metrics.
     *
     * @param dataSource       The datasource to bind metrics for
     * @param metadataProvider A composite object of all the metadataProviders
     * @param dataSourceName   The name of the datasource
     * @param tags             Any k:v pairs to add as tags
     */
    DataSourcePoolMetricsBinder(DataSource dataSource,
                                DataSourcePoolMetadata metadataProvider,
                                String dataSourceName,
                                Iterable<Tag> tags) {
        this.dataSource = dataSource;
        this.metadataProvider = metadataProvider;
        this.tags = Tags.concat(tags, "name", dataSourceName);
    }

    /**
     * Method for getting metadataProvider object for datasource that will bind the pool metrics.
     *
     * @param meterRegistry the meter registry object
     */
    @Override
    public void bindTo(@NotNull MeterRegistry meterRegistry) {
        if (this.metadataProvider != null) {
            bindPoolMetadata(meterRegistry, "active", DataSourcePoolMetadata::getActive);
            bindPoolMetadata(meterRegistry, "max", DataSourcePoolMetadata::getMax);
            bindPoolMetadata(meterRegistry, "min", DataSourcePoolMetadata::getMin);
            bindPoolMetadata(meterRegistry, "usage", DataSourcePoolMetadata::getUsage);
        }
    }

    private <N extends Number> void bindPoolMetadata(MeterRegistry registry,
                                                     String metricName,
                                                     Function<DataSourcePoolMetadata, N> function) {
        bindDataSource(registry,
                metricName,
                this.getValueFunction(function));
    }

    private <N extends Number> Function<DataSource, N> getValueFunction(
            Function<DataSourcePoolMetadata, N> function) {
        return dataSource -> function.apply(this.metadataProvider);
    }

    /**
     * Creates the gauges for getting pool metrics.
     *
     * @param meterRegistry Meter registry to bind to
     * @param metricName    Metric name to bind
     * @param function      Function to retrieve metric
     * @param <N>           Metric Value
     */
    private <N extends Number> void bindDataSource(MeterRegistry meterRegistry,
                                                   String metricName,
                                                   Function<DataSource, N> function) {
        if (function.apply(this.dataSource) != null) {
            meterRegistry.gauge("jdbc.connections." + metricName,
                    this.tags,
                    this.dataSource,
                    m -> function.apply(m).doubleValue());
        }
    }
}
