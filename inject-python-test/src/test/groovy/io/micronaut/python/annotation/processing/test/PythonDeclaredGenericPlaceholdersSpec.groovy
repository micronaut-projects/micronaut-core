package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement

/**
 * Parity with javac, Groovy and KSP: the declared generic placeholders of a parameterized use are the
 * variables the type declares, not the arguments of the use.
 */
class PythonDeclaredGenericPlaceholdersSpec extends AbstractPythonTypeElementSpec {

    void "test the declared generic placeholders of a parameterized use are the declared variables"() {
        expect:
        buildClassElement('''
from typing import Generic, TypeVar

T = TypeVar('T')

class Holder(Generic[T]):
    items: list[str]
    mapping: dict[str, int]
''') { ClassElement element ->
            assert element.getDeclaredGenericPlaceholders()*.variableName == ['T']
            def items = element.getFields().find { it.name == 'items' }.getType()
            assert items.getBoundGenericTypes()*.name == ['java.lang.String']
            assert items.getDeclaredGenericPlaceholders()*.variableName == ['E']
            def mapping = element.getFields().find { it.name == 'mapping' }.getType()
            assert mapping.getDeclaredGenericPlaceholders()*.variableName == ['K', 'V']
            return element
        }
    }
}
