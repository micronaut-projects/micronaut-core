package org.particleframework.inject.lifecycle.beaninitializingeventlistener

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification

class BeanInitializingEventListenerSpec extends Specification {
    void "test bean initializing event listener"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean is retrieved where a BeanInitializedEventListener is present"
        B b= context.getBean(B)

        then:"The event is triggered prior to @PostConstruct hooks"
        b.name == "CHANGED"

    }
}
