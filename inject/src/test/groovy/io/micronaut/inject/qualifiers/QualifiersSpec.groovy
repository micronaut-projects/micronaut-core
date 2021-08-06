package io.micronaut.inject.qualifiers


import jakarta.inject.Named
import jakarta.inject.Singleton
import spock.lang.Specification

import java.lang.annotation.Annotation

class QualifiersSpec extends Specification {

    def "check byAnnotations"() {
        when:
            def annotations = Qualifiers.byAnnotations(new Named() {

                @Override
                String value() {
                    return "test"
                }

                @Override
                Class<? extends Annotation> annotationType() {
                    return Named.class
                }
            }, new Singleton() {

                @Override
                Class<? extends Annotation> annotationType() {
                    return Singleton.class
                }
            })
        then:
            annotations instanceof CompositeQualifier
            annotations.qualifiers.length == 2
        when:
            def emptyAnnotations1 = Qualifiers.byAnnotations()
        then:
            emptyAnnotations1 == null
        when:
            def emptyAnnotations2 = Qualifiers.byAnnotations(null)
        then:
            emptyAnnotations2 == null
    }

}
