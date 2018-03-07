package io.micronaut.inject.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
import io.micronaut.context.ApplicationContext
import io.micronaut.context.DefaultApplicationContext
import io.micronaut.context.env.DefaultEnvironment
import io.micronaut.context.env.Environment
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
