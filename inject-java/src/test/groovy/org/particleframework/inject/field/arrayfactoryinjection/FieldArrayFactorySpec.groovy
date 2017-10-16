package org.particleframework.inject.field.arrayfactoryinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class FieldArrayFactorySpec extends Specification {

    void "test injection with field supplied by a provider"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a field that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.all != null
        b.all[0] instanceof AImpl
        ((AImpl)b.all[0]).c != null
        ((AImpl)b.all[0]).c2 != null
        b.all[0].is(context.getBean(AImpl))
    }
}


