package io.micronaut.inject.qualifiers.replaces.defaultimpl

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NonUniqueBeanException
import spock.lang.Specification

class ReplacesDefaultImplSpec extends Specification {

    void "test A replacement"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesDefaultImplSpec'])

        when:"retrieve the interface"
        A a = context.getBean(A)

        then:"the default implementation is replaced"
        noExceptionThrown()
        a instanceof A2

        cleanup:
        context.close()
    }

    void "test B replacement"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesDefaultImplSpec'])

        when:"retrieve the interface"
        List<B> bs = context.getBeansOfType(B).toList()

        then:"the default implementation is replaced"
        noExceptionThrown()
        bs.size() == 2
        bs.stream().noneMatch({ b -> b instanceof B1 })

        cleanup:
        context.close()
    }

    void "test nested default impls"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesDefaultImplSpec'])

        when:
        F f = context.getBean(F)

        then:
        noExceptionThrown()
        f instanceof F2

        when:
        context.getBean(E)

        then:
        thrown(NonUniqueBeanException)

        when:
        List<E> es = context.getBeansOfType(E).toList()

        then:
        es.size() == 2
        es.stream().anyMatch({e -> e instanceof F2})
        es.stream().anyMatch({e -> e instanceof E2})

        cleanup:
        context.close()
    }

    void "test normal replacement still works"() {
        given:
        ApplicationContext context = ApplicationContext.run(['spec.name':'ReplacesDefaultImplSpec'])

        when:"retrieve the interface"
        List<C> cs = context.getBeansOfType(C).toList()

        then:"the target implementation is replaced"
        noExceptionThrown()
        cs.size() == 2
        cs.stream().noneMatch({ c -> c instanceof C2 })

        cleanup:
        context.close()
    }
}
