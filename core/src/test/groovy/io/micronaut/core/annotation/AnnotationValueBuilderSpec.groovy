package io.micronaut.core.annotation

import spock.lang.Specification
import spock.lang.Unroll

class AnnotationValueBuilderSpec extends Specification {

    void "test class value"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .value(String.class)
                .build()

        expect:
        av.getValue(String.class).isPresent()
        av.getValue(AnnotationClassValue.class).isPresent()
    }


    @Unroll
    void "test value for #type"() {
        given:
        def av = AnnotationValue.builder("Foo")
                .value(val)
                .build()

        expect:
        av.getValue(type).isPresent()
        av.getValue(type).get() == val

        where:
        val   | type
        1     | Integer
        1L    | Long
        true  | Boolean
        "str" | String
    }
}
