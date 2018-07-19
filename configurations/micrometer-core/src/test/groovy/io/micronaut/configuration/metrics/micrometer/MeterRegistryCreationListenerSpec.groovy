package io.micronaut.configuration.metrics.micrometer

import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.configuration.metrics.aggregator.MeterRegistryConfigurer
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static MeterRegistryFactory.MICRONAUT_METRICS_ENABLED

class MeterRegistryCreationListenerSpec extends Specification {

    @Unroll
    void "verify beans present == #result for #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(MeterRegistryConfigurer).isPresent() == result
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
    void "verify beans present and registered with composite"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.findBean(MeterRegistryConfigurer).isPresent()
        context.findBean(CompositeMeterRegistry).isPresent()
        context.findBean(SimpleMeterRegistry).isPresent()
        context.findBean(CompositeMeterRegistry).get().registries.size() == 1

        cleanup:
        context.close()
    }
}
