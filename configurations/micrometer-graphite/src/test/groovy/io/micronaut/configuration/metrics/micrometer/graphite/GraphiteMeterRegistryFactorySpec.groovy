package io.micronaut.configuration.metrics.micrometer.graphite

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteMeterRegistryFactory.GRAPHITE_ENABLED

class GraphiteMeterRegistryFactorySpec extends Specification {


    void "verify GraphiteMeterRegistry is created by default when this configuration used"() {
        when:
            ApplicationContext context = ApplicationContext.run()

        then:
            context.getBeansOfType(MeterRegistry)*.class*.simpleName.contains('GraphiteMeterRegistry')
    }

    @Unroll
    void "verify GraphiteMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
            ApplicationContext context = ApplicationContext.run([(cfg) : setting])

        then:
            context.findBean(GraphiteMeterRegistry).isPresent() == result

        where:
            cfg              | setting | result
            METRICS_ENABLED  | false   | false
            METRICS_ENABLED  | true    | true
            GRAPHITE_ENABLED | true    | true
            GRAPHITE_ENABLED | false   | false
    }
}
