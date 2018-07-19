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

package io.micronaut.configuration.metrics.micrometer.prometheus;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The PrometheusMeterRegistryFactory that will configure and create a prometheus meter registry.
 */
@Factory
public class PrometheusMeterRegistryFactory {
    public static final String PROMETHEUS_CONFIG = MICRONAUT_METRICS_EXPORT + ".prometheus";
    public static final String PROMETHEUS_ENABLED = PROMETHEUS_CONFIG + ".enabled";

    private final PrometheusConfig prometheusConfig;

    /**
     * Sets the underlying prometheus meter registry properties.
     *
     * @param prometheusConfigurationProperties prometheus properties
     */
    PrometheusMeterRegistryFactory(final PrometheusConfigurationProperties prometheusConfigurationProperties) {
        this.prometheusConfig = prometheusConfigurationProperties;
    }

    /**
     * Create a PrometheusMeterRegistry bean if global metrics are enables
     * and the prometheus is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A PrometheusMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = PROMETHEUS_ENABLED, value = "true", defaultValue = "true")
    @Requires(beans = CompositeMeterRegistry.class)
    PrometheusMeterRegistry prometheusConfig() {
        return new PrometheusMeterRegistry(prometheusConfig);
    }
}
