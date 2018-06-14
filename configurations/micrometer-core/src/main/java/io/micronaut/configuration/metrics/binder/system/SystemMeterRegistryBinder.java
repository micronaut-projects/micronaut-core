package io.micronaut.configuration.metrics.binder.system;

import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;

/**
 * Binder factory that will create the system metrics beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
public class SystemMeterRegistryBinder {

    /**
     * Uptime metrics bean.
     *
     * @return uptimeMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS + "binders.uptime.enabled", value = "true", defaultValue = "true")
    public UptimeMetrics uptimeMetrics() {
        return new UptimeMetrics();
    }

    /**
     * Processor metrics bean.
     *
     * @return processorMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS + "binders.processor.enabled", value = "true", defaultValue = "true")
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     * Files metrics bean.
     *
     * @return fileDescriptorMetrics bean
     */
    @Bean
    @Singleton
    @Primary
    @Requires(property = MICRONAUT_METRICS + "binders.files.enabled", value = "true", defaultValue = "true")
    public FileDescriptorMetrics fileDescriptorMetrics() {
        return new FileDescriptorMetrics();
    }

}
