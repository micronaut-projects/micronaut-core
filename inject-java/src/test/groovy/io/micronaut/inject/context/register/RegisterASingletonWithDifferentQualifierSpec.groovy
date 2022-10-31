package io.micronaut.inject.context.register

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class RegisterASingletonWithDifferentQualifierSpec extends Specification {

    def "test registering the same singleton class with a different qualifier"() {
        given:
            def ctx = BeanContext.run()

        when:
            def q1 = ctx.createBean(Abc, Qualifiers.none())
            ctx.registerSingleton(Abc.class, q1, Qualifiers.byName("ONE"))
        then:
            noExceptionThrown()

        when:
            def q2 = ctx.getBean(Abc, Qualifiers.byName("ONE"))
        then:
            q1 == q2

        when:
            def q3 = ctx.createBean(Abc, Qualifiers.none())
            ctx.registerSingleton(Abc.class, q3, Qualifiers.byName("TWO"))
            def q4 = ctx.getBean(Abc, Qualifiers.byName("TWO"))

        then:
            q1 == q2
            q1 != q3
            q1 != q4
            q3 == q4
        and:
            ctx.getBeanRegistrations(Abc).size() == 3

        cleanup:
            ctx.close()
    }
}


