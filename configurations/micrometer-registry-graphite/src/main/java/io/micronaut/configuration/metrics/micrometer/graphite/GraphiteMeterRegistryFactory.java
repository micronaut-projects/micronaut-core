package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_EXPORT;

/**
 * The GraphiteMeterRegistryFactory that will configure and create a graphite meter registry.
 */
@Factory
public class GraphiteMeterRegistryFactory {

    public static final String GRAPHITE_CONFIG = MICRONAUT_METRICS_EXPORT + ".graphite";
    public static final String GRAPHITE_ENABLED = GRAPHITE_CONFIG + ".enabled";

    private final GraphiteConfig graphiteConfig;

    /**
     * Sets the underlying graphite meter registry properties.
     *
     * @param graphiteConfigurationProperties atlas properties
     */
    GraphiteMeterRegistryFactory(final GraphiteConfigurationProperties graphiteConfigurationProperties) {
        this.graphiteConfig = graphiteConfigurationProperties;
    }

    /**
     * Create a GraphiteMeterRegistry bean if global metrics are enables
     * and the graphite is enabled.  Will be true by default when this
     * configuration is included in project.
     *
     * @return A GraphiteMeterRegistry
     */
    @Bean
    @Primary
    @Singleton
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = GRAPHITE_ENABLED, value = "true", defaultValue = "true")
    GraphiteMeterRegistry graphiteMeterRegistry() {
        return new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM);
    }
}
