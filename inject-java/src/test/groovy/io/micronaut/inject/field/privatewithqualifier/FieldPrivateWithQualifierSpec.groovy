package io.micronaut.inject.field.privatewithqualifier

import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.field.protectedwithqualifier.OneA
import io.micronaut.inject.field.protectedwithqualifier.TwoA
import io.micronaut.context.BeanContext
import io.micronaut.context.DefaultBeanContext
import io.micronaut.inject.field.protectedwithqualifier.OneA
import io.micronaut.inject.field.protectedwithqualifier.TwoA
import spock.lang.Specification

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
}






