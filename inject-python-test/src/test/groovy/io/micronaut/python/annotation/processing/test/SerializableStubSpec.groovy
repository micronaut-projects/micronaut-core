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
