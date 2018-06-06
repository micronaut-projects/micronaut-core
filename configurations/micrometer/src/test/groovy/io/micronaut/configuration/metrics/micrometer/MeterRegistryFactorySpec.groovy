package io.micronaut.configuration.metrics.micrometer

import static MeterRegistryFactory.METRICS_ENABLED
import static MeterRegistryFactory.SIMPLE_METER_REGISTRY_ENABLED
import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.COMPOSITE_REGISTRY_ENABLED

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

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
            Optional<CompositeMeterRegistry> opt = context.findBean(CompositeMeterRegistry)

        then:
            opt.isPresent()
    }

    void "verify CompositeMeterRegistry creation can be turned off"() {
        when:
            ApplicationContext context = ApplicationContext.run(['metrics.composite-meter-registry.enabled': false])
            Optional<CompositeMeterRegistry> opt = context.findBean(CompositeMeterRegistry)

        then:
            !opt.isPresent()
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
            cfg                        | setting | result
            METRICS_ENABLED            | false   | false
            METRICS_ENABLED            | true    | true
            COMPOSITE_REGISTRY_ENABLED | true    | true
            COMPOSITE_REGISTRY_ENABLED | false   | false
    }

}
