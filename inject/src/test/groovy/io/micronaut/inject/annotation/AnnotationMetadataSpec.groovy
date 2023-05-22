package io.micronaut.inject.annotation

import io.micronaut.context.annotation.EachBean
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.core.annotation.TypeHint
import spock.lang.Specification

import java.lang.annotation.RetentionPolicy

class AnnotationMetadataSpec extends Specification {

    void "test class values with string"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder("foo.Bar").values(AnnotationMetadataSpec, Specification))

        expect:
        metadata.classValues("foo.Bar") == [AnnotationMetadataSpec, Specification] as Class[]
    }

    void "test class values with type"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder(EachBean).values(AnnotationMetadataSpec, Specification))

        expect:
        metadata.classValues(EachBean) == [AnnotationMetadataSpec, Specification] as Class[]
    }

    void "test string values with type"() {
        given:
        AnnotationMetadata metadata = newMetadata(AnnotationValue.builder(TypeHint).values(UUID[], UUID))

        expect:
        metadata.stringValues(TypeHint).size() == 2
    }

    void "test empty values then append"() {
        given:
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata([:], null, null, [:], null, false)
        metadata.addAnnotation("foo.Bar", [:])

        when:
        metadata.addRepeatable("foo.Bar", new AnnotationValue("foo.Bar"), RetentionPolicy.RUNTIME)

        then:
        noExceptionThrown()
    }

    AnnotationMetadata newMetadata(AnnotationValueBuilder... builders) {

        def values = builders.collect({ it.build() })

        Map<String, Map<CharSequence, Object>> annotations = [:]
        for (AnnotationValue av in values) {
            annotations.put(av.annotationName, av.values)
        }

        return new MutableAnnotationMetadata(
                annotations, null, null, annotations, null, false
        )
    }
}
