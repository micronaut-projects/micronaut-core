package io.micronaut.inject.ast

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.annotation.AnnotationValueBuilder
import io.micronaut.inject.annotation.MutableAnnotationMetadata
import spock.lang.Specification

import java.lang.annotation.Annotation
import java.util.function.Consumer

/**
 * Covers the default {@link MethodElement#getMethodAnnotationMetadata()}, which every shipped language
 * module overrides but third party and synthetic elements inherit.
 */
class MethodElementAnnotationMetadataDefaultSpec extends Specification {

    void "the default method annotation metadata writes through to an element that supports annotating"() {
        given:
        def element = new MutableTestMethodElement()

        when:
        element.getMethodAnnotationMetadata().annotate("example.ByName") { AnnotationValueBuilder builder ->
            builder.member("name", "foo")
        }
        element.getMethodAnnotationMetadata().annotate(AnnotationValue.builder("example.ByValue").build())

        then:
        element.annotationMetadata.hasDeclaredAnnotation("example.ByName")
        element.annotationMetadata.stringValue("example.ByName", "name").get() == "foo"
        element.annotationMetadata.hasDeclaredAnnotation("example.ByValue")
    }

    void "an element that cannot be annotated reports itself rather than the delegate"() {
        given:
        def element = MethodElement.of(
            ClassElement.of("example.Bean"),
            AnnotationMetadata.EMPTY_METADATA,
            PrimitiveElement.VOID,
            PrimitiveElement.VOID,
            "test"
        )

        when:
        element.getMethodAnnotationMetadata().annotate("example.ByName")

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("does not support adding annotations at compilation time")
        e.message.contains(element.getClass().name)
    }

    /**
     * A method element that opts in to mutation through {@link Element#annotate} only, without overriding
     * {@link MethodElement#getMethodAnnotationMetadata()}.
     */
    private static class MutableTestMethodElement implements MethodElement {

        final MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata()

        @Override
        String getName() {
            "test"
        }

        @Override
        boolean isProtected() {
            false
        }

        @Override
        boolean isPublic() {
            true
        }

        @Override
        Object getNativeType() {
            this
        }

        @Override
        ClassElement getDeclaringType() {
            ClassElement.of("example.Bean")
        }

        @Override
        ClassElement getReturnType() {
            PrimitiveElement.VOID
        }

        @Override
        ParameterElement[] getParameters() {
            ParameterElement.ZERO_PARAMETER_ELEMENTS
        }

        @Override
        MethodElement withParameters(ParameterElement... newParameters) {
            this
        }

        @Override
        AnnotationMetadata getAnnotationMetadata() {
            annotationMetadata
        }

        @Override
        <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType)
            consumer.accept(builder)
            annotationMetadata.addDeclaredAnnotation(annotationType, builder.build().values)
            this
        }

        @Override
        <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
            annotationMetadata.addDeclaredAnnotation(annotationValue.annotationName, annotationValue.values)
            this
        }
    }
}
