package io.micronaut.configuration.metrics.micrometer.graphite

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.graphite.GraphiteMeterRegistryFactory.GRAPHITE_ENABLED

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.composite.CompositeMeterRegistry
import io.micrometer.core.instrument.search.MeterNotFoundException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.graphite.GraphiteMeterRegistry
import io.micronaut.configuration.metrics.micrometer.MeterRegistryCustomizer
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import spock.lang.Specification
import spock.lang.Unroll

class GraphiteMeterRegistryFactorySpec extends Specification {

    void "verify GraphiteMeterRegistry is created by default"() {
        when:
            ApplicationContext ctx = ApplicationContext.run()
            GraphiteMeterRegistry graphiteRegistry = ctx.getBean(GraphiteMeterRegistry)

        then:
            graphiteRegistry

        cleanup:
            ctx.stop()
    }

    void "verify GraphiteMeterRegistry is registered with CompositeMetrRegistry created by default "() {
        when:
            ApplicationContext ctx = ApplicationContext.run()
            CompositeMeterRegistry compositeMeterRegistry = ctx.getBean(CompositeMeterRegistry)

        then:
            compositeMeterRegistry.registries*.class.contains(GraphiteMeterRegistry)

        cleanup:
            ctx.stop()
    }

    @Unroll
    void "verify GraphiteMeterRegistry bean exists = #result when config #cfg = #setting"() {
        when:
            ApplicationContext ctx = ApplicationContext.run([(cfg) : setting])

        then:
            ctx.findBean(GraphiteMeterRegistry).isPresent() == result

        cleanup:
            ctx.stop()

        where:
            cfg              | setting | result
            METRICS_ENABLED  | false   | false
            METRICS_ENABLED  | true    | true
            GRAPHITE_ENABLED | true    | true
            GRAPHITE_ENABLED | false   | false
    }

    void "verify the GraphiteMeterRegistry can be customized"() {
        when:
            ApplicationContext ctx = ApplicationContext.run()

            MeterRegistry registry = ctx.getBean(GraphiteMeterRegistry)
            registry.counter('test.app')

            SimpleMeterRegistry simpleMeterRegistry = ctx.getBean(SimpleMeterRegistry)
            simpleMeterRegistry.counter('foo.bar')

        then:
            registry.get("test.app").tags('application', 'MyApp').counter()         // tags common for all registries
            registry.get("test.app").tags('region', 'us-east-2').counter()          // only graphite registry gets this
            simpleMeterRegistry.get('foo.bar').tags('application', 'MyApp').counter()

        cleanup:
            ctx.stop()
    }

    void "verify another registry doesn't get the GraphiteMeterRegistry customization"() {
        given:
            ApplicationContext ctx = ApplicationContext.run()

            SimpleMeterRegistry simpleMeterRegistry = ctx.getBean(SimpleMeterRegistry)
            simpleMeterRegistry.counter('foo.bar')

        when:
            simpleMeterRegistry.get('foo.bar').tags('region', 'us-east-2').gauge() // only GraphiteMeterRegistry should get these customized tags

        then:
            thrown(MeterNotFoundException)

        cleanup:
            ctx.stop()
    }
}

@Context
class CommonRegistryCustomizer implements MeterRegistryCustomizer {

    @Override
    void customize(MeterRegistry registry) {
        registry.config().commonTags('application', 'MyApp')
    }

    @Override
    boolean supports(MeterRegistry registry) {
        true
    }
}

@Context
class GraphiteRegistryCustomizer implements MeterRegistryCustomizer {

    @Override
    void customize(MeterRegistry registry) {
        registry.config().commonTags('region', 'us-east-2')
    }

    @Override
    boolean supports(MeterRegistry registry) {
        GraphiteMeterRegistry.isAssignableFrom(registry.class)
    }
}
