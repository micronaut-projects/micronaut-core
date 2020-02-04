package io.micronaut.docs.context.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.env.PropertySource
import io.micronaut.core.util.CollectionUtils
import spock.lang.Specification

class EnvironmentSpec extends Specification {

    void "test run environment"() {
        // tag::env[]
        when:
        ApplicationContext applicationContext = ApplicationContext.run("test", "android")
        Environment environment = applicationContext.getEnvironment()

        then:
        environment.getActiveNames().contains("test")
        environment.getActiveNames().contains("android")
        // end::env[]

        cleanup:
        applicationContext.close()
    }

    void "test run environment with properties"() {
        // tag::envProps[]
        when:
        ApplicationContext applicationContext = ApplicationContext.run(
                PropertySource.of(
                        "test",
                        [
                            "micronaut.server.host": "foo",
                            "micronaut.server.port": 8080
                        ]
                ),
                "test", "android")
        Environment environment = applicationContext.getEnvironment()

        then:
        "foo" == environment.getProperty("micronaut.server.host", String.class).orElse("localhost")
        // end::envProps[]

        cleanup:
        applicationContext.close()

    }
}
