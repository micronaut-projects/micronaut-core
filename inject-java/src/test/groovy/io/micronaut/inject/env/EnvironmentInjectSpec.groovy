package io.micronaut.inject.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.Environment
import spock.lang.Specification

class EnvironmentInjectSpec extends Specification {

    void "test inject the environment object"() {
        given:
        ApplicationContext applicationContext = ApplicationContext.run("foo")

        when:
        A a = applicationContext.getBean(A)

        then:
        a.environment != null
        a.environment.activeNames.contains("foo")
        a.environment.activeNames.contains(Environment.TEST)
        a.defaultEnvironment != null
        a.defaultEnvironment.activeNames.contains("foo")
    }
}