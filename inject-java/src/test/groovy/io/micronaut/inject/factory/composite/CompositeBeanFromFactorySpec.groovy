package io.micronaut.inject.factory.composite

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CompositeBeanFromFactorySpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test composite factory method"() {
        given:
        def registry = context.getBean(SomeRegistry)

        expect:
        registry instanceof CompositeSomeRegistry
        registry.someRegistries.size() == 1
        registry.someRegistries.any { it instanceof SomeRegistryA }
    }
}
