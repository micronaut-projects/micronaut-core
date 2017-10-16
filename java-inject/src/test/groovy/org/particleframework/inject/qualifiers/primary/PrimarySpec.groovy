package org.particleframework.inject.qualifiers.primary

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import spock.lang.Specification
/**
 * Created by graemerocher on 26/05/2017.
 */
class PrimarySpec extends Specification {

    void "test the @Primary annotation influences bean selection"() {

        given:
        BeanContext context = BeanContext.run()

        when:"A bean has a dependency on an interface with multiple impls"
        B b = context.getBean(B)

        then:"The impl marked with @Primary is selected"
        b.all.size() == 2
        b.all.any() { it instanceof A1 }
        b.all.any() { it instanceof A2 }
        b.a instanceof A2
    }
}
