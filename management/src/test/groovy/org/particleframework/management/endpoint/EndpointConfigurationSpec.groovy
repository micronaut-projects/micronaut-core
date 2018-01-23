package org.particleframework.management.endpoint

import org.particleframework.context.ApplicationContext
import org.particleframework.context.env.MapPropertySource
import org.particleframework.context.env.PropertySource
import org.particleframework.inject.qualifiers.Qualifiers
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
