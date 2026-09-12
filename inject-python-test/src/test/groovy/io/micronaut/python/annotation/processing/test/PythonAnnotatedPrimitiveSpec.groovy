package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.PrimitiveElement

/**
 * Parity with javac and Groovy for a type annotation on a primitive: Python attaches an {@code Annotated[...]}
 * on a parameter or an attribute to that declaration, and a type use inside a type argument is boxed for the
 * generic, so the annotated primitive itself is only seen on its way to the boxed type argument.
 */
class PythonAnnotatedPrimitiveSpec extends AbstractPythonTypeElementSpec {

    void "test primitives"() {
        expect:
        buildClassElement('''
from typing import Annotated
from micronaut.python.compiler import TestAnnotation

class Primitives:
    counts: list[Annotated[int, TestAnnotation("e")]]

    def run(self, plain: int, annotated: Annotated[int, TestAnnotation("p")]) -> int:
        return plain
''') { ClassElement element ->
            def method = element.findMethod('run').get()
            def plain = method.getParameters().find { it.name == 'plain' }.getType()
            assert plain.is(PrimitiveElement.INT)
            assert plain.getTypeAnnotationMetadata().isEmpty()

            def annotated = method.getParameters().find { it.name == 'annotated' }
            assert annotated.hasAnnotation('io.micronaut.python.compiler.TestAnnotation')
            assert annotated.getType() == PrimitiveElement.INT

            def argument = element.getFields().find { it.name == 'counts' }.getGenericType().getFirstTypeArgument().get()
            assert argument.name == 'java.lang.Integer'
            assert argument.getTypeAnnotationMetadata().stringValue('io.micronaut.python.compiler.TestAnnotation').get() == 'e'
            return element
        }
    }
}
