package io.micronaut.management.endpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.MapPropertySource
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
        foo.sensitive
        foo.enabled

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

        then: "Foo to be sensitive because it was inherited from all"
        foo.sensitive
        !foo.enabled

        cleanup:
        context.close()
    }
}
