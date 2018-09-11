package io.micronaut.configuration.metrics.binder.system

import io.micrometer.core.instrument.binder.system.FileDescriptorMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.core.instrument.binder.system.UptimeMetrics
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_BINDERS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class SystemMeterRegistryBinderFactorySpec extends Specification {

    def "test getting the beans manually"() {
        when:
        def binder = new SystemMeterRegistryBinderFactoryFactory()

        then:
        binder.uptimeMetrics()
        binder.processorMetrics()
        binder.fileDescriptorMetrics()
    }

    def "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(SystemMeterRegistryBinderFactoryFactory)
        context.containsBean(UptimeMetrics)
        context.containsBean(ProcessorMetrics)
        context.containsBean(FileDescriptorMetrics)

        cleanup:
        context.close()
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(SystemMeterRegistryBinderFactoryFactory).isPresent() == binderPresent
        if (binderPresent) {
            context.findBean(UptimeMetrics).isPresent() == uptimePresent
            context.findBean(ProcessorMetrics).isPresent() == processorPresent
            context.findBean(FileDescriptorMetrics).isPresent() == filePresent
        }

        cleanup:
        context.close()

        where:
        cfg                                             | setting | binderPresent | uptimePresent | processorPresent | filePresent
        MICRONAUT_METRICS_ENABLED                       | true    | true          | true          | true             | true
        MICRONAUT_METRICS_ENABLED                       | false   | false         | false         | false            | false
        MICRONAUT_METRICS_BINDERS + ".uptime.enabled"    | false   | true          | false         | true             | true
        MICRONAUT_METRICS_BINDERS + ".processor.enabled" | false   | true          | true          | false            | true
        MICRONAUT_METRICS_BINDERS + ".files.enabled"     | false   | true          | true          | true             | false
    }
}
