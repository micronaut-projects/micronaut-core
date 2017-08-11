package org.particleframework.inject.lifecycle.beanwithpostconstruct

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class BeanWithPostConstructSpec extends Specification{

    void "test that a bean with a protected post construct hook that the hook is invoked"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a != null
        b.injectedFirst
        b.setupComplete
    }
}
