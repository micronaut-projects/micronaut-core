package org.particleframework.inject.env

import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.DefaultEnvironment
import org.particleframework.context.env.Environment
import spock.lang.Specification

import javax.inject.Inject

/**
 * Created by graemerocher on 12/06/2017.
 */
class EnvironmentInjectSpec extends Specification {

    void "test inject the environment object"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
                                                        .start()

        when:
        A a = applicationContext.getBean(A)

        then:
        a.environment != null
        a.defaultEnvironment != null
    }

    static class A {
        @Inject Environment environment

        @Inject DefaultEnvironment defaultEnvironment
    }
}
