package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static MeterRegistryFactory.METRICS_ENABLED

class MeterRegistryFactorySpec extends Specification {

    def "wireup the beans manually"() {
        when:
        MeterRegistryFactory factory = new MeterRegistryFactory()

        then:
        factory.simpleMeterRegistry()
        factory.compositeMeterRegistry()
    }

    void "verify SimpleMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        context.findBean(SimpleMeterRegistry).isPresent()
        context.findBean(CompositeMeterRegistry).isPresent()
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
        context.findBean(SimpleMeterRegistry).isPresent() == result

        where:
        cfg             | setting | result
        METRICS_ENABLED | false   | false
        METRICS_ENABLED | true    | true
    }

    @Unroll
    void "verify CompositeMeterRegistry present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CompositeMeterRegistry).isPresent() == result
        context.findBean(SimpleMeterRegistry).isPresent() == result

        where:
        cfg             | setting | result
        METRICS_ENABLED | false   | false
        METRICS_ENABLED | true    | true
    }

}
