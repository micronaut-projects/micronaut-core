/*
 * Copyright 2017-2025 original authors
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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.python.compiler.Serdeable

/**
 * Tests for Python bean introspection.
 *
 * @author Micronaut
 * @since 4.8.0
 */
class BeanIntrospectionSpec extends AbstractPythonTypeElementSpec {

    void "test nullable annotated generated id resolves to boxed Integer"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import MappedEntity
from micronaut.data.annotation import Id
from micronaut.data.annotation import GeneratedValue

@dataclass
@MappedEntity
class MyPerson:
    id : Annotated[int | None, Id, GeneratedValue]
    name : str
    age : int
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.MyPerson")
        def idProperty = introspection.getRequiredProperty("id", Integer)

        then:
        introspection != null
        idProperty.type == Integer
        idProperty.hasAnnotation("io.micronaut.data.annotation.Id")
        idProperty.hasAnnotation("io.micronaut.data.annotation.GeneratedValue")

        cleanup:
        context?.close()
    }

    void "test @Introspected on Python @dataclass"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected
from dataclasses import dataclass

@Introspected
@dataclass
class TestDataClass:
    name: str
    age: int = 25

'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.TestDataClass")

        then:
        introspection != null
        introspection.getBeanType().getSimpleName() == "TestDataClass"
        introspection.getPropertyNames().length == 2
        introspection.getProperty("name").isPresent()
        introspection.getProperty("age").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate("John", 30)

        then:"instance is created correctly"
        instance != null
        introspection.getRequiredProperty("name", String).get(instance) == "John"
        introspection.getRequiredProperty("age", int).get(instance) == 30
        instance.asPolyglotValue().getMember("name").asString() == "John"
        instance.asPolyglotValue().getMember("age").asInt() == 30
        cleanup:
        context?.close()
    }

    void "test dataclass boolean getter overrides dynamic superclass boolean getter"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

class UserState:
    enabled: bool

@Introspected
@dataclass
class User(UserState):
    name: str
    enabled: bool
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.User")
        def instance = introspection.instantiate("Sherlock", true)

        then:
        introspection.getRequiredProperty("enabled", boolean).get(instance)
        instance.isEnabled()

        cleanup:
        context?.close()
    }

    void "test dataclass subclass syncs introspected fields into polyglot value"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import MappedEntity
from micronaut.data.annotation import Id
from micronaut.data.annotation import GeneratedValue

class UserState:
    username: str
    password: str
    enabled: bool

@dataclass
@MappedEntity
class User(UserState):
    username: str
    password: str
    enabled: bool
    id: Annotated[int | None, Id, GeneratedValue] = None
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.User")
        def instance = introspection.instantiate("sherlock", "secret", true, null)
        introspection.getRequiredProperty("id", Integer).set(instance, 42)
        def value = instance.asPolyglotValue()

        then:
        value.getMember("username").asString() == "sherlock"
        value.getMember("password").asString() == "secret"
        value.getMember("enabled").asBoolean()
        value.getMember("id").asInt() == 42

        cleanup:
        context?.close()
    }

    void "test Python enum dataclass field is restored as Python enum in polyglot value"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from enum import Enum

from micronaut.core.annotation import Introspected

class Player(Enum):
    WHITE = "w"
    BLACK = "b"

@Introspected
@dataclass
class GameStateDTO:
    player: Player
'''

        when:
        def context = buildContext(pythonCode)
        def playerType = context.classLoader.loadClass("python.Player")
        def white = Enum.valueOf(playerType, "WHITE")
        def introspection = getBeanIntrospection(context, "python.GameStateDTO")
        def instance = introspection.instantiate(white)
        def playerValue = instance.asPolyglotValue().getMember("player")

        then:
        !playerValue.isHostObject()
        playerValue.getMember("name").asString() == "WHITE"
        playerValue.getMember("value").asString() == "w"

        cleanup:
        context?.close()
    }

    void "test Python dataclass default factory fields accept null default and explicit list through introspection"() {
        given:
        def pythonCode = '''
from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING, Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

if TYPE_CHECKING:
    from .phone_entity import PhoneEntity

@dataclass
@MappedEntity("phone")
class PhoneEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    phone: str

@dataclass
@MappedEntity("contact")
class ContactEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    firstName: str
    lastName: str
    phones: Annotated[
        list[PhoneEntity],
        Relation(value=Relation.Kind.ONE_TO_MANY, mappedBy="contact"),
    ] = field(default_factory=list)
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.ContactEntity")
        def phoneIntrospection = getBeanIntrospection(context, "python.PhoneEntity")
        def defaultedInstance = introspection.instantiate(null, "Sergio", "del Amo", null)
        def defaultedPhones = introspection.getRequiredProperty("phones", List).get(defaultedInstance)
        def phone = phoneIntrospection.instantiate(null, "555-0100")
        def explicitInstance = introspection.instantiate(null, "Sergio", "del Amo", [phone])
        def explicitPhones = introspection.getRequiredProperty("phones", List).get(explicitInstance)
        def explicitPhonesValue = explicitInstance.asPolyglotValue().getMember("phones")

        then:
        introspection.constructorArguments*.name == ["id", "firstName", "lastName", "phones"]
        defaultedPhones == []
        defaultedInstance.asPolyglotValue().getMember("phones").hasArrayElements()
        defaultedInstance.asPolyglotValue().getMember("phones").arraySize == 0
        explicitPhones.size() == 1
        explicitPhonesValue.hasArrayElements()
        explicitPhonesValue.arraySize == 1
        explicitPhonesValue.getArrayElement(0).getMember("phone").asString() == "555-0100"

        cleanup:
        context?.close()
    }

    void "test bidirectional Python dataclass asPolyglotValue does not recursively reconstruct associations"() {
        given:
        def pythonCode = '''
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, Relation

@dataclass
@MappedEntity("contact")
class ContactEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    firstName: str
    lastName: str
    phones: Annotated[
        list[PhoneEntity],
        Relation(value=Relation.Kind.ONE_TO_MANY, mappedBy="contact"),
    ] = field(default_factory=list)

@dataclass
@MappedEntity("phone")
class PhoneEntity:
    id: Annotated[int | None, Id, GeneratedValue]
    phone: str
    contact: Annotated[ContactEntity, Relation(value=Relation.Kind.MANY_TO_ONE)]
'''

        when:
        def context = buildContext(pythonCode)
        def contactIntrospection = getBeanIntrospection(context, "python.ContactEntity")
        def phoneIntrospection = getBeanIntrospection(context, "python.PhoneEntity")
        def contact = contactIntrospection.instantiate(null, "Sergio", "del Amo", null)
        def phone = phoneIntrospection.instantiate(null, "555-0100", contact)
        contactIntrospection.getRequiredProperty("phones", List).set(contact, [phone])
        def contactValue = contact.asPolyglotValue()
        def phonesValue = contactValue.getMember("phones")
        def phoneValue = phonesValue.getArrayElement(0)

        then:
        contactValue.getMember("firstName").asString() == "Sergio"
        phonesValue.hasArrayElements()
        phonesValue.arraySize == 1
        phoneValue.getMember("phone").asString() == "555-0100"
        phoneValue.getMember("contact").getMember("firstName").asString() == "Sergio"

        cleanup:
        context?.close()
    }

    void "test @Introspected excludes merge generated proxy properties"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected
from dataclasses import dataclass

@Introspected(excludes=["ignored"])
@dataclass
class TestDataClass:
    name: str
    ignored: str

'''

        when:
        def excludes = buildClassElement(pythonCode, "TestDataClass") { classElement ->
            classElement.getAnnotationMetadata().stringValues(Introspected, "excludes") as Set
        }
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.TestDataClass")

        then:
        introspection != null
        introspection.getProperty("name").isPresent()
        introspection.getProperty("ignored").isEmpty()
        introspection.getProperty("memberKeys").isEmpty()
        excludes == ["ignored", "memberKeys"] as Set

        cleanup:
        context?.close()
    }

    void "test @Introspected on regular Python class with attributes no constructor"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected

@Introspected
class TestClass:
    name: str
    value: int
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.TestClass")

        then:
        introspection != null
        introspection.getBeanType().getSimpleName() == "TestClass"
        introspection.getPropertyNames().length == 2
        introspection.getProperty("name").isPresent()
        introspection.getProperty("value").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate()
        introspection.getRequiredProperty("name", String).set(instance, "Test")
        introspection.getRequiredProperty("value", int).set(instance, 100)

        then:"instance is created correctly"
        instance != null
        introspection.getRequiredProperty("name", String).get(instance) == "Test"
        introspection.getRequiredProperty("value", int).get(instance) == 100
        instance.asPolyglotValue().getMember("name").asString() == "Test"
        instance.asPolyglotValue().getMember("value").asInt() == 100

        cleanup:
        context?.close()
    }

    void "test @Introspected on regular Python class with attributes"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected

@Introspected
class TestClass:
    name: str
    value: int

    def __init__(self, name: str, value: int = 42):
        self.name = name
        self.value = value
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.TestClass")

        then:
        introspection != null
        introspection.getBeanType().getSimpleName() == "TestClass"
        introspection.getPropertyNames().length == 2
        introspection.getProperty("name").isPresent()
        introspection.getProperty("value").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate("Test", 100)

        then:"instance is created correctly"
        instance != null
        introspection.getRequiredProperty("name", String).get(instance) == "Test"
        introspection.getRequiredProperty("value", int).get(instance) == 100

        cleanup:
        context?.close()
    }

    void "test introspection includes inherited Python attributes"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected

class Parent:
    parent_name: str

@Introspected
class Child(Parent):
    name: str
    count: int
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Child")

        then:
        introspection != null
        introspection.getPropertyNames() as Set == ["parent_name", "name", "count"] as Set
        introspection.getProperty("parent_name").isPresent()
        introspection.getProperty("name").isPresent()
        introspection.getProperty("count").isPresent()

        cleanup:
        context?.close()
    }

    void "test @Introspected on Python class with @property decorator"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected

@Introspected
class TestPropertyClass:
    def __init__(self, first_name: str, last_name: str):
        self._first_name = first_name
        self._last_name = last_name

    @property
    def full_name(self) -> str:
        return f"{self._first_name} {self._last_name}"

    @full_name.setter
    def full_name(self, value: str):
        parts = value.split(" ", 1)
        self._first_name = parts[0]
        self._last_name = parts[1] if len(parts) > 1 else ""
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.TestPropertyClass")

        then:
        introspection != null
        introspection.getBeanType().getSimpleName() == "TestPropertyClass"
        introspection.getPropertyNames().length >= 1  // At least the full_name property
        introspection.getProperty("full_name").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate("John", "Doe")

        then:"instance is created correctly"
        instance != null
        introspection.getProperty("full_name").get().get(instance) == 'John Doe'
        instance.full_name() == 'John Doe'
        // Note: Property access will depend on whether getters/setters are properly generated
        // This test verifies the basic structure is in place

        cleanup:
        context?.close()
    }

    void "test generic placeholders for bean properties"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected
from typing import Generic, TypeVar

T = TypeVar("T", bound=str)

@Introspected
class Test(Generic[T]):
    property: T
    values: list[T]
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Test")
        def property = introspection.getRequiredProperty("property", String)
        def propertyArgument = property.asArgument()
        def values = introspection.getRequiredProperty("values", List)
        def valueArgument = values.asArgument().getFirstTypeVariable().orElse(null)

        then:
        propertyArgument instanceof GenericPlaceholder
        propertyArgument.variableName == "T"
        propertyArgument.name == "property"
        propertyArgument.type == String

        valueArgument instanceof GenericPlaceholder
        valueArgument.name == "E"
        valueArgument.variableName == "T"
        valueArgument.type == String

        cleanup:
        context?.close()
    }

    void "test @Serdeable on Python @dataclass"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable

@Serdeable
@dataclass
class SerdeableDataClass:
    name: str
    age: int = 25
    active: bool = False
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.SerdeableDataClass")

        then:
        introspection != null
        introspection.hasStereotype(Serdeable.Serializable)
        introspection.hasStereotype(Serdeable.Deserializable)
        introspection.getBeanType().getSimpleName() == "SerdeableDataClass"
        introspection.getPropertyNames().length == 3
        introspection.getProperty("name").isPresent()
        introspection.getProperty("age").isPresent()
        introspection.getProperty("active").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate("John", 30, true)

        then:"instance is created correctly"
        instance != null
        introspection.getRequiredProperty("name", String).get(instance) == "John"
        introspection.getRequiredProperty("age", int).get(instance) == 30
        introspection.getRequiredProperty("active", boolean).get(instance)
        instance.class.methods.any { it.name == "isActive" && it.parameterCount == 0 && it.returnType == boolean }
        instance.isActive()

        cleanup:
        context?.close()
    }

    void "test @Serdeable on Python @dataclass with object property"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable

@Serdeable
@dataclass
class SerdeableDataClass:
    name: str
    data: object | None = None
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.SerdeableDataClass")

        then:
        introspection != null
        introspection.hasStereotype(Serdeable.Serializable)
        introspection.hasStereotype(Serdeable.Deserializable)
        introspection.getRequiredProperty("data", Object).type == Object

        cleanup:
        context?.close()
    }

    void "test binary @Serdeable on Python @dataclass"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.serde.annotation import Serdeable

@Serdeable
@dataclass
class BinarySerdeableDataClass:
    name: str
    age: int = 25
    active: bool = False
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.BinarySerdeableDataClass")

        then:
        introspection != null
        introspection.hasStereotype(Introspected)
        introspection.hasStereotype("io.micronaut.serde.annotation.Serdeable\$Serializable")
        introspection.hasStereotype("io.micronaut.serde.annotation.Serdeable\$Deserializable")
        introspection.getPropertyNames().length == 3
        introspection.getProperty("name").isPresent()
        introspection.getProperty("age").isPresent()
        introspection.getProperty("active").isPresent()

        when:
        def instance = introspection.instantiate("John", 30, true)

        then:
        introspection.getRequiredProperty("active", boolean).get(instance)
        instance.class.methods.any { it.name == "isActive" && it.parameterCount == 0 && it.returnType == boolean }
        instance.isActive()

        cleanup:
        context?.close()
    }

    void "test binary @Serdeable on Python @dataclass mapped entity"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import NotBlank
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity
from micronaut.serde.annotation import Serdeable

@dataclass
@Serdeable
@MappedEntity
class Genre:
    name: Annotated[str, NotBlank]
    id: Annotated[int | None, Id, GeneratedValue] = None
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Genre")

        then:
        introspection != null
        introspection.hasStereotype(Introspected)
        introspection.hasStereotype("io.micronaut.serde.annotation.Serdeable\$Serializable")
        introspection.hasStereotype("io.micronaut.serde.annotation.Serdeable\$Deserializable")
        introspection.getPropertyNames().length == 2
        introspection.getProperty("name").isPresent()
        introspection.getProperty("id").isPresent()

        when:
        def genre = introspection.instantiate("DevOps", 1)

        then:
        genre.getMemberKeys() as Set == ["name", "id"] as Set

        cleanup:
        context?.close()
    }

    void "equals/hashCode/toString for @Introspected Python dataclass"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected
from dataclasses import dataclass
from typing import List

@Introspected
@dataclass
class Address:
    street: str
    zip: int

@Introspected
@dataclass
class Person:
    name: str
    age: int
    tags: List[str]
    scores: List[int]
    address: Address
    nums: list[int]
'''
        when:
        def context = buildContext(pythonCode)
        def personIntrospection = getBeanIntrospection(context, "python.Person")
        def addressIntrospection = getBeanIntrospection(context, "python.Address")

        then:
        personIntrospection != null
        addressIntrospection != null

        when:
        def addr1 = addressIntrospection.instantiate("Main", 94105)
        def addr2 = addressIntrospection.instantiate("Main", 94105)
        def p1 = personIntrospection.instantiate("Sally", 30, ["a","b"], [1,2], addr1, [1,2,3])
        def p2 = personIntrospection.instantiate("Sally", 30, ["a","b"], [1,2], addr2, [1,2,3])

        then:
        p1.equals(p2)
        p1.hashCode() == p2.hashCode()
        p1.toString().contains("Person[")
        p1.toString().contains("name=Sally")
        p1.toString().contains("nums=")

        when:
        // change an array component to ensure deep equality is enforced
        def p3 = personIntrospection.instantiate("Sally", 30, ["a","b"], [1,2], addr2, [1,2,4])
        then:
        !p1.equals(p3)

        cleanup:
        context?.close()
    }
}
