package io.micronaut.configuration.metrics.micrometer.dropwizard;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.dropwizard.DropwizardConfig;
import io.micrometer.core.instrument.dropwizard.DropwizardMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;

import java.util.concurrent.TimeUnit;

/**
 * Implements a Dropwizard console reporter meter registry.
 *
 * To enable it add the Dropwizard dependency; e.g. in Gradle build: compile 'io.dropwizard.metrics:metrics-core:3.2.6'
 *
 * To disable it: Declare a property metrics.export.dropwizard-console.enabled = false
 *
 * @see <a href="http://micrometer.io/docs/guide/consoleReporter">http://micrometer.io/docs/guide/consoleReporter</a>
 */
@Factory
public class DropwizardConsoleMeterFactory {
    public static final String DROPWIZARD_CONSOLE_LOGGING_ENABLED = "metrics.export.dropwizard-console.enabled";

    /**
     *
     * @return The DropwizardMeterRegistry
     */
    @Bean
    @Context
    @Requires(classes = ConsoleReporter.class)
    @Requires(property = DROPWIZARD_CONSOLE_LOGGING_ENABLED, value = "true", defaultValue = "false")
    public DropwizardMeterRegistry dropwizardMeterRegistry() {
        MetricRegistry dropwizardRegistry = new MetricRegistry();

        ConsoleReporter reporter = ConsoleReporter.forRegistry(dropwizardRegistry)
          .convertRatesTo(TimeUnit.SECONDS)
          .convertDurationsTo(TimeUnit.MILLISECONDS)
          .build();
        reporter.start(1, TimeUnit.SECONDS);

        DropwizardConfig consoleConfig = new DropwizardConfig() {
            @Override
            public String prefix() {
                return "console";
            }

            @Override
            public String get(String key) {
                return null;
            }
        };

        return new DropwizardMeterRegistry(consoleConfig, dropwizardRegistry, HierarchicalNameMapper.DEFAULT, Clock.SYSTEM) {
            @Override
            protected Double nullGaugeValue() {
                return null;
            }
        };
    }

}
