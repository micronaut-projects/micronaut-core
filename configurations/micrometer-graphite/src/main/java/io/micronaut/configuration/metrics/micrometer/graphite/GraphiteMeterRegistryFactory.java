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

@Factory
public class GraphiteMeterRegistryFactory {

    private final GraphiteConfig graphiteConfig;

    GraphiteMeterRegistryFactory(final GraphiteConfigurationProperties graphiteConfiguration) {
        this.graphiteConfig = new GraphiteConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String host() {
                return graphiteConfiguration.getHost();
            }

            @Override
            public int port() {
                return graphiteConfiguration.getPort();
            }

            @Override
            public boolean enabled() {
                return graphiteConfiguration.isEnabled();
            }

            @Override
            public Duration step() {
                return Duration.parse(graphiteConfiguration.getStep());
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
