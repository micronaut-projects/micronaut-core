package io.micronaut.inject.value.nullablevalue

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class NullableValueSpec extends Specification {

    void "test value with nullable"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                ["exists.x":"fromConfig"], "test"
        )

        when:
        A a = context.getBean(A)

        then:
        a.nullField == null
        a.nonNullField == "fromConfig"
        a.nullConstructorArg == null
        a.nonNullConstructorArg == "fromConfig"
        a.nullMethodArg == null
        a.nonNullMethodArg == "fromConfig"
    }
}
