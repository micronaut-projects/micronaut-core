package io.micronaut.configuration.metrics.binder.jvm;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS;
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED;

/**
 * Binder factory that will create the jvm metrics beans.
 *
 * @author Christian Oestreich
 * @since 1.0
 */
@Factory
@Requires(property = MICRONAUT_METRICS_ENABLED, value = "true", defaultValue = "true")
@Requires(property = MICRONAUT_METRICS + "binders.jvm.enabled", value = "true", defaultValue = "true")
public class JvmMeterRegistryBinder {

    /**
     * JVM GC metrics bean.
     *
     * @return jvmGcMetrics
     */
    @Bean
    @Primary
    @Singleton
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * JVM Memory metrics bean.
     *
     * @return jvmMemoryMetrics
     */
    @Bean
    @Primary
    @Singleton
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * JVM Thread metrics bean.
     *
     * @return jvmThreadMetrics
     */
    @Bean
    @Primary
    @Singleton
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * JVM Class loader metrics bean.
     *
     * @return classLoaderMetrics
     */
    @Bean
    @Primary
    @Singleton
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }
}
