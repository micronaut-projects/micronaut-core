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

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory for all supported MetricRegistry beans.
 */
@Factory
public class MeterRegistryFactory {
    public static final String COMPOSITE_REGISTRY_ENABLED = "metrics.composite-meter-registry.enabled";
    public static final String METRICS_ENABLED = "metrics.enabled";
    public static final String SIMPLE_METER_REGISTRY_ENABLED = "metrics.simple-meter-registry.enabled";

    private static final Logger LOG = LoggerFactory.getLogger(MeterRegistryFactory.class);

    /**
     * @return A standard Clock
     */
    @Bean
    @Primary
    @Context
    @Requires(missingBeans = io.micrometer.core.instrument.Clock.class)
    public Clock micrometerClock() {
        return Clock.SYSTEM;
    }

    /**
     * @param ctx - the ApplicationContext.
     * @return A SimpleMeterFactory.
     */
    @Bean
    @Primary
    @Context
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = SIMPLE_METER_REGISTRY_ENABLED, value = "true", defaultValue = "true")
    SimpleMeterRegistry simpleMeterRegistry(ApplicationContext ctx) {
        LOG.debug("Adding SimpleMeterRegistry bean");
        return new SimpleMeterRegistry();
    }

    /**
     * Create a CompositeMeterFactory bean if metrics are not disabled, and the composite-meter-registry is not disabled.
     * <p>
     * The default implementation is the Metrics.globalRegistry. To use your own CompositeMeterRegistry, replace this bean
     * with your own implementation
     *
     * @param ctx - the ApplicationContext.
     * @return A CompositeMeterFactory
     */
    @Bean
    @Primary
    @Context
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = COMPOSITE_REGISTRY_ENABLED, value = "true", defaultValue = "true")
    CompositeMeterRegistry compositeMeterRegistry(ApplicationContext ctx) {
        return Metrics.globalRegistry;
    }

}
