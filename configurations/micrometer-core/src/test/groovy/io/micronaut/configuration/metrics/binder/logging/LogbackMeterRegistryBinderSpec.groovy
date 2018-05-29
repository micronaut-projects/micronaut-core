package io.micronaut.configuration.metrics.binder.logging

import io.micrometer.core.instrument.binder.logging.LogbackMetrics
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class LogbackMeterRegistryBinderSpec extends Specification {

    def "test getting the beans manually"() {
        when:
        def binder = new LogbackMeterRegistryBinder()

        then:
        binder.logbackMetrics()
    }

    def "test getting the beans"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(LogbackMeterRegistryBinder)
        context.containsBean(LogbackMetrics)

        cleanup:
        context.close()
    }

    @Unroll
    def "test getting the beans #cfg #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(LogbackMeterRegistryBinder).isPresent() == setting
        context.findBean(LogbackMetrics).isPresent() == setting

        cleanup:
        context.close()

        where:
        cfg                                           | setting
        MICRONAUT_METRICS_ENABLED                     | true
        MICRONAUT_METRICS_ENABLED                     | false
        MICRONAUT_METRICS + "binders.logback.enabled" | true
        MICRONAUT_METRICS + "binders.logback.enabled" | false
    }

}
