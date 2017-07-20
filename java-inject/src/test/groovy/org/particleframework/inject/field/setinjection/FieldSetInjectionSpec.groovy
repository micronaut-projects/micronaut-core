package org.particleframework.inject.field.setinjection

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class FieldSetInjectionSpec extends Specification {
    void "test injection via setter that takes a set"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b =  context.getBean(B)

        then:
        b.all != null
        b.all.size() == 2
        b.all.contains(context.getBean(AImpl))
        b.all.contains(context.getBean(AnotherImpl))
    }
}