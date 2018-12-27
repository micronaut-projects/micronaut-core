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

package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The StatsdMeterRegistryFactory that will configure and create a statsd meter registry.
 */
@Factory
public class StatsdMeterRegistryFactory {

    public static final String STATSD_CONFIG = MICRONAUT_METRICS_EXPORT + ".statsd";
    public static final String STATSD_ENABLED = STATSD_CONFIG + ".enabled";

    private final StatsdConfig statsdConfig;

    /**
     * Sets the underlying statsd meter registry properties.
     *
     * @param statsdConfigurationProperties atlas properties
     */
    StatsdMeterRegistryFactory(final StatsdConfigurationProperties statsdConfigurationProperties) {
        this.statsdConfig = statsdConfigurationProperties;
    }

    /**
     * Create a StatsdMeterRegistry bean if global metrics are enables
     * and the statsd is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A StatsdMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = STATSD_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    StatsdMeterRegistry statsdMeterRegistry() {
        return new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
    }
}
