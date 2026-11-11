package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.GenericPlaceholderElement

class BoundedPlaceholderElementSpec extends AbstractPythonTypeElementSpec {

    void "a bounded method placeholder exposes its bound while retaining its variable identity"() {
        expect:
        buildClassElement('''
from micronaut.core.annotation import Introspected

class Parent:
    pass

@Introspected
class Entity(Parent):
    pass

class Repository:
    def save_all[S: Entity](self, entities: list[S]) -> list[S]:
        return entities
''', 'Repository') { ClassElement element ->
            def method = element.findMethod('save_all').get()
            assert method.declaredTypeVariables.size() == 1
            def variable = method.declaredTypeVariables[0]
            def argument = method.genericReturnType.typeArguments['E']
            assert argument instanceof GenericPlaceholderElement
            assert variable.variableName == 'S'
            assert argument.variableName == 'S'
            assert variable.declaringElement.get() == method
            assert variable.bounds*.name == ['python.Entity']
            assert argument.name == 'python.Entity'
            assert argument.hasAnnotation('io.micronaut.core.annotation.Introspected')
            def parameterArgument = method.parameters[0].genericType.typeArguments['E']
            assert parameterArgument.name == 'python.Entity'
            assert parameterArgument.hasAnnotation('io.micronaut.core.annotation.Introspected')
            verifyAll(variable) {
                name == 'python.Entity'
                simpleName == 'Entity'
                hasAnnotation('io.micronaut.core.annotation.Introspected')
                isAssignable('python.Entity')
                isAssignable('python.Parent')
                isAssignable(Object)
            }
            true
        }
    }

    void "an annotated bounded class placeholder retains metadata through copies and arrays"() {
        expect:
        buildClassElement('''
from typing import Annotated
from jakarta.validation.constraints import NotNull
from micronaut.core.annotation import Introspected

@Introspected
class Entity:
    pass

class Repository[S: Entity]:
    def save_all(self, entities: list[Annotated[S, NotNull()]]) -> list[S]:
        return entities
''', 'Repository') { ClassElement element ->
            def method = element.findMethod('save_all').get()
            def variable = method.parameters[0].genericType.typeArguments['E']
            assert variable instanceof GenericPlaceholderElement
            assert variable.variableName == 'S'
            assert variable.declaringElement.get() == element
            assert variable.typeAnnotationMetadata.hasAnnotation('jakarta.validation.constraints.NotNull$List')
            assert variable.hasAnnotation('io.micronaut.core.annotation.Introspected')
            def copy = variable.withAnnotationMetadata(variable.annotationMetadata)
            def array = copy.toArray()
            assert array instanceof GenericPlaceholderElement
            assert array.arrayDimensions == 1
            assert array.variableName == 'S'
            assert array.name == 'python.Entity'
            assert array.hasAnnotation('io.micronaut.core.annotation.Introspected')
            assert array.typeAnnotationMetadata.hasAnnotation('jakarta.validation.constraints.NotNull$List')
            assert array.fromArray().arrayDimensions == 0
            assert array.fromArray().variableName == 'S'
            assert method.genericReturnType.typeArguments['E'].typeAnnotationMetadata.empty
            true
        }
    }

    void "a placeholder exposes the class and package of #bound while retaining its variable identity"() {
        expect:
        buildClassElement("""
from java.lang import Number

class Repository[${declaration}]:
    def identity(self, value: S) -> S:
        return value
""", 'Repository') { ClassElement element ->
            def variable = element.declaredGenericPlaceholders[0]
            assert variable.variableName == 'S'
            assert variable.name == bound.name
            assert variable.packageName == bound.packageName
            assert variable.bounds*.name == [bound.name]
            assert variable.isAssignable(Object)
            assert !variable.isAssignable(String)
            true
        }

        where:
        declaration | bound
        'S'         | Object
        'S: Number' | Number
    }
}
