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

import java.lang.reflect.Field
import java.lang.reflect.Modifier

/**
 * The generated class of an introspected Python class declares the fields of its properties only: the
 * bridge state (the Python object, the sync snapshots and flags) is declared by a generated superclass.
 * Tools that take the declared instance fields of a class for its properties, such as LangChain4j when it
 * builds the JSON schema or the format instructions of a structured output, do not honour
 * {@code transient}, and would otherwise ask a model for the bridge state.
 */
class DataclassDeclaredFieldsSpec extends AbstractPythonTypeElementSpec {

    void "a Serdeable dataclass declares the fields of its properties only"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from micronaut.python.compiler import Serdeable

@Serdeable
@dataclass
class ValidationResult:
    valid: bool
    reason: str
    sanitized_interests: str | None
''')
        Class<?> type = context.classLoader.loadClass("python.ValidationResult")

        expect: "the fields LangChain4j turns into schema properties are the dataclass fields"
        schemaFieldNames(type) == ["valid", "reason", "sanitized_interests"]

        and: "the bridge state is declared, transient, by the generated superclass"
        type.superclass.name == 'python.ValidationResult$$GraalPyInternalState'
        Modifier.isAbstract(type.superclass.modifiers)
        Modifier.isPublic(type.superclass.modifiers)
        instanceFields(type.superclass)*.name.containsAll([
            "graalpyInternalValue",
            "graalpyInternalValueSyncing",
            "graalpyInternalSynced_valid",
            "graalpyInternalSynced_reason",
            "graalpyInternalSynced_sanitized_interests"
        ])
        instanceFields(type.superclass).every { Modifier.isTransient(it.modifiers) }

        when: "the class is used from Java and bridged to Python"
        def introspection = getBeanIntrospection(context, "python.ValidationResult")
        def result = introspection.instantiate([true, "fine", "java"] as Object[])
        def value = result.asPolyglotValue()

        then:
        value.getMember("reason").asString() == "fine"
        value.getMember("sanitized_interests").asString() == "java"

        when: "a field changes on the Java side"
        result.reason = "changed"

        then: "the snapshot in the superclass still tracks the sync"
        result.asPolyglotValue().is(value)
        value.getMember("reason").asString() == "changed"

        cleanup:
        context?.close()
    }

    void "a dataclass handing collections and nested objects to Python by reference keeps the ownership flag in the superclass"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Address:
    street: str

@Introspected
@dataclass
class Person:
    name: str
    address: Address
    tags: list[str] = field(default_factory=list)
''')
        Class<?> type = context.classLoader.loadClass("python.Person")

        expect:
        schemaFieldNames(type) == ["name", "address", "tags"]
        schemaFieldNames(context.classLoader.loadClass("python.Address")) == ["street"]
        instanceFields(type.superclass)*.name.containsAll(["graalpyInternalValue", "graalpyInternalJavaOwned"])

        when: "a Java-owned object hands the list to Python by reference (the Java list itself)"
        def introspection = getBeanIntrospection(context, "python.Person")
        def address = getBeanIntrospection(context, "python.Address").instantiate("Main Street")
        def person = introspection.instantiate("Ada", address, ["a"])
        person.asPolyglotValue().getMember("tags").invokeMember("add", "b")

        then:
        person.tags == ["a", "b"]

        cleanup:
        context?.close()
    }

    void "the state superclass of a nested dataclass is named after its binary name and is no conversion target"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

class Outer:
    @Introspected
    @dataclass
    class Inner:
        name: str

@Introspected
@dataclass
class Outer_Inner:
    size: int
''')
        Class<?> inner = context.classLoader.loadClass('python.Outer$Inner')
        Class<?> lookalike = context.classLoader.loadClass("python.Outer_Inner")

        expect: "a top-level class whose name joins the nested names does not collide with it"
        inner.superclass.name == 'python.Outer$Inner$$GraalPyInternalState'
        lookalike.superclass.name == 'python.Outer_Inner$$GraalPyInternalState'
        schemaFieldNames(inner) == ["name"]
        schemaFieldNames(lookalike) == ["size"]

        and: "the state superclass is not among the types a Python value of the class converts to"
        def mapping = context.classLoader.loadClass("python.Outer_InnerTargetTypeMapping").getDeclaredConstructor().newInstance()
        !mapping.assignableTargetTypes().any { it.name.contains('GraalPyInternalState') }

        cleanup:
        context?.close()
    }

    void "a dataclass extending a dataclass keeps working"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from micronaut.core.annotation import Introspected

@Introspected
@dataclass
class Base:
    name: str

@Introspected
@dataclass
class Derived(Base):
    size: int
''')
        Class<?> base = context.classLoader.loadClass("python.Base")
        Class<?> derived = context.classLoader.loadClass("python.Derived")

        expect: "the base declares its properties only, the subclass extends the base"
        schemaFieldNames(base) == ["name"]
        derived.superclass == base

        and: "a value of the subclass converts to the base, not to the state superclass of the base"
        def targetTypes = context.classLoader.loadClass("python.DerivedTargetTypeMapping").getDeclaredConstructor().newInstance().assignableTargetTypes()
        base in targetTypes
        !targetTypes.any { it.name.contains('GraalPyInternalState') }

        when:
        def instance = getBeanIntrospection(context, "python.Derived").instantiate("box", 3)

        then:
        instance.asPolyglotValue().getMember("name").asString() == "box"
        instance.asPolyglotValue().getMember("size").asInt() == 3

        cleanup:
        context?.close()
    }

    /**
     * The fields LangChain4j 1.x ({@code JsonSchemaElementUtils}, {@code PojoOutputParser}) takes for the
     * properties of a structured output: the declared fields of the class that are not static.
     */
    private static List<String> schemaFieldNames(Class<?> type) {
        instanceFields(type)*.name
    }

    private static List<Field> instanceFields(Class<?> type) {
        type.declaredFields.findAll { !Modifier.isStatic(it.modifiers) && it.name != '__$hits$__' && !it.name.startsWith('this$') } as List<Field>
    }
}
