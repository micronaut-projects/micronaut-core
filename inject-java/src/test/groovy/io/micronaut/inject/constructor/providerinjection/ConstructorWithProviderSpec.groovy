package io.micronaut.inject.constructor.providerinjection

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class ConstructorWithProviderSpec extends Specification {

    void "test injection with constructor supplied by a provider"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:"A bean is obtained which has a constructor that depends on a bean provided by a provider"
        B b =  context.getBean(B)

        then:"The implementation is injected"
        b.a != null
        b.a instanceof AImpl
        b.a.is(context.getBean(AImpl))
        ((AImpl)(b.a)).c != null
        ((AImpl)(b.a)).c2 != null
    }
}