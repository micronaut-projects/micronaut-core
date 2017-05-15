package org.particleframework.inject.property

import org.particleframework.context.Context
import org.particleframework.context.DefaultContext
import org.particleframework.inject.qualifiers.One
import spock.lang.Specification

import javax.inject.Inject
import javax.inject.Named
import javax.inject.Qualifier
import javax.inject.Singleton
import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

/**
 * Created by graemerocher on 15/05/2017.
 */
class PropertyWithQualifierSpec extends Specification {

    void "test that a property with a qualifier is injected correctly"() {
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
        @Inject @One A a
        @Inject @Named('twoA') A a2
    }

    static  interface A {

    }

}


@Singleton
class OneA implements PropertyWithQualifierSpec.A {

}
@Singleton
class TwoA implements PropertyWithQualifierSpec.A {

}



