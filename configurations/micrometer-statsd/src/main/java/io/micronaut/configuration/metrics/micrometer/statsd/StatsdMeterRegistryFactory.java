package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdFlavor;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.time.Duration;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;
import static io.micronaut.configuration.metrics.micrometer.statsd.StatsdConfiguration.STATSD_ENABLED;

/**
 * The StatsdMeterRegistryFactory that will configure and create a statsd meter registry.
 */
@Factory
public class StatsdMeterRegistryFactory {

    private final StatsdConfig statsdConfig;

    /**
     * Sets the underlying statsd meter registry properties.
     *
     * @param statsdConfigurationProperties statsd properties
     */
    StatsdMeterRegistryFactory(final StatsdConfigurationProperties statsdConfigurationProperties) {
        this.statsdConfig = new StatsdConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public StatsdFlavor flavor() {
                return statsdConfigurationProperties.getFlavor();
            }

            @Override
            public boolean enabled() {
                return statsdConfigurationProperties.isEnabled();
            }

            @Override
            public String host() {
                return statsdConfigurationProperties.getHost();
            }

            @Override
            public int port() {
                return statsdConfigurationProperties.getPort();
            }

            @Override
            public Duration step() {
                return Duration.parse(statsdConfigurationProperties.getStep());
            }
        };
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
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = STATSD_ENABLED, value = "true", defaultValue = "true")
    StatsdMeterRegistry statsdMeterRegistry() {
        return new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
    }
}
