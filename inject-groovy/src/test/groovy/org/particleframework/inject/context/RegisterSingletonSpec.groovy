package org.particleframework.inject.context

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by graemerocher on 26/05/2017.
 */
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

    @Singleton
    static class A {}

    static class B {
        @Inject A a
    }
}
