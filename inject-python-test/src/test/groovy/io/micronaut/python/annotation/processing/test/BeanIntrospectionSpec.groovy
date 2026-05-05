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
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.SerdeableDataClass")

        then:
        introspection != null
        introspection.hasStereotype(Serdeable.Serializable)
        introspection.hasStereotype(Serdeable.Deserializable)
        introspection.getBeanType().getSimpleName() == "SerdeableDataClass"
        introspection.getPropertyNames().length == 2
        introspection.getProperty("name").isPresent()
        introspection.getProperty("age").isPresent()

        when:"instantiating with constructor arguments"
        def instance = introspection.instantiate("John", 30)

        then:"instance is created correctly"
        instance != null
        introspection.getRequiredProperty("name", String).get(instance) == "John"
        introspection.getRequiredProperty("age", int).get(instance) == 30

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
