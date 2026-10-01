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
import io.micronaut.context.python.ValueCoercible
import org.graalvm.polyglot.Context

/**
 * The Java class generated for an introspected Python class is {@link Serializable}: the property
 * fields are written, the Python object is rebuilt from them after deserialization.
 */
class SerializableStubSpec extends AbstractPythonTypeElementSpec {

    void "introspected Python objects survive Java serialization"() {
        given:
        String py = '''
from dataclasses import dataclass, field
from enum import Enum

from micronaut.core.annotation import Introspected


class Role(Enum):
    ADMIN = "admin"
    USER = "user"


@Introspected
@dataclass
class Address:
    city: str


@Introspected
@dataclass
class Person:
    name: str
    age: int
    role: Role
    address: Address
    tags: list[str] = field(default_factory=list)

    def greeting(self) -> str:
        return "Hello " + self.name + " from " + self.address.city


'''
        ApplicationContext ctx = buildContext(py, true)
        Context polyglot = ctx.getBean(Context)
        Class<?> personClass = ctx.classLoader.loadClass('python.Person')
        Class<?> addressClass = ctx.classLoader.loadClass('python.Address')
        Class<?> roleClass = ctx.classLoader.loadClass('python.Role')
        def person = polyglot.eval("python", "Person('Fred', 42, Role.ADMIN, Address('Paris'), ['a', 'b'])").as(personClass)
        Map<String, Object> store = new HashMap<>()
        store.put('person', person)

        when:
        Map<String, Object> restored = roundTrip(store, ctx.classLoader)
        def copy = restored.get('person')

        then:
        Serializable.isAssignableFrom(personClass)
        Serializable.isAssignableFrom(addressClass)
        personClass.isInstance(copy)
        !copy.is(person)
        copy.name == 'Fred'
        copy.age == 42
        copy.role == Enum.valueOf((Class<Enum>) roleClass, 'ADMIN')
        addressClass.isInstance(copy.address)
        copy.address.city == 'Paris'
        copy.tags == ['a', 'b']

        when: "the Python object is rebuilt from the restored fields"
        def pythonCopy = ((ValueCoercible) copy).asPolyglotValue()

        then:
        pythonCopy.getMember('name').asString() == 'Fred'
        pythonCopy.getMember('age').asInt() == 42
        pythonCopy.getMember('role').getMember('value').asString() == 'admin'
        pythonCopy.getMember('address').getMember('city').asString() == 'Paris'
        pythonCopy.getMember('tags').getArraySize() == 2
        pythonCopy.invokeMember('greeting').asString() == 'Hello Fred from Paris'
        !pythonCopy.equals(((ValueCoercible) person).asPolyglotValue())

        cleanup:
        ctx?.close()
    }

    void "list and dict attributes are written as plain collections and come back as the collections of the restored fields"() {
        given:
        String py = '''
from dataclasses import dataclass, field

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Matrix:
    rows: list[list[int]] = field(default_factory=list)
    labels: dict[str, list[str]] = field(default_factory=dict)

    def describe(self) -> str:
        return type(self.rows).__name__ + ":" + type(self.rows[0]).__name__ + ":" + str(self.rows) + ":" + str(self.labels)


'''
        ApplicationContext ctx = buildContext(py, true)
        Context polyglot = ctx.getBean(Context)
        Class<?> matrixClass = ctx.classLoader.loadClass('python.Matrix')
        def matrix = polyglot.eval("python", "Matrix([[1, 2], [3]], {'a': ['x']})").as(matrixClass)

        when:
        def copy = roundTrip(['matrix': matrix], ctx.classLoader).get('matrix')

        then: "the restored fields hold plain Java collections"
        copy.rows == [[1, 2], [3]]
        copy.rows.getClass() == ArrayList
        copy.rows[0].getClass() == ArrayList
        copy.labels == [a: ['x']]

        when: "the Python object is rebuilt"
        String description = copy.describe()

        then: "a deserialized object is owned by its Java fields, so Python sees their collections by reference"
        description.endsWith(':[[1, 2], [3]]:{\'a\': [\'x\']}')
        !description.startsWith('list:')
        copy.rows[0].add(9)
        copy.describe().endsWith(':[[1, 2, 9], [3]]:{\'a\': [\'x\']}')

        cleanup:
        ctx?.close()
    }

    void "a Python class is serializable when its bases are"() {
        given:
        String py = '''
from dataclasses import dataclass
from java.io import Serializable

from micronaut.core.annotation import Introspected


@Introspected
@dataclass
class Base:
    name: str


@Introspected
@dataclass
class Derived(Base):
    size: int


@Introspected
class Computed:
    def __init__(self, name: str):
        self._name = name

    @property
    def name(self) -> str:
        return self._name.upper()


@Introspected
class DerivedFromComputed(Computed):
    def __init__(self, name: str, size: int):
        super().__init__(name)
        self.size = size


@Introspected
@dataclass
class Declared(Serializable):
    name: str


'''
        ApplicationContext ctx = buildContext(py, true)

        expect: "a dataclass extending an introspected dataclass"
        Serializable.isAssignableFrom(ctx.classLoader.loadClass('python.Derived'))

        and: "not when a Python base has a custom property accessor, whose state cannot be rebuilt"
        !Serializable.isAssignableFrom(ctx.classLoader.loadClass('python.Computed'))
        !Serializable.isAssignableFrom(ctx.classLoader.loadClass('python.DerivedFromComputed'))

        and: "a class listing Serializable among its bases declares it once"
        ctx.classLoader.loadClass('python.Declared').interfaces.count { it == Serializable } == 1

        when:
        def derivedIntrospection = getBeanIntrospection(ctx, 'python.Derived')
        def derived = derivedIntrospection.instantiate(derivedIntrospection.constructorArguments.collect { it.type == String ? 'd' : 3 } as Object[])
        derived.name = 'd'
        def copy = roundTrip(['derived': derived], ctx.classLoader).get('derived')

        then:
        copy.name == 'd'
        copy.size == 3
        ((ValueCoercible) copy).asPolyglotValue().getMember('name').asString() == 'd'
        ((ValueCoercible) copy).asPolyglotValue().getMember('size').asInt() == 3

        cleanup:
        ctx?.close()
    }

    void "the generated class of a Python class without introspected properties is not serializable"() {
        given:
        String py = '''
class Connection:
    def __init__(self):
        self.open = True


'''
        ApplicationContext ctx = buildContext(py, true)

        expect:
        !Serializable.isAssignableFrom(ctx.classLoader.loadClass('python.Connection'))

        cleanup:
        ctx?.close()
    }

    private static Map<String, Object> roundTrip(Map<String, Object> store, ClassLoader classLoader) {
        def bytes = new ByteArrayOutputStream()
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(store) }
        def input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                try {
                    return Class.forName(desc.getName(), false, classLoader)
                } catch (ClassNotFoundException e) {
                    return super.resolveClass(desc)
                }
            }
        }
        input.withCloseable { (Map<String, Object>) it.readObject() }
    }
}
