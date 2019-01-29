package io.micronaut.inject.close

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class BeanCloseOrderSpec extends Specification {

    static List<Class> closed = []

    void "test close order"() {
        given:
        ApplicationContext ctx = ApplicationContext.run(["spec.name": getClass().simpleName])
        ctx.getBean(A)
        ctx.getBean(B)
        ctx.getBean(C)
        ctx.getBean(D)

        when:
        ctx.close()

        then:
        closed == [A,B,C,D]
    }
}
