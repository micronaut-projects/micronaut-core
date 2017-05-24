package org.particleframework.inject.field

import org.particleframework.context.BeanContext
import org.particleframework.context.DefaultBeanContext
import org.particleframework.inject.qualifiers.One
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class FieldProtectedWithQualifierSpec extends Specification {

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
        @Inject @One protected A a
        @Inject @Named('twoA') protected A a2
    }

    static  interface A {

    }

}


@Singleton
class OneA implements FieldProtectedWithQualifierSpec.A {

}
@Singleton
class TwoA implements FieldProtectedWithQualifierSpec.A {

}



