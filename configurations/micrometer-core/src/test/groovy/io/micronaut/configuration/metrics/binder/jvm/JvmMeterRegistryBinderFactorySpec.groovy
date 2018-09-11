package io.micronaut.configuration.metrics.binder.jvm

import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class JvmMeterRegistryBinderFactorySpec extends Specification {

    def "test getting the beans manually"() {
        when:
        def binder = new JvmMeterRegistryBinderFactory()

        then:
        binder.jvmGcMetrics()
        binder.jvmMemoryMetrics()
        binder.jvmThreadMetrics()
        binder.classLoaderMetrics()
    }

    def "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(JvmMeterRegistryBinderFactory)
        context.containsBean(JvmGcMetrics)
        context.containsBean(JvmMemoryMetrics)
        context.containsBean(JvmThreadMetrics)
        context.containsBean(ClassLoaderMetrics)

        cleanup:
        context.close()
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(JvmMeterRegistryBinderFactory).isPresent() == setting
        context.findBean(JvmGcMetrics).isPresent() == setting
        context.findBean(JvmMemoryMetrics).isPresent() == setting
        context.findBean(JvmThreadMetrics).isPresent() == setting
        context.findBean(ClassLoaderMetrics).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                       | setting
        MICRONAUT_METRICS_ENABLED                 | true
        MICRONAUT_METRICS_ENABLED                 | false
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | true
        MICRONAUT_METRICS_BINDERS + ".jvm.enabled" | false
    }
}
