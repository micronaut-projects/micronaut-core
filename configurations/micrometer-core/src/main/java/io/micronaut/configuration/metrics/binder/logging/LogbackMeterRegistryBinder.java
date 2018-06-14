package io.micronaut.configuration.metrics.binder.logging;

import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import org.slf4j.LoggerFactory;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;

/**
 * Binder factory that will create the logback metrics beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@Requires(classes = LoggerFactory.class)
@Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
@Requires(property = MICRONAUT_METRICS + "binders.logback.enabled", value = "true", defaultValue = "true")
public class LogbackMeterRegistryBinder {

    /**
     * Logback metrics bean.
     *
     * @return logbackMetrics bean
     */
    @Bean
    public LogbackMetrics logbackMetrics() {
        return new LogbackMetrics();
    }
}
