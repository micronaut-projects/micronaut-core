package io.micronaut.annotation.processing

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.ast.ClassElement

import javax.lang.model.element.VariableElement

class TypeMirrorKeySpec extends AbstractTypeElementSpec {

    void "type mirror keys compare structurally equal array types by identity"() {
        expect:
        buildClassElement('''
package test;
class Test {
    String[] first;
    String[] second;
}
''') { ClassElement element ->
            def first = mirror(element, 'first')
            def second = mirror(element, 'second')
            assert first.equals(second)
            assert !first.is(second)

            def key = new JavaAnnotationMetadataBuilder.TypeMirrorKey(first)
            assert key.equals(new JavaAnnotationMetadataBuilder.TypeMirrorKey(first))
            assert key.hashCode() == new JavaAnnotationMetadataBuilder.TypeMirrorKey(first).hashCode()
            assert !key.equals(new JavaAnnotationMetadataBuilder.TypeMirrorKey(second))
            assert !key.equals(first)
            element
        }
    }

    private static mirror(ClassElement element, String name) {
        ((VariableElement) element.fields.find { it.name == name }.nativeType.element()).asType()
    }
}
