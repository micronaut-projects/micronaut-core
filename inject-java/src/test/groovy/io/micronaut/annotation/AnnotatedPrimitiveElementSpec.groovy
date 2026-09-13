package io.micronaut.annotation

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.ast.PrimitiveElement
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import jakarta.inject.Singleton

class AnnotatedPrimitiveElementSpec extends AbstractTypeElementSpec {

    private static final String SOURCE = '''
package test;

import java.lang.annotation.*;

class Test {

    public @TypeAnn("f") int field;

    public int plain;

    public @TypeAnn("array") int[] array;

    public @TypeAnn("r") boolean method(@TypeAnn("p") long param) {
        return true;
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeAnn {
    String value() default "";
}
'''

    void "test a type annotation on a primitive is kept on an annotated copy of the shared constant"() {
        given:
        def element = buildClassElement(SOURCE)
        def field = element.getFields().find { it.name == 'field' }
        def type = field.getType()

        expect:
        type.isPrimitive()
        type.name == 'int'
        type instanceof PrimitiveElement
        type.getAnnotationMetadata().stringValue('test.TypeAnn').get() == 'f'
        type.getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'f'

        and: "the annotated copy still equals the shared constant"
        type == PrimitiveElement.INT
        type.hashCode() == PrimitiveElement.INT.hashCode()
        !type.is(PrimitiveElement.INT)
        PrimitiveElement.INT.getAnnotationMetadata().isEmpty()
    }

    void "test a primitive without a type annotation is the shared constant"() {
        given:
        def element = buildClassElement(SOURCE)
        def type = element.getFields().find { it.name == 'plain' }.getType()

        expect:
        type.is(PrimitiveElement.INT)
        type.getTypeAnnotationMetadata().isEmpty()
    }

    void "test an annotated use of a primitive can be annotated like any other type use"() {
        given:
        def element = buildClassElement(SOURCE)
        def field = element.getFields().find { it.name == 'field' }

        when:
        field.getType().annotate('test.Added')

        then: "the next element created for the same use sees it, the shared constant does not"
        field.getType().getTypeAnnotationMetadata().hasAnnotation('test.Added')
        field.getType().hasAnnotation('test.Added')
        !PrimitiveElement.INT.hasAnnotation('test.Added')

        when: "a primitive without type annotations has no element to annotate"
        element.getFields().find { it.name == 'plain' }.getType().annotate('test.Added')

        then:
        thrown(UnsupportedOperationException)
    }

    void "test return and parameter types and arrays of primitives"() {
        given:
        def element = buildClassElement(SOURCE)
        def method = element.getMethods().find { it.name == 'method' }
        def array = element.getFields().find { it.name == 'array' }.getType()

        expect:
        method.getReturnType().isPrimitive()
        method.getReturnType().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'r'
        method.getParameters()[0].getType().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'p'

        and: "the annotation on the component of an array of primitives is kept through the array"
        array.isArray()
        array.isPrimitive()
        array.fromArray().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'array'
    }

    void "test every mutation of an annotated primitive reaches the type annotations of the use"() {
        given:
        def element = buildClassElement(SOURCE)
        def type = { element.getFields().find { it.name == 'field' }.getType() }

        when: "annotating by name, by type, with a builder and with a value"
        type().annotate('test.ByName')
        type().annotate(Singleton)
        type().annotate(Deprecated) { it.member('since', '1') }
        type().annotate('test.WithBuilder') { it.value('v') }
        type().annotate(AnnotationValue.builder('test.AsValue').build())

        then:
        type().hasAnnotation('test.ByName')
        type().hasAnnotation(Singleton)
        type().stringValue(Deprecated, 'since').get() == '1'
        type().stringValue('test.WithBuilder').get() == 'v'
        type().hasAnnotation('test.AsValue')
        type().hasStereotype('jakarta.inject.Scope')

        when: "removing by name, by type, by predicate and the stereotype"
        type().removeAnnotation('test.ByName')
        type().removeAnnotation(Deprecated)
        type().removeAnnotationIf { AnnotationValue<?> av -> av.annotationName == 'test.AsValue' }
        type().removeStereotype('jakarta.inject.Scope')
        type().removeStereotype(Singleton)

        then:
        !type().hasAnnotation('test.ByName')
        !type().hasAnnotation(Deprecated)
        !type().hasAnnotation('test.AsValue')
        type().hasAnnotation('test.WithBuilder')
        type().getTypeAnnotationMetadata().toString().contains('int')

        and: "the written annotation is still there and the constant untouched"
        type().stringValue('test.TypeAnn').get() == 'f'
        PrimitiveElement.INT.getAnnotationMetadata().isEmpty()
    }

    void "test a factory that does not build type annotations from a cache entry says so"() {
        given:
        def factory = [:] as ElementAnnotationMetadataFactory

        when:
        factory.buildTypeAnnotations(null, 'int')

        then:
        thrown(UnsupportedOperationException)
    }
}
