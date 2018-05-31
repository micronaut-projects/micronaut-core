package io.micronaut.configuration.metrics.micrometer

import io.micrometer.atlas.AtlasMeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micronaut.context.ApplicationContext
import spock.lang.Specification
import spock.lang.Unroll

import static io.micronaut.configuration.metrics.micrometer.MeterRegistryFactory.MICRONAUT_METRICS_ENABLED
import static io.micronaut.configuration.metrics.micrometer.atlas.AtlasMeterRegistryFactory.ATLAS_ENABLED

class CompositeMeterRegistryInitializerSpec extends Specification {

    @Unroll
    def "test getting the registry types"() {
        given:
        ApplicationContext context = ApplicationContext.run(configuration)
        CompositeMeterRegistryInitializer initializer = new CompositeMeterRegistryInitializer()

        when:
        def types = initializer.findMeterRegistryTypes(context)

        then:
        types.sort() == expectedRegistries.sort()
        types.size() == expectedRegistryCount

        where:
        configuration                        || expectedRegistries                        | expectedRegistryCount
        [:]                                  || [SimpleMeterRegistry, AtlasMeterRegistry] | 2
        [(ATLAS_ENABLED): false]             || [SimpleMeterRegistry]                     | 1
        [(MICRONAUT_METRICS_ENABLED): false] || []                                        | 0
    }

}
