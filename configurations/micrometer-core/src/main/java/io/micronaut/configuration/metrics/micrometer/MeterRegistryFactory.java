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

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

/**
 * Factory for all supported MetricRegistry beans.
 */
@Factory
public class MeterRegistryFactory {
    public static final String CFG_ROOT = "micronaut.metrics.";
    public static final String METRICS_ENABLED = CFG_ROOT + "enabled";

    /**
     * Create a SimpleMeterRegistry bean if metrics are enabled, true by default.
     * <p>
     * Micrometer packs with a SimpleMeterRegistry that holds the latest value of each meter
     * in memory and doesn't export the data anywhere. If you don’t yet have a preferred monitoring
     * system, you can get started playing with metrics by using the simple registry.
     *
     * @return A SimpleMeterRegistry.
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    SimpleMeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();
    }

    /**
     * Create a CompositeMeterRegistry bean if metrics are enabled, true by default.
     *
     * @return A CompositeMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    CompositeMeterRegistry compositeMeterRegistry() {
        return new CompositeMeterRegistry();
    }
}
