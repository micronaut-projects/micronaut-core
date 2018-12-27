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

package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer;
import io.micronaut.configuration.metrics.aggregator.MicrometerMeterRegistryConfigurer;
import io.micronaut.configuration.metrics.annotation.RequiresMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;
import java.util.Collection;

/**
 * Factory for all supported MetricRegistry beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
public class MeterRegistryFactory {

    public static final String MICRONAUT_METRICS = "micronaut.metrics.";
    public static final String MICRONAUT_METRICS_BINDERS = MICRONAUT_METRICS + "binders";
    public static final String MICRONAUT_METRICS_ENABLED = MICRONAUT_METRICS + "enabled";
    public static final String MICRONAUT_METRICS_EXPORT = MICRONAUT_METRICS + "export";

    /**
     * Create a CompositeMeterRegistry bean if metrics are enabled, true by default.
     *
     * @return A CompositeMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    CompositeMeterRegistry compositeMeterRegistry() {
        return new CompositeMeterRegistry();
    }

    /**
     * Creates a MeterRegistryConfigurer bean if the metrics are endabled, true by default.
     * <p>
     * This bean adds the filters and binders to the metric registry.
     *
     * @param binders list of binder beans
     * @param filters list of filter beans
     * @return meterRegistryConfigurer bean
     */
    @Bean
    @Primary
    @Singleton
    @RequiresMetrics
    MeterRegistryConfigurer meterRegistryConfigurer(Collection<MeterBinder> binders,
                                                    Collection<MeterFilter> filters) {
        return new MicrometerMeterRegistryConfigurer(binders, filters);
    }
}
