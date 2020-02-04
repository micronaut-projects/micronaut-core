package io.micronaut.inject.injectionpoint

import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class InjectionPointSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext applicationContext =
            ApplicationContext.run()

    def "void test that the injection point can be used to construct the object"() {
        given:
        def consumer = applicationContext.getBean(SomeBeanConsumer)

        expect:
        consumer.fromConstructor.name == 'one'
        consumer.fromField.name == 'two'
        consumer.fromMethod.name == 'three'
        consumer.someType.name == 'four'
    }

    def "void test that the injection point can be used in injected introduction AOP advice"() {
        given:
        def consumer = applicationContext.getBean(SomeClientConsumer)

        expect:
        consumer.fromConstructor.test() == 'one'
        consumer.fromField.test() == 'two'
        consumer.fromMethod.test() == 'three'
    }
}
