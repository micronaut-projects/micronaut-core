package io.micronaut.ast.groovy.visitor

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.ast.PrimitiveElement

class AnnotatedPrimitiveElementSpec extends AbstractBeanDefinitionSpec {

    private static final String SOURCE = '''
package test

import java.lang.annotation.*

class Test {

    public @TypeAnn("f") int field

    public int plain

    public @TypeAnn("r") boolean method(@TypeAnn("p") long param) {
        return true
    }
}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE_USE)
@interface TypeAnn {
    String value() default ""
}
'''

    void "test a type annotation on a primitive is kept on an annotated copy of the shared constant"() {
        given:
        def element = buildClassElement('test.Test', SOURCE)
        def type = element.getFields().find { it.name == 'field' }.getType()

        expect:
        type.isPrimitive()
        type.name == 'int'
        type instanceof PrimitiveElement
        type.getAnnotationMetadata().stringValue('test.TypeAnn').get() == 'f'
        type.getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'f'

        and: "the annotated copy still equals the shared constant"
        type == PrimitiveElement.INT
        type.hashCode() == PrimitiveElement.INT.hashCode()
        PrimitiveElement.INT.getAnnotationMetadata().isEmpty()
    }

    void "test a primitive without a type annotation is the shared constant"() {
        given:
        def element = buildClassElement('test.Test', SOURCE)
        def type = element.getFields().find { it.name == 'plain' }.getType()

        expect:
        type.is(PrimitiveElement.INT)
        type.getTypeAnnotationMetadata().isEmpty()
    }

    void "test an annotated use of a primitive can be annotated like any other type use"() {
        given:
        def element = buildClassElement('test.Test', SOURCE)
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

    void "test return and parameter types"() {
        given:
        def element = buildClassElement('test.Test', SOURCE)
        def method = element.getMethods().find { it.name == 'method' }

        expect:
        method.getReturnType().isPrimitive()
        method.getReturnType().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'r'
        method.getParameters()[0].getType().getTypeAnnotationMetadata().stringValue('test.TypeAnn').get() == 'p'
    }
}
