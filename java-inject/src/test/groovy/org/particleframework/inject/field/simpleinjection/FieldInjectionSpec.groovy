package org.particleframework.inject.field.simpleinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class FieldInjectionSpec extends Specification {

    void "test injection via field with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"Alpha bean is obtained that has a field with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
    }
}

