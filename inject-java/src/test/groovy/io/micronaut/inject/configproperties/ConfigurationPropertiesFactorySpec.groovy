package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConfigurationPropertiesFactorySpec extends Specification {

    void "test replacing a configuration properties via a factory"() {
        ApplicationContext ctx = ApplicationContext.run(["spec.name": ConfigurationPropertiesFactorySpec.simpleName])

        expect:
        ctx.getBean(Neo4jProperties).uri.getHost() == "google.com"
    }
}
