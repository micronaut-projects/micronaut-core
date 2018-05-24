package io.micronaut.configuration.metrics.micrometer.graphite

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteConfiguration.GRAPHITE_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteConfiguration.GRAPHITE_HOST
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteConfiguration.GRAPHITE_PORT
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteConfiguration.GRAPHITE_STEP

class GraphiteMeterRegistryFactorySpec extends Specification {

    def "wireup the bean manually"() {
        when:
        GraphiteMeterRegistryFactory factory = new GraphiteMeterRegistryFactory(new GraphiteConfigurationProperties())

        then:
        factory.graphiteMeterRegistry()
    }

    void "verify GraphiteMeterRegistry is created by default when this configuration used"() {
        when:
        ApplicationContext context = ApplicationContext.run()

        then:
        context.getBeansOfType(MeterRegistry).size() == 3
        context.getBeansOfType(MeterRegistry)*.class*.simpleName.containsAll(['CompositeMeterRegistry', 'SimpleMeterRegistry', 'GraphiteMeterRegistry'])
    }

    void "verify CompositeMeterRegistry created by default"() {
        when:
        ApplicationContext context = ApplicationContext.run()
        CompositeMeterRegistry compositeRegistry = context.getBean(CompositeMeterRegistry)

        then:
        compositeRegistry
        compositeRegistry.registries.size() == 2
        compositeRegistry.registries*.class.containsAll([GraphiteMeterRegistry, SimpleMeterRegistry])
    }

    @Unroll
    void "verify GraphiteMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
        ApplicationContext context = ApplicationContext.run([(cfg): setting])

        then:
        context.findBean(GraphiteMeterRegistry).isPresent() == result

        where:
        cfg              | setting | result
        METRICS_ENABLED  | false   | false
        METRICS_ENABLED  | true    | true
        GRAPHITE_ENABLED | true    | true
        GRAPHITE_ENABLED | false   | false
    }

    void "verify GraphiteMeterRegistry bean exists with default config"() {
        when:
        ApplicationContext context = ApplicationContext.run([(GRAPHITE_ENABLED): true])
        Optional<GraphiteMeterRegistry> meterRegistry = context.findBean(GraphiteMeterRegistry)

        then:
        meterRegistry.isPresent()
        meterRegistry.get().config.enabled()
        meterRegistry.get().config.port() == 2004
        meterRegistry.get().config.host() == "localhost"
        meterRegistry.get().config.step() == Duration.ofMinutes(1)
    }

    void "verify GraphiteMeterRegistry bean exists changed port, host and step"() {
        when:
        ApplicationContext context = ApplicationContext.run([
                (GRAPHITE_ENABLED): true,
                (GRAPHITE_HOST)   : "zerocool",
                (GRAPHITE_PORT)   : 2345,
                (GRAPHITE_STEP)   : "PT2M",
        ])
        Optional<GraphiteMeterRegistry> meterRegistry = context.findBean(GraphiteMeterRegistry)

        then:
        meterRegistry.isPresent()
        meterRegistry.get().config.enabled()
        meterRegistry.get().config.port() == 2345
        meterRegistry.get().config.host() == "zerocool"
        meterRegistry.get().config.step() == Duration.ofMinutes(2)
    }
}
