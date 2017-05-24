package org.particleframework.inject.method

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
class SetterWithQualifierSpec extends Specification {

    void "test that a property with a qualifier is injected correctly"() {
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
        private A a
        private A a2
        @Inject setA(@One A a) {
            this.a = a
        }
        @Inject setAnother(@Named('twoA') A a2) {
            this.a2 = a2
        }

        A getA() {
            return a
        }

        A getA2() {
            return a2
        }
    }

    static  interface A {

    }

}


@Singleton
class OneA implements SetterWithQualifierSpec.A {

}
@Singleton
class TwoA implements SetterWithQualifierSpec.A {

}




