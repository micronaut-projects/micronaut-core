package org.particleframework.inject.env

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.Environment
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