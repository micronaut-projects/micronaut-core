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

package io.micronaut.configuration.metrics.micrometer.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The AtlasMeterRegistryFactory that will configure and create a atlas meter registry.
 */
@Factory
public class AtlasMeterRegistryFactory {
    public static final String ATLAS_CONFIG = MICRONAUT_METRICS_EXPORT + ".atlas";
    public static final String ATLAS_ENABLED = ATLAS_CONFIG + ".enabled";

    private final AtlasConfig atlasConfig;

    /**
     * Sets the underlying atlas meter registry properties.
     *
     * @param atlasConfigurationProperties atlas properties
     */
    AtlasMeterRegistryFactory(final AtlasConfigurationProperties atlasConfigurationProperties) {
        this.atlasConfig = atlasConfigurationProperties;
    }

    /**
     * Create a AtlasMeterRegistry bean if global metrics are enables
     * and the atlas is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A AtlasMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(property = ATLAS_ENABLED, value = StringUtils.TRUE, defaultValue = StringUtils.TRUE)
    @Requires(beans = CompositeMeterRegistry.class)
    AtlasMeterRegistry atlasMeterRegistry() {
        return new AtlasMeterRegistry(atlasConfig, Clock.SYSTEM);
    }
}
