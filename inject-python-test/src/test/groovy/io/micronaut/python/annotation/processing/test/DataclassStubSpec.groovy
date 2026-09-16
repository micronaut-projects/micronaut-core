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

import io.micronaut.inject.ast.EnumElement
import io.micronaut.python.annotation.processing.test.dataclass.VersionedObject

/**
 * The stub of a dataclass takes the fields of its dataclass bases, in the order of the generated {@code __init__},
 * its attributes can implement the accessors of a Java interface, and generic and optional enum attributes keep
 * their types.
 */
class DataclassStubSpec extends AbstractPythonTypeElementSpec {

    void "test a dataclass inherits the constructor fields of its dataclass base"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Animal:
    name: str

@Introspected
@dataclass
class Cat(Animal):
    lives: int = 9

@Introspected
@dataclass
class Kitten(Cat):
    pass
'''

        when:
        def context = buildContext(pythonCode)
        def animalType = context.classLoader.loadClass("python.Animal")
        def catIntrospection = getBeanIntrospection(context, "python.Cat")
        def kittenIntrospection = getBeanIntrospection(context, "python.Kitten")

        then: "the base fields lead the constructor and the properties, as in the Python __init__"
        catIntrospection.constructorArguments*.name == ["name", "lives"]
        catIntrospection.propertyNames == ["name", "lives"] as String[]
        kittenIntrospection.constructorArguments*.name == ["name", "lives"]
        kittenIntrospection.propertyNames == ["name", "lives"] as String[]

        when:
        def cat = catIntrospection.instantiate("Tom", 3)
        def kitten = kittenIntrospection.instantiate("Kit", 1)

        then:
        animalType.isInstance(cat)
        cat.name == "Tom"
        cat.lives == 3
        cat.getName() == "Tom"
        kitten.name == "Kit"
        kitten.lives == 1

        when: "the Python object is created from the Java fields"
        def pythonCat = cat.asPolyglotValue()

        then:
        pythonCat.getMember("name").asString() == "Tom"
        pythonCat.getMember("lives").asInt() == 3
        pythonCat.getMetaObject().getMetaSimpleName() == "Cat"

        when: "a Python instance is wrapped"
        def pythonInstance = pythonCat.getMetaObject().execute("Felix", 7)
        def wrapper = catIntrospection.getBeanType().fromPolyglotValue(pythonInstance)

        then:
        wrapper.name == "Felix"
        wrapper.lives == 7

        cleanup:
        context?.close()
    }

    void "test a field declared again by the subclass keeps the position of the base"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Base:
    a: int
    b: int

@Introspected
@dataclass
class Sub(Base):
    a: int
    c: int
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Sub")

        then:
        introspection.constructorArguments*.name == ["a", "b", "c"]
        introspection.propertyNames == ["a", "b", "c"] as String[]

        when:
        def sub = introspection.instantiate(1, 2, 3)

        then:
        sub.a == 1
        sub.b == 2
        sub.c == 3
        sub.asPolyglotValue().getMember("b").asInt() == 2

        when: "the class element reports the declaration of the subclass"
        def declaringTypes = buildClassElement(pythonCode, "Sub") { classElement ->
            classElement.beanProperties.collectEntries { [(it.name): it.declaringType.simpleName] }
        }

        then:
        declaringTypes == [a: "Sub", b: "Base", c: "Sub"]

        cleanup:
        context?.close()
    }

    void "test an introspected dataclass extending a plain dataclass"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@dataclass
class Animal:
    name: str

@Introspected
@dataclass
class Cat(Animal):
    lives: int = 9
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Cat")

        then:
        introspection.constructorArguments*.name == ["name", "lives"]

        when:
        def cat = introspection.instantiate("Tom", 3)

        then:
        cat.name == "Tom"
        cat.asPolyglotValue().getMember("name").asString() == "Tom"
        cat.asPolyglotValue().getMember("lives").asInt() == 3

        cleanup:
        context?.close()
    }

    void "test a dataclass implements a Java interface through attributes named after its getters"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected
from micronaut.python.annotation.processing.test.dataclass import VersionedObject

@Introspected
@dataclass
class CustomObject(VersionedObject):
    apiVersion: str
    kind: str
    enabled: bool = True
    labels: list[str] = field(default_factory=list)
    value: str | None = None
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.CustomObject")
        VersionedObject object = introspection.instantiate("custom.test.io/v1", "CustomObject", false, ["a", "b"], "value")

        then: "the generated accessors implement the interface"
        object.apiVersion == "custom.test.io/v1"
        object.kind == "CustomObject"
        !object.enabled
        object.labels == ["a", "b"]
        object.describe() == "CustomObject/custom.test.io/v1"

        cleanup:
        context?.close()
    }

    void "test a generic dataclass keeps the type variable of its attributes"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field
from typing import Generic, TypeVar
from micronaut.core.annotation import Introspected

T = TypeVar("T")

@Introspected
@dataclass
class Response(Generic[T]):
    result: T
    items: list[T] = field(default_factory=list)
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Response")
        def response = introspection.instantiate("ok", ["a", "b"])

        then:
        introspection.beanType.typeParameters*.name == ["T"]
        introspection.beanType.getField("result").genericType.typeName == "T"
        introspection.beanType.getField("items").genericType.typeName == "java.util.List<T>"
        response.result == "ok"
        response.items == ["a", "b"]
        response.asPolyglotValue().getMember("result").asString() == "ok"

        cleanup:
        context?.close()
    }

    void "test an optional enum attribute is an enum element"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from enum import Enum
from micronaut.core.annotation import Introspected

class PetType(Enum):
    DOG = "DOG"
    CAT = "CAT"

@Introspected
@dataclass
class Pet:
    type: PetType | None
    kind: PetType
'''

        when:
        def propertyTypes = buildClassElement(pythonCode, "Pet") { classElement ->
            classElement.beanProperties.collectEntries { [(it.name): it.genericType] }
        }

        then: "the nullable wrapper of the enum is still an EnumElement, as visitors expect"
        propertyTypes.type instanceof EnumElement
        propertyTypes.type.isEnum()
        propertyTypes.type.values() == ["DOG", "CAT"]
        propertyTypes.type.isNullable()
        propertyTypes.kind instanceof EnumElement
        !propertyTypes.kind.isNullable()
    }
}
