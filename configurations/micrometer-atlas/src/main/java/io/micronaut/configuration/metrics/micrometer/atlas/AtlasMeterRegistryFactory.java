package io.micronaut.configuration.metrics.micrometer.atlas;

import com.netflix.spectator.atlas.AtlasConfig;
import io.micrometer.atlas.AtlasMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

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
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = ATLAS_ENABLED, value = "true", defaultValue = "true")
    AtlasMeterRegistry atlasMeterRegistry() {
        return new AtlasMeterRegistry(atlasConfig, Clock.SYSTEM);
    }
}
