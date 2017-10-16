package org.particleframework.inject.env

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import spock.lang.Specification

class EnvironmentInjectSpec extends Specification {

    void "test inject the environment object"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("foo")
                                                        .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        a.environment != null
        a.environment.name == "foo"
        a.defaultEnvironment != null
        a.defaultEnvironment.name == "foo"
    }
}