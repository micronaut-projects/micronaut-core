package org.particleframework.inject.lifecycle.beancreationeventlistener

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
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
