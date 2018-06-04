package io.micronaut.configuration.metrics.micrometer.statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

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
    @Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = STATSD_ENABLED, value = "true", defaultValue = "true")
    @Requires(beans = CompositeMeterRegistry.class)
    StatsdMeterRegistry statsdMeterRegistry() {
        return new StatsdMeterRegistry(statsdConfig, Clock.SYSTEM);
    }
}
