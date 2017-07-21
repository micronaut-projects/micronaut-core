package org.particleframework.inject.field.optionalinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class FieldOptionalSpec extends Specification {

    void "test injection of optional objects"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained that has an optional field with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        !b.c.isPresent()
    }
}


