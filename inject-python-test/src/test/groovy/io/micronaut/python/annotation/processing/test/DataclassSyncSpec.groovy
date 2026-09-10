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

/**
 * Generated dataclass stubs only write fields back to the Python object when the Java side changed them.
 */
class DataclassSyncSpec extends AbstractPythonTypeElementSpec {

    void "test unchanged immutable fields are not written back on asPolyglotValue"() {
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
        def introspection = getBeanIntrospection(context, "python.Person")
        def person = introspection.instantiate("John", 30)
        def value = person.asPolyglotValue()

        then:
        value.getMember("name").asString() == "John"
        value.getMember("age").asInt() == 30

        when: "the Python side changes an attribute and Java does not touch the field"
        value.putMember("name", "changed in Python")
        value.putMember("age", 31)

        then: "the change survives further bridge calls"
        person.asPolyglotValue().is(value)
        value.getMember("name").asString() == "changed in Python"
        value.getMember("age").asInt() == 31

        when: "Java changes one field"
        introspection.getRequiredProperty("name", String).set(person, "Jane")

        then: "the changed field is written and the other one keeps the Python value"
        person.asPolyglotValue().getMember("name").asString() == "Jane"
        person.asPolyglotValue().getMember("age").asInt() == 31

        when: "the Java field is written directly, bypassing the setter"
        person.age = 40

        then:
        person.asPolyglotValue().getMember("age").asInt() == 40

        cleanup:
        context?.close()
    }

    void "test a wrapper created from a Python value does not overwrite it"() {
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
        def introspection = getBeanIntrospection(context, "python.Person")
        def pythonPerson = introspection.instantiate("x", 1).asPolyglotValue().getMetaObject().execute("Ada", 36)
        def wrapper = introspection.getBeanType().fromPolyglotValue(pythonPerson)

        then:
        wrapper.name == "Ada"
        wrapper.age == 36

        when: "Python mutates the object and Java reads it again through the bridge"
        pythonPerson.putMember("age", 37)

        then:
        wrapper.asPolyglotValue().is(pythonPerson)
        pythonPerson.getMember("age").asInt() == 37

        when: "Java changes the same field"
        introspection.getRequiredProperty("age", int).set(wrapper, 38)

        then:
        wrapper.asPolyglotValue().getMember("age").asInt() == 38

        cleanup:
        context?.close()
    }

    void "test mutable fields are still written on every asPolyglotValue"() {
        given:
        def pythonCode = '''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Basket:
    owner: str
    items: list[str] = field(default_factory=list)
'''

        when:
        def context = buildContext(pythonCode)
        def introspection = getBeanIntrospection(context, "python.Basket")
        def basket = introspection.instantiate("Ada", new ArrayList<String>(["apple"]))

        then:
        basket.asPolyglotValue().getMember("items").getArraySize() == 1

        when: "the list is changed in place"
        basket.items.add("pear")

        then: "the in-place change is visible to Python"
        basket.asPolyglotValue().getMember("items").getArraySize() == 2
        basket.asPolyglotValue().getMember("items").getArrayElement(1).asString() == "pear"

        cleanup:
        context?.close()
    }
}
