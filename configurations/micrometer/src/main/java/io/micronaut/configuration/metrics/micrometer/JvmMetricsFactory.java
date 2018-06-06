package io.micronaut.configuration.metrics.micrometer;

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Requires;

/**
 * A factory for the JVM meter binders provided by Micrometer.io. Basically we just make optional beans out of them.
 */
@Factory
public class JvmMetricsFactory {
    public static final String JVM_CLASSLOADER_METRICS_ENABLED = "metrics.jvm.classloader.enabled";
    public static final String JVM_GC_METRICS_ENABLED = "metrics.jvm.gc.enabled";
    public static final String JVM_MEM_METRICS_ENABLED = "metrics.jvm.mem.enabled";
    public static final String JVM_METRICS_ENABLED = "metrics.jvm.enabled";
    public static final String JVM_PROCESSOR_METRICS_ENABLED = "metrics.jvm.processor.enabled";
    public static final String JVM_THREAD_METRICS_ENABLED = "metrics.jvm.thread.enabled";

    /**
     *
     * @return Class loader metrics
     */
    @Bean
    @Primary
    @Context
    @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = JVM_CLASSLOADER_METRICS_ENABLED, value = "true", defaultValue = "true")
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     *
     * @return JVM GC metrics
     */
    @Bean
    @Primary
    @Context
    @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = JVM_GC_METRICS_ENABLED, value = "true", defaultValue = "true")
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     *
     * @return Jvm Memory Metrics
     */
    @Bean
    @Primary
    @Context
    @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = JVM_MEM_METRICS_ENABLED, value = "true", defaultValue = "true")
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     *
     * @return Processor Metrics
     */
    @Bean
    @Primary
    @Context
    @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = JVM_PROCESSOR_METRICS_ENABLED, value = "true", defaultValue = "true")
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    /**
     *
     * @return JVM Thread Metrics
     */
    @Bean
    @Primary
    @Context
    @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
    @Requires(property = JVM_THREAD_METRICS_ENABLED, value = "true", defaultValue = "true")
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }
}

