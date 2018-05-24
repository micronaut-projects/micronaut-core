package io.micronaut.configuration.metrics.micrometer.graphite;

import io.micrometer.core.instrument.Clock;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.time.Duration;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteConfiguration.GRAPHITE_ENABLED;

/**
 * The GraphiteMeterRegistryFactory that will configure and create a graphite meter registry.
 */
@Factory
public class GraphiteMeterRegistryFactory {

    private final GraphiteConfig graphiteConfig;

    /**
     * Sets the underlying graphite meter registry properties.
     *
     * @param graphiteConfigurationProperties graphite properties
     */
    GraphiteMeterRegistryFactory(final GraphiteConfigurationProperties graphiteConfigurationProperties) {
        this.graphiteConfig = new GraphiteConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String host() {
                return graphiteConfigurationProperties.getHost();
            }

            @Override
            public int port() {
                return graphiteConfigurationProperties.getPort();
            }

            @Override
            public boolean enabled() {
                return graphiteConfigurationProperties.isEnabled();
            }

            @Override
            public Duration step() {
                return Duration.parse(graphiteConfigurationProperties.getStep());
            }
        };
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
    @Requires(property = METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = GRAPHITE_ENABLED, value = "true", defaultValue = "true")
    GraphiteMeterRegistry graphiteMeterRegistry() {
        return new GraphiteMeterRegistry(graphiteConfig, Clock.SYSTEM);
    }
}
