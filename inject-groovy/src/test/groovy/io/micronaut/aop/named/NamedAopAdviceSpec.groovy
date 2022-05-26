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

    void "test manually named beans with AOP advice"() {
        given:
        def context = ApplicationContext.run()

        expect:
        context.getBean(OtherInterface, Qualifiers.byName("first")).doStuff() == 'first'
        context.getBean(OtherInterface, Qualifiers.byName("second")).doStuff() == 'second'
        context.getBeansOfType(OtherInterface).size() == 2
        context.getBeansOfType(OtherInterface).every({ it instanceof Intercepted })
        context.getBean(OtherBean).first.doStuff() == "first"
        context.getBean(OtherBean).second.doStuff() == "second"

        cleanup:
        context.close()
    }

    void "test named bean relying on non iterable config"() {
        given:
        def context = ApplicationContext.run(['other.interfaces.third': 'third'])

        expect:
        context.getBean(OtherInterface, Qualifiers.byName("third")).doStuff() == 'third'

        cleanup:
        context.close()
    }
}
