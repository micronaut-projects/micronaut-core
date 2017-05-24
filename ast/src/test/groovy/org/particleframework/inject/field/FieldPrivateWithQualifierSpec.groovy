package org.particleframework.inject.field

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.qualifiers.One
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named

/**
 * Created by graemerocher on 15/05/2017.
 */
class FieldPrivateWithQualifierSpec extends Specification {

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

    static class B {
        @Inject @One private FieldProtectedWithQualifierSpec.A a
        @Inject @Named('twoA') private FieldProtectedWithQualifierSpec.A a2
    }


}






