package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class EndpointConfigurationSpec extends Specification {

    void "test sensitive inheritance"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(
                ['endpoints.foo.enabled': true, 'endpoints.all.sensitive': true]
        ))
        context.start()

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then: "Foo to be sensitive because it was inherited from all"
        foo.isSensitive().get()
        foo.isEnabled().get()

        cleanup:
        context.close()
    }

    void "test enabled inheritance"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(
                ['endpoints.foo.sensitive': true, 'endpoints.all.enabled': false]
        ))
        context.start()

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then: "Foo to not be enabled because it was inherited from all"
        foo.isSensitive().get()
        !foo.isEnabled().get()

        cleanup:
        context.close()
    }

    void "test sensitive is not present"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(
                ['endpoints.foo.enabled': true]
        ))
        context.start()

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then:
        !foo.isSensitive().isPresent()

        cleanup:
        context.close()
    }

    void "test enabled is not present"() {
        given:
        ApplicationContext context = ApplicationContext.build("test")
        context.environment.addPropertySource(PropertySource.of(
                ['endpoints.foo.sensitive': true]
        ))
        context.start()

        when:
        EndpointConfiguration foo = context.getBean(EndpointConfiguration, Qualifiers.byName("foo"))

        then:
        !foo.isEnabled().isPresent()

        cleanup:
        context.close()
    }
}
