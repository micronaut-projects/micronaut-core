package io.micronaut.inject.factory.nullreturn

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.context.exceptions.NoSuchBeanException
import spock.lang.Specification

class NullReturnFactorySpec extends Specification {

    void "test factory that returns null"() {
        given:
        BeanContext beanContext = ApplicationContext.run()
        NullableFactory factory = beanContext.getBean(NullableFactory)

        when:
        beanContext.createBean(A, "null")

        then:
        thrown(NoSuchBeanException)

        when:
        A a = beanContext.createBean(A, "hello")

        then:
        a != null

        when:
        Collection<B> bs = beanContext.getBeansOfType(B)

        then:
        bs.size() == 2
        bs.any { it.name == "two" }
        bs.any { it.name == "three" }
        factory.bCalls == 3

        when:
        Collection<C> cs = beanContext.getBeansOfType(C)

        then:
        cs.size() == 1
        cs[0].name == "three"
        factory.cCalls == 3
        factory.bCalls == 3

        expect:
        beanContext.getBeansOfType(D).size() == 1
        factory.bCalls == 3
        factory.cCalls == 3
        factory.dCalls == 3

        when:
        beanContext.getBean(E)

        then:
        thrown(NoSuchBeanException)

        when:
        beanContext.getBean(F)

        then:
        thrown(BeanContextException)

        when:
        beanContext.getBean(FactoryConstructor)

        then:
        thrown(BeanContextException)

        cleanup:
        beanContext.close()
    }

    void "test it works as expected nested resolution"() {
        given:
        BeanContext beanContext = ApplicationContext.run()
        NullableFactory factory = beanContext.getBean(NullableFactory)

        expect:
        beanContext.getBeansOfType(D).size() == 1
        beanContext.getBeansOfType(C).size() == 1
        beanContext.getBeansOfType(B).size() == 2
        factory.bCalls == 3
        factory.cCalls == 3
        factory.dCalls == 3

        cleanup:
        beanContext.close()
    }
}
