package org.particleframework.inject.constructor.simpleinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class ConstructorSimpleInjectionSpec extends Specification {

    void "test injection with constructor"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has a constructor with @Inject"
        B b =  context.getBean(B)
        B2 b2 =  context.getBean(B2)

        then:"The implementation is injected"
        b.a != null
        b2.a != null
        b2.a2 != null
    }
}