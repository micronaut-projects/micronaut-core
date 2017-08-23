package org.particleframework.inject.qualifiers.replaces

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Ignore
import spock.lang.Specification
/**
 * Created by graemerocher on 26/05/2017.
 */
class ReplacesSpec extends Specification {

    @Ignore
    void "test that a bean can be marked to replace another bean"() {
        given:
        BeanContext context = new DefaultBeanContext().start()

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl that replaces the other impl is the only one present"
        b.all.size() == 1
        !b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2
    }
}
