package io.micronaut.inject.injectionpoint

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.injectionpoint.notlazytarget.ProxiedSomeBeanConsumer
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

    def "inject point points are propagated for proxies with non-lazy target"() {
        given:
        def consumer = applicationContext.getBean(ProxiedSomeBeanConsumer)

        expect:
        consumer.fromConstructor.name == 'one'
        consumer.fromField.name == 'two'
        consumer.fromMethod.name == 'three'
        consumer.someType.name == 'four'
    }

    def "inject point points are propagated for proxies with lazy target"() {
        given:
        def consumer = applicationContext.getBean(io.micronaut.inject.injectionpoint.lazytarget.ProxiedSomeBeanConsumer)

        expect:
        consumer.fromConstructor.name == 'one'
        consumer.fromField.name == 'two'
        consumer.fromMethod.name == 'three'
        consumer.someType.name == 'four'
    }

    def "inject point points are propagated for proxies with cacheable lazy target"() {
        given:
        def consumer = applicationContext.getBean(io.micronaut.inject.injectionpoint.cacheablelazytarget.ProxiedSomeBeanConsumer)

        expect:
        consumer.fromConstructor.name == 'one'
        consumer.fromField.name == 'two'
        consumer.fromMethod.name == 'three'
        consumer.someType.name == 'four'
    }

}
