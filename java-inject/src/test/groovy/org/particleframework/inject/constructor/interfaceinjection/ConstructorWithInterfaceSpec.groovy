package org.particleframework.inject.constructor.interfaceinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class ConstructorWithInterfaceSpec extends Specification {

    void "test injection with constructor with an interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a constructor that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
    }
}
