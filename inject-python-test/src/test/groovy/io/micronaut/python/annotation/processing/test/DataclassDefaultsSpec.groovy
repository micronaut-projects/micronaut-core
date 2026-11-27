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

import io.micronaut.core.reflect.InstantiationUtils

/**
 * A Python class whose attributes all have defaults gets a Java no-arg constructor that applies them.
 */
class DataclassDefaultsSpec extends AbstractPythonTypeElementSpec {

    void "test an introspected dataclass with only defaulted fields has a no-arg constructor"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Customer:
    firstName: str = "unknown"
    lastName: str | None = None

@Introspected
@dataclass
class Data:
    customers: dict[str, Customer] = field(default_factory=dict)
    count: int = 5
    label: str = "root"
    enabled: bool = True
'''

        when:
        def context = buildContext(pythonCode)
        def dataClass = context.classLoader.loadClass("python.Data")
        def introspection = getBeanIntrospection(context, "python.Data")

        then: "the generated class has a public no-arg constructor"
        dataClass.getConstructor() != null

        when: "the class is instantiated without arguments from Java"
        def data = InstantiationUtils.instantiate(dataClass)

        then: "the Python defaults are visible from Java"
        data.count == 5
        data.label == "root"
        data.enabled
        data.customers != null
        data.customers.isEmpty()

        when: "the introspection instantiates it with no arguments"
        def introspected = introspection.instantiate()

        then:
        introspected.count == 5
        introspected.label == "root"
        introspected.customers.isEmpty()
        introspection.getRequiredProperty("count", int).get(introspected) == 5

        when: "the object is handed back to Python"
        data.count = 7
        def value = data.asPolyglotValue()

        then: "the Python object carries the defaults and the Java change"
        value.getMember("count").asInt() == 7
        value.getMember("label").asString() == "root"
        value.getMember("customers").hasHashEntries()

        when: "a dataclass whose defaults are all literals is instantiated the same way"
        def customer = InstantiationUtils.instantiate(context.classLoader.loadClass("python.Customer"))

        then:
        customer.firstName == "unknown"
        customer.lastName == null

        cleanup:
        context?.close()
    }

    void "test a plain dataclass with only defaulted fields has a no-arg constructor"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field

@dataclass
class Data:
    customers: dict[str, str] = field(default_factory=dict)
    count: int = 5
'''

        when:
        def context = buildContext(pythonCode)
        def dataClass = context.classLoader.loadClass("python.Data")
        def data = InstantiationUtils.instantiate(dataClass)

        then: "the Python defaults apply"
        data.getCount() == 5
        data.getCustomers().isEmpty()
        data.asPolyglotValue().getMember("count").asInt() == 5

        when: "the all-arguments constructor is still available"
        def explicit = dataClass.getConstructor(Map, int).newInstance([a: "b"], 9)

        then:
        explicit.getCount() == 9
        explicit.getCustomers() == [a: "b"]

        cleanup:
        context?.close()
    }

    void "test a dataclass with a required field is introspected through its constructor"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Person:
    name: str
    age: int = 25
'''

        when:
        def context = buildContext(pythonCode)
        def personClass = context.classLoader.loadClass("python.Person")

        then: "the introspection constructs the class through its arguments; the public no-arg constructor of the generated class only serves frameworks that populate its fields reflectively (JPA)"
        def introspection = getBeanIntrospection(context, "python.Person")
        introspection.constructorArguments*.name == ["name", "age"]
        introspection.instantiate("John", 30).age == 30
        personClass.constructors.find { it.parameterCount == 0 } != null

        cleanup:
        context?.close()
    }

    void "test a dataclass constructed with null defaulted arguments gets the defaults of its __init__ and requires the others"() {
        given:
        def pythonCode = '''
import dataclasses
import enum
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected


class ReadingStatus(enum.Enum):
    WANT_TO_READ = "WANT_TO_READ"
    READING = "READING"


@Introspected
@dataclass
class Book:
    name: str
    count: int
    note: str | None
    status: ReadingStatus = ReadingStatus.WANT_TO_READ
    label: str = "default-label"
    tags: list[str] = field(default_factory=list)
    initialized: bool | None = None

    def __post_init__(self):
        self.initialized = True

    def describe(self) -> str:
        return f"{self.name}:{self.status.name}:{self.label}:{list(self.tags)}:{self.initialized}:{dataclasses.is_dataclass(self)}"
'''

        when:
        def context = buildContext(pythonCode)
        def bookClass = context.classLoader.loadClass("python.Book")
        def introspection = getBeanIntrospection(context, "python.Book")
        def book = introspection.instantiate("x", 1, null, null, null, null, null)

        then: "the defaults of the dataclass apply, whatever their kind, and __post_init__ runs"
        book.status.name() == "WANT_TO_READ"
        book.label == "default-label"
        book.tags == []
        book.initialized
        book.describe() == "x:WANT_TO_READ:default-label:[]:True:True"

        when: "a defaulted argument before one that has a value is null"
        def explicit = bookClass.constructors.find { it.parameterCount == 7 }
            .newInstance("y", 2, "n", null, "mine", null, null)

        then: "it gets its default too, rather than None"
        explicit.status.name() == "WANT_TO_READ"
        explicit.label == "mine"
        explicit.describe() == "y:WANT_TO_READ:mine:[]:True:True"

        and: "a deserializer has to supply the arguments without a default that cannot be None"
        def required = introspection.constructorArguments.collectEntries {
            [it.name, it.annotationMetadata.booleanValue("io.micronaut.serde.config.annotation.SerdeConfig", "required").orElse(false)]
        }
        required == [name: true, count: true, note: false, status: false, label: false, tags: false, initialized: false]

        cleanup:
        context?.close()
    }

    void "test an introspected class with class-level defaults applies them in the no-arg constructor"() {
        given:
        def pythonCode = '''
from micronaut.core.annotation import Introspected

@Introspected
class Settings:
    customers: dict[str, str] = {}
    count: int = 5
    label: str = "root"
'''

        when:
        def context = buildContext(pythonCode)
        def settingsClass = context.classLoader.loadClass("python.Settings")
        def settings = InstantiationUtils.instantiate(settingsClass)

        then: "the Python defaults are applied"
        settings.count == 5
        settings.label == "root"
        settings.customers != null

        when: "the Java object is handed to Python"
        settings.count = 6
        def value = settings.asPolyglotValue()

        then:
        value.getMember("count").asInt() == 6
        value.getMember("label").asString() == "root"

        when:
        def introspected = getBeanIntrospection(context, "python.Settings").instantiate()

        then:
        introspected.count == 5

        cleanup:
        context?.close()
    }
}
