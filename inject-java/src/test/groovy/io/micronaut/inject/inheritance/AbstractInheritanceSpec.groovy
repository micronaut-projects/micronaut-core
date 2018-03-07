package io.micronaut.inject.inheritance

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class AbstractInheritanceSpec extends Specification {

    void "test values are injected for abstract parent class"() {
        given:
        BeanContext context  = new DefaultBeanContext()
        context.start()

        when:"A bean is retrieved that has abstract inherited values"
        B b = context.getBean(B)

        then:"The values are injected"
        b.a != null
        b.another != null
        b.a.is(b.another)
        b.packagePrivate != null
        b.packagePrivate.is(b.another)
    }
}
