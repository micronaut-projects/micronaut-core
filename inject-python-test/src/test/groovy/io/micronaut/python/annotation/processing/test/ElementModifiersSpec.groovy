/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.python.annotation.processing.test

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.MethodElement

/**
 * The modifiers and the primary constructor the Python element model reports to type element visitors:
 * Python has no access modifiers, so every element is public unless its name starts with an underscore,
 * and a class without {@code __init__} has an implicit no-arg constructor like a Java class without a
 * declared constructor.
 */
class ElementModifiersSpec extends AbstractPythonTypeElementSpec {

    void "test class without __init__ has an implicit public primary constructor"() {
        expect:
        buildClassElement('''
class PlainService:

    def run(self) -> str:
        return "run"
''', "PlainService") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert primaryConstructor instanceof ConstructorElement
            assert primaryConstructor.parameters.length == 0
            assert primaryConstructor.isPublic()
            assert !primaryConstructor.isPrivate()
            assert primaryConstructor.modifiers == [ElementModifier.PUBLIC] as Set
            assert primaryConstructor.declaringType.name == "python.PlainService"
            assert primaryConstructor.owningType.name == "python.PlainService"

            MethodElement defaultConstructor = element.getDefaultConstructor().orElse(null)
            assert defaultConstructor != null
            assert defaultConstructor.parameters.length == 0

            // the implicit constructor is not a declared member, like the implicit constructor of a Java class
            assert element.getEnclosedElements(ElementQuery.CONSTRUCTORS).isEmpty()
            assert !element.isInterface()
            return element
        }
    }

    void "test declared __init__ is the primary constructor"() {
        expect:
        buildClassElement('''
class Dependency:
    pass

class ConstructedService:

    def __init__(self, dependency: Dependency):
        self.dependency = dependency
''', "ConstructedService") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert primaryConstructor instanceof ConstructorElement
            assert primaryConstructor.parameters*.name == ["dependency"]
            assert primaryConstructor.parameters*.type*.name == ["python.Dependency"]
            assert primaryConstructor.modifiers == [ElementModifier.PUBLIC] as Set
            assert primaryConstructor.declaringType.name == "python.ConstructedService"
            assert element.getEnclosedElements(ElementQuery.CONSTRUCTORS).size() == 1
            assert element.getDefaultConstructor().isEmpty()
            return element
        }
    }

    void "test dataclass constructor is the primary constructor"() {
        expect:
        buildClassElement('''
from dataclasses import dataclass

@dataclass
class Point:
    x: int
    y: int = 0
''', "Point") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert primaryConstructor instanceof ConstructorElement
            assert primaryConstructor.parameters*.name == ["x", "y"]
            assert primaryConstructor.modifiers == [ElementModifier.PUBLIC] as Set
            assert element.getDefaultConstructor().isEmpty()
            return element
        }
    }

    void "test inherited __init__ is the primary constructor of a subclass without __init__"() {
        expect:
        buildClassElement('''
class Parent:

    def __init__(self, name: str):
        self.name = name

class Child(Parent):

    def child_method(self) -> str:
        return self.name
''', "Child") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert primaryConstructor instanceof ConstructorElement
            assert primaryConstructor.parameters*.name == ["name"]
            assert primaryConstructor.declaringType.name == "python.Parent"
            assert primaryConstructor.owningType.name == "python.Child"
            assert element.getDefaultConstructor().isEmpty()
            assert element.getEnclosedElements(ElementQuery.CONSTRUCTORS).isEmpty()
            return element
        }
    }

    void "test inherited no-arg __init__ is also the default constructor"() {
        expect:
        buildClassElement('''
class Parent:

    def __init__(self):
        self.name = "parent"

class Child(Parent):
    pass
''', "Child") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert primaryConstructor.parameters.length == 0
            assert primaryConstructor.declaringType.name == "python.Parent"
            assert element.getDefaultConstructor().isPresent()
            assert element.getDefaultConstructor().get().parameters.length == 0
            return element
        }
    }

    void "test static creator wins over the implicit constructor"() {
        expect:
        buildClassElement('''
from micronaut.core.annotation import Creator

class Created:

    @staticmethod
    @Creator
    def create(name: str) -> "Created":
        return Created()
''', "Created") { ClassElement element ->
            MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null)
            assert primaryConstructor != null
            assert !(primaryConstructor instanceof ConstructorElement)
            assert primaryConstructor.name == "create"
            assert primaryConstructor.isStatic()
            assert primaryConstructor.modifiers == [ElementModifier.PUBLIC, ElementModifier.STATIC] as Set
            return element
        }
    }

    void "test class, method and field modifiers"() {
        expect:
        buildClassElement('''
class ModifierService:
    label: str = "label"

    def run(self) -> str:
        return "run"

    def _internal(self) -> str:
        return "internal"

    @staticmethod
    def create() -> "ModifierService":
        return ModifierService()

    @classmethod
    def named(cls, name: str) -> "ModifierService":
        return ModifierService()
''', "ModifierService") { ClassElement element ->
            assert element.isPublic()
            assert !element.isAbstract()
            assert !element.isStatic()
            assert element.modifiers == [ElementModifier.PUBLIC] as Set

            Map<String, MethodElement> methods = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                .collectEntries { [(it.name): it] }
            assert methods.keySet() == ["run", "_internal", "create", "named"] as Set

            assert methods.run.isPublic()
            assert !methods.run.isStatic()
            assert methods.run.modifiers == [ElementModifier.PUBLIC] as Set

            // the compiler treats underscore names as non-public and does not bridge them
            assert !methods._internal.isPublic()
            assert methods._internal.isPrivate()
            assert methods._internal.modifiers == [ElementModifier.PRIVATE] as Set

            assert methods.create.isPublic()
            assert methods.create.isStatic()
            assert methods.create.modifiers == [ElementModifier.PUBLIC, ElementModifier.STATIC] as Set
            assert methods.named.modifiers == [ElementModifier.PUBLIC, ElementModifier.STATIC] as Set

            def fields = element.getFields().collectEntries { [(it.name): it] }
            assert fields.keySet() == ["label"] as Set
            assert fields.label.isPublic()
            assert !fields.label.isStatic()
            assert !fields.label.isFinal()
            assert fields.label.modifiers == [ElementModifier.PUBLIC] as Set

            // the public modifier filter used by BeanElementBuilder.produceBeans and ElementQuery.onlyAccessible
            def publicMethods = element.getEnclosedElements(
                ElementQuery.ALL_METHODS.onlyDeclared().modifiers { it.contains(ElementModifier.PUBLIC) }
            )
            assert publicMethods*.name as Set == ["run", "create", "named"] as Set
            return element
        }
    }

    void "test abstract class and method modifiers"() {
        expect:
        buildClassElement('''
from abc import ABC, abstractmethod

class Shape(ABC):

    @abstractmethod
    def area(self) -> float:
        pass

    def describe(self) -> str:
        return "shape"
''', "Shape") { ClassElement element ->
            assert element.isAbstract()
            assert element.modifiers == [ElementModifier.PUBLIC, ElementModifier.ABSTRACT] as Set

            Map<String, MethodElement> methods = element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                .collectEntries { [(it.name): it] }
            assert methods.area.isAbstract()
            assert methods.area.modifiers == [ElementModifier.PUBLIC, ElementModifier.ABSTRACT] as Set
            assert methods.describe.modifiers == [ElementModifier.PUBLIC] as Set
            return element
        }
    }

    void "test enum constant modifiers"() {
        expect:
        buildClassElement('''
from enum import Enum

class Color(Enum):
    RED = "red"
    GREEN = "green"
''', "Color") { ClassElement element ->
            assert element.isEnum()
            assert element.modifiers == [ElementModifier.PUBLIC] as Set
            def constants = ((EnumElement) element).elements()
            assert constants*.name == ["RED", "GREEN"]
            assert constants.every { it.modifiers == [ElementModifier.PUBLIC, ElementModifier.STATIC, ElementModifier.FINAL] as Set }
            return element
        }
    }

    void "test property accessor modifiers"() {
        expect:
        buildClassElement('''
class Account:

    def __init__(self):
        self._balance = 0

    @property
    def balance(self) -> int:
        return self._balance

    @balance.setter
    def balance(self, value: int) -> None:
        self._balance = value
''', "Account") { ClassElement element ->
            def property = element.getBeanProperties().find { it.name == "balance" }
            assert property != null
            assert property.readMethod.get().modifiers == [ElementModifier.PUBLIC] as Set
            assert property.writeMethod.get().modifiers == [ElementModifier.PUBLIC] as Set
            return element
        }
    }
}
