package io.micronaut.inject.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.inject.ast.Element

import java.lang.annotation.RetentionPolicy

class AnnotationMetadataBuilderSpec extends AbstractTypeElementSpec {

    void "test build annotation by name"() {
        given:
        def builder = newJavaAnnotationBuilder()

        when:
        def annotationValue = builder.buildAnnotation('jakarta.inject.Singleton')

        then:
        annotationValue.present
        annotationValue.get().annotationName == 'jakarta.inject.Singleton'
        annotationValue.get().retentionPolicy == RetentionPolicy.RUNTIME
        annotationValue.get().stereotypes != null
    }

    void "test build annotation returns empty for unknown annotation"() {
        given:
        def builder = newJavaAnnotationBuilder()

        expect:
        builder.buildAnnotation('test.DoesNotExist').empty
    }

    void "test processing exception exposes originating element"() {
        given:
        Element element = Stub(Element)
        def exception = new ProcessingException(element, 'test')

        expect:
        exception.element.is(element)
    }
}
