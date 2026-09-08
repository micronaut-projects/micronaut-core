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

    void "an element whose own mutators route back through the delegate is rejected rather than left to recurse"() {
        given:
        def element = new CircularTestMethodElement()

        when:
        element.getMethodAnnotationMetadata().annotate("example.ByName")

        then:
        def e = thrown(UnsupportedOperationException)
        e.message.contains("route back through getMethodAnnotationMetadata()")
        e.message.contains(CircularTestMethodElement.name)
    }

    void "the delegate reads the annotation metadata back off whatever the mutation returned"() {
        given:
        def element = new CopyOnWriteTestMethodElement()

        when:
        def metadata = element.getMethodAnnotationMetadata().annotate("example.ByName")

        then: "the original is untouched and the returned metadata is the copy's"
        !element.annotationMetadata.hasDeclaredAnnotation("example.ByName")
        metadata.hasDeclaredAnnotation("example.ByName")
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

    /**
     * A method element that implements {@link Element#annotate} in terms of the very delegate that routes
     * back to it. Nonsensical, but it used to fail with an exception rather than a {@link StackOverflowError}.
     */
    private static class CircularTestMethodElement extends MutableTestMethodElement {

        @Override
        <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
            getMethodAnnotationMetadata().annotate(annotationType, consumer)
            this
        }
    }

    /**
     * A method element that answers a mutation with a new instance rather than mutating itself.
     */
    private static class CopyOnWriteTestMethodElement extends MutableTestMethodElement {

        @Override
        <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
            def copy = new CopyOnWriteTestMethodElement()
            AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType)
            consumer.accept(builder)
            copy.annotationMetadata.addDeclaredAnnotation(annotationType, builder.build().values)
            copy
        }
    }
}
