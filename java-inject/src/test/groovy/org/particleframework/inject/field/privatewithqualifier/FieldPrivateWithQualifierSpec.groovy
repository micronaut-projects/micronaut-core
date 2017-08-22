package org.particleframework.inject.field.privatewithqualifier

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.field.protectedwithqualifier.OneA
import org.particleframework.inject.field.protectedwithqualifier.TwoA
import spock.lang.Ignore
import spock.lang.Specification

class FieldPrivateWithQualifierSpec extends Specification {

    @Ignore
    void "test that a field with a qualifier is injected correctly"() {
        given:
        BeanContext context = new DefaultBeanContext()
        context.start()

        when:
        B b = context.getBean(B)

        then:
        b.a instanceof OneA
        b.a2 instanceof TwoA
    }
}






