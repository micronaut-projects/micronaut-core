package org.particleframework.inject.context

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class RegisterSingletonSpec extends Specification {

    void "test register singleton method"() {
        given:
        BeanContext context = new DefaultBeanContext().start()
        def b = new B()

        when:
        context.registerSingleton(b)

        then:
        context.getBean(B) == b
        b.a != null
        b.a == context.getBean(A)
    }
}
