package org.particleframework.inject.constructor.simpleinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.BeanDefinition
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

        when:
        BeanDefinition bd = context.getBeanDefinition(B)
        BeanDefinition bd2 = context.getBeanDefinition(B2)

        then: "The constructor argument is added to the required components"
        bd.getRequiredComponents().size() == 1
        bd.getRequiredComponents().contains(A)
        bd2.getRequiredComponents().size() == 1
        bd2.getRequiredComponents().contains(A)
    }
}