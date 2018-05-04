package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static MeterRegistryFactory.METRICS_ENABLED
import static MeterRegistryFactory.SIMPLE_METER_REGISTRY_ENABLED
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.COMPOSITE_METER_REGISTRY_ENABLED

class MeterRegistryFactorySpec extends Specification {

    void "verify SimpleMeterRegistry created by default"() {
        when:
            ApplicationContext context = ApplicationContext.run()

        then:
            context.containsBean(SimpleMeterRegistry)
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
            ApplicationContext context = ApplicationContext.run()
            CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
            compositeRegistry
            compositeRegistry?.registries?.size() == 1
            compositeRegistry.registries*.class.contains SimpleMeterRegistry
    }

    @Unroll
    void "verify SimpleMeterRegistry  present == #result for #cfg = #setting"() {
        when:
            ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
            context.findBean(SimpleMeterRegistry).isPresent() == result

        where:
            cfg                           | setting | result
            METRICS_ENABLED               | false   | false
            METRICS_ENABLED               | true    | true
            SIMPLE_METER_REGISTRY_ENABLED | true    | true
            SIMPLE_METER_REGISTRY_ENABLED | false   | false
    }

    @Unroll
    void "verify CompositeMeterRegistry present == #result for #cfg = #setting"() {
        when:
            ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
            context.findBean(CompositeMeterRegistry).isPresent() == result

        where:
            cfg                              | setting | result
            METRICS_ENABLED                  | false   | false
            METRICS_ENABLED                  | true    | true
            COMPOSITE_METER_REGISTRY_ENABLED | true    | true
            COMPOSITE_METER_REGISTRY_ENABLED | false   | false
    }

}
