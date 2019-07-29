package io.micronaut.aop.named

import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class NamedAopAdviceSpec extends Specification {

    void "test that named beans that have AOP advice applied lookup the correct target named bean - primary included"() {
        given:
        def context = ApplicationContext.run(
                'aop.test.named.default': 0,
                'aop.test.named.one': 1,
                'aop.test.named.two': 2,
        )

        expect:
        context.getBean(NamedInterface) instanceof Intercepted
        context.getBean(NamedInterface).doStuff() == 'default'
        context.getBean(NamedInterface, Qualifiers.byName("one")).doStuff() == 'one'
        context.getBean(NamedInterface, Qualifiers.byName("two")).doStuff() == 'two'
        context.getBeansOfType(NamedInterface).size() == 3
        context.getBeansOfType(NamedInterface).every({ it instanceof Intercepted })



        cleanup:
        context.close()
    }

    void "test that named beans that have AOP advice applied lookup the correct target named bean - no primary"() {
        given:
        def context = ApplicationContext.run(
                'aop.test.named.one': 1,
                'aop.test.named.two': 2,
        )

        expect:
        context.getBean(NamedInterface, Qualifiers.byName("one")).doStuff() == 'one'
        context.getBean(NamedInterface, Qualifiers.byName("two")).doStuff() == 'two'
        context.getBeansOfType(NamedInterface).size() == 2
        context.getBeansOfType(NamedInterface).every({ it instanceof Intercepted })



        cleanup:
        context.close()
    }
}
