package io.micronaut.inject.lifecycle.beancreationeventlistener

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import spock.lang.Specification

class BeanCreationEventListenerSpec extends Specification {

    void "test bean creation listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:
        B b= context.getBean(B)

        then:
        b instanceof ChildB
        b.name == "good"

    }
}
