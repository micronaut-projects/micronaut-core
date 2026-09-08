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
 * module overrides but third party and synthetic elements inherit. The default does not support mutation;
 * what matters is that saying so names the element at fault rather than the delegate.
 */
class MethodElementAnnotationMetadataDefaultSpec extends Specification {

    void "an element that cannot be annotated at all reports itself rather than the delegate"() {
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

    void "an element that supports Element.annotate but not the override still reports itself"() {
        given: "the shape that sent the original investigation at Core rather than at the element"
        def element = new MutableTestMethodElement()

        when:
        element.getMethodAnnotationMetadata().annotate("example.ByName")

        then: "the two surfaces are independent, and the message names the element that did not override"
        def e = thrown(UnsupportedOperationException)
        e.message.contains(MutableTestMethodElement.name)
        !e.message.contains('$1')

        and: "the surface it does implement is unaffected"
        element.annotate("example.ByName")
        element.annotationMetadata.hasDeclaredAnnotation("example.ByName")
    }

    void "removing through the default also names the element"() {
        given:
        def element = new MutableTestMethodElement()

        when:
        element.getMethodAnnotationMetadata().removeAnnotation("example.ByName")

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("does not support removing annotations at compilation time")
        e.message.contains(MutableTestMethodElement.name)
    }

    void "the read side is the element's annotation metadata"() {
        given:
        def element = new MutableTestMethodElement()
        element.annotate("example.ByName")

        expect:
        element.getMethodAnnotationMetadata().hasDeclaredAnnotation("example.ByName")
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
