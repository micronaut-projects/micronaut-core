package io.micronaut.inject.lifecycle.beanwithpostconstruct

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
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
