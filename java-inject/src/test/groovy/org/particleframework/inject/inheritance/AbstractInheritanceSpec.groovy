package org.particleframework.inject.inheritance

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Ignore
import spock.lang.Specification
/**
 * Created by graemerocher on 15/05/2017.
 */
class AbstractInheritanceSpec extends Specification {

    @Ignore
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
    }

}
