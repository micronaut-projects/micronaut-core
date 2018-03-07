package io.micronaut.inject.method.simpleinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class SetterInjectionSpec extends Specification {

    void "test injection via setter with interface"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"Alpha bean is obtained that has a setter with @Inject"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.getA().equals(context.getBean(AImpl))

    }
}

