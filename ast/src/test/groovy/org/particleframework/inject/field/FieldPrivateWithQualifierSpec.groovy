package org.particleframework.inject.field

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import org.particleframework.inject.qualifiers.One
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Created by graemerocher on 15/05/2017.
 */
class FieldPrivateWithQualifierSpec extends Specification {

    void "test that a field with a qualifier is injected correctly"() {
        given:
        Context context = new DefaultContext()
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






