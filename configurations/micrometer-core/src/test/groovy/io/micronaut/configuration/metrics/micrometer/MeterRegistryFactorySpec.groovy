package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MeterRegistryFactorySpec extends Specification {

    def "wireup the beans manually"() {
        when:
        MeterRegistryFactory factory = new MeterRegistryFactory()

        then:
        factory.compositeMeterRegistry()
        factory.meterRegistryConfigurer([], [])
    }

    void "verify beans created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.containsBean(CompositeMeterRegistry)
        context.containsBean(SimpleMeterRegistry)
        context.containsBean(MeterRegistryConfigurer)

        cleanup:
        context.close()
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        context.findBean(CompositeMeterRegistry).isPresent()
        context.findBean(SimpleMeterRegistry).isPresent()
        compositeRegistry
        compositeRegistry?.registries?.size() == 1
        compositeRegistry.registries*.class.containsAll([SimpleMeterRegistry])

        cleanup:
        context.close()
    }

    @Unroll
    void "verify SimpleMeterRegistry present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CompositeMeterRegistry).isPresent() == result
        context.findBean(SimpleMeterRegistry).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
    }

    @Unroll
    void "verify CompositeMeterRegistry present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(CompositeMeterRegistry).isPresent() == result
        context.findBean(SimpleMeterRegistry).isPresent() == result

        cleanup:
        context.close()

        where:
        cfg                       | setting | result
        MICRONAUT_METRICS_ENABLED | false   | false
        MICRONAUT_METRICS_ENABLED | true    | true
    }
}
