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

import io.micronaut.validation.validator.Validator
import io.micronaut.validation.validator.DefaultValidatorConfiguration
import io.micronaut.core.beans.BeanIntrospector
import java.time.LocalDate
import jakarta.validation.valueextraction.Unwrapping

class DataclassFieldValidationSpec extends AbstractPythonTypeElementSpec {

    void "dataclass field metadata uses the existing Jakarta NotBlank validator"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field

@dataclass
class Contact:
    name: str = field(metadata={"validation": {"not_blank": True}})
''', true)
        def introspection = getBeanIntrospection(context, "python.Contact")
        def validator = context.getBean(Validator)

        expect:
        introspection.getRequiredProperty("name", String).hasAnnotation("jakarta.validation.constraints.NotBlank")
        introspection.constructorArguments*.name == ["name"]

        when:
        def contact = introspection.instantiate("  Alice  ")

        then:
        validator.validate(introspection, contact).isEmpty()
        contact.name == "  Alice  "
        contact.asPolyglotValue().getMember("name").asString() == "  Alice  "

        when:
        def violations = [null, "", " \t\n"].collect { value ->
            contact.name = value
            validator.validate(introspection, contact)
        }

        then:
        violations.every { it.size() == 1 }
        violations.every { it.first().propertyPath.toString() == "name" }
        violations.every { it.first().message == "must not be blank" }

        cleanup:
        context?.close()
    }

    void "dataclasses infer introspection while retaining defaults inheritance frozen fields and explicit options"() {
        given:
        def context = buildContext('''
import dataclasses as dc
from dataclasses import dataclass as data, field as f
from micronaut.core.annotation import Introspected as Inspect

@data
class Base:
    name: str

@dc.dataclass
class Child(Base):
    age: int = 25

@data(frozen=True)
class Frozen:
    name: str

@Inspect(excludes=["secret"])
@data
class Configured:
    visible: str
    secret: str

@data
class Defaults:
    optional: str | None = f(default=None)
    label: str = f(default="ready", metadata={"validation": {"not_blank": True}})
    tags: list[str] = f(default_factory=list)
''')
        def base = getBeanIntrospection(context, "python.Base")
        def child = getBeanIntrospection(context, "python.Child")
        def frozen = getBeanIntrospection(context, "python.Frozen")
        def configured = getBeanIntrospection(context, "python.Configured")
        def defaults = getBeanIntrospection(context, "python.Defaults")

        expect:
        base.propertyNames.toList() == ["name"]
        child.constructorArguments*.name == ["name", "age"]
        child.instantiate("Ada", 30).asPolyglotValue().getMember("age").asInt() == 30
        frozen.instantiate("Ada").asPolyglotValue().getMember("name").asString() == "Ada"
        configured.propertyNames.toList() == ["visible"]
        !base.annotationMetadata.hasStereotype("io.micronaut.serde.annotation.Serdeable")
        !base.annotationMetadata.hasAnnotation("io.micronaut.data.annotation.MappedEntity")

        when:
        def first = defaults.instantiate()
        def second = defaults.instantiate()
        first.tags.add("first")

        then:
        first.optional == null
        first.label == "ready"
        first.tags == ["first"]
        second.tags.isEmpty()
        first.asPolyglotValue().getMember("label").asString() == "ready"

        cleanup:
        context?.close()
    }

    void "constraint members are enforced by the Jakarta validators"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from datetime import date

@dataclass
class Options:
    title: str = field(metadata={"validation": {
        "size": {"min": 3, "max": 8, "message": "title size"},
        "pattern": {"regexp": "[a-z]+", "flags": ["CASE_INSENSITIVE"], "message": "letters"}
    }})
    count: int = field(metadata={"validation": {"min": 0, "max": 9}})
    price: str = field(metadata={"validation": {
        "decimal_min": {"value": "0.10", "inclusive": False},
        "decimal_max": "9.99", "digits": {"integer": 1, "fraction": 2}
    }})
    enabled: bool = field(metadata={"validation": {"assert_true": True}})
    disabled: bool = field(metadata={"validation": {"assert_false": True}})
    email: str = field(metadata={"validation": {"email": {"regexp": ".*@example[.]com", "flags": ["CASE_INSENSITIVE"]}}})
    before: date = field(metadata={"validation": {"past": True}})
    after: date = field(metadata={"validation": {"future": True}})
''', true)
        def introspection = getBeanIntrospection(context, "python.Options")
        def validator = context.getBean(Validator)
        def options = introspection.instantiate("Xray", 0, "0.11", true, false,
            "ALICE@EXAMPLE.COM", LocalDate.of(2000, 1, 1), LocalDate.of(2999, 1, 1))

        expect:
        validator.validate(introspection, options).isEmpty()
        introspection.getRequiredProperty("title", String).annotationMetadata.getAnnotationValuesByType(jakarta.validation.constraints.Pattern).size() == 1

        when:
        def patterns = ["123", "hello", "xx"].collect { title ->
            options.title = title
            validator.validate(introspection, options)*.message.toSet()
        }

        then:
        patterns == [["letters"] as Set, [] as Set, ["title size"] as Set]

        when: "an inclusive option is respected exactly"
        options.title = "Xray"
        options.price = "0.10"
        def boundary = validator.validate(introspection, options)

        then:
        boundary.size() == 1
        boundary.first().propertyPath.toString() == "price"

        when:
        options.price = "12.345"
        options.count = -1
        options.enabled = false
        options.disabled = true
        options.email = "invalid"
        options.before = LocalDate.of(2999, 1, 1)
        options.after = LocalDate.of(2000, 1, 1)
        def violations = validator.validate(introspection, options)

        then:
        violations*.propertyPath*.toString().toSet() == ["price", "count", "enabled", "disabled", "email", "before", "after"] as Set
        violations.count { it.propertyPath.toString() == "price" } == 2

        cleanup:
        context?.close()
    }

    void "constraint groups and payload use existing annotation class values"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from jakarta.validation.valueextraction import Unwrapping

@dataclass
class Grouped:
    name: str = field(metadata={"validation": {
        "not_blank": {"message": "selected group", "groups": ["java.io.Serializable"], "payload": [Unwrapping.Skip]}
    }})
''', true)
        def introspection = getBeanIntrospection(context, "python.Grouped")
        def validator = context.getBean(Validator)
        def grouped = introspection.instantiate("")
        def metadata = introspection.getRequiredProperty("name", String).annotationMetadata

        expect:
        metadata.classValues("jakarta.validation.constraints.NotBlank", "groups").toList() == [Serializable]
        metadata.classValues("jakarta.validation.constraints.NotBlank", "payload").toList() == [Unwrapping.Skip]
        validator.validate(introspection, grouped).isEmpty()
        validator.validate(introspection, grouped, Serializable)*.message == ["selected group"]

        cleanup:
        context?.close()
    }

    void "remaining built-in constraints and disabled constraints retain Jakarta behavior"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field
from datetime import date

@dataclass
class Remaining:
    required: str | None = field(metadata={"validation": {"not_null": True}})
    absent: str | None = field(metadata={"validation": {"null": True}})
    items: list[str] = field(metadata={"validation": {"not_empty": True}})
    positive: int = field(metadata={"validation": {"positive": True}})
    nonnegative: int = field(metadata={"validation": {"positive_or_zero": True}})
    negative: int = field(metadata={"validation": {"negative": True}})
    nonpositive: int = field(metadata={"validation": {"negative_or_zero": True}})
    before: date = field(metadata={"validation": {"past_or_present": True}})
    after: date = field(metadata={"validation": {"future_or_present": True}})
    unchecked: str = field(default=" ", metadata={"validation": {"not_blank": False}})
''', true)
        def introspection = getBeanIntrospection(context, "python.Remaining")
        def validator = context.getBean(Validator)
        def remaining = introspection.instantiate("ready", null, ["a"], 1, 0, -1, 0,
            LocalDate.now(), LocalDate.now(), " ")

        expect:
        validator.validate(introspection, remaining).isEmpty()
        !introspection.getRequiredProperty("unchecked", String).hasAnnotation("jakarta.validation.constraints.NotBlank")

        when:
        remaining.required = null
        remaining.absent = "present"
        remaining.items = []
        remaining.positive = 0
        remaining.nonnegative = -1
        remaining.negative = 0
        remaining.nonpositive = 1
        remaining.before = LocalDate.of(2999, 1, 1)
        remaining.after = LocalDate.of(2000, 1, 1)
        def violations = validator.validate(introspection, remaining)

        then:
        violations.size() == 9
        violations*.propertyPath*.toString().toSet() == ["required", "absent", "items", "positive", "nonnegative",
            "negative", "nonpositive", "before", "after"] as Set

        cleanup:
        context?.close()
    }

    void "Valid cascades through actual generated dataclass introspections"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass, field

@dataclass
class Address:
    street: str = field(metadata={"validation": {"not_blank": True}})

@dataclass
class Person:
    address: Address = field(metadata={"validation": {"valid": True}})
''')
        def addressIntrospection = getBeanIntrospection(context, "python.Address")
        def personIntrospection = getBeanIntrospection(context, "python.Person")
        def generated = [(addressIntrospection.beanType): addressIntrospection,
                         (personIntrospection.beanType): personIntrospection]
        def configuration = new DefaultValidatorConfiguration()
        // The in-memory classloader has no service index; the introspections themselves are generated.
        configuration.setBeanIntrospector(Stub(BeanIntrospector) {
            findIntrospection(_) >> { Class type -> Optional.ofNullable(generated[type]) }
        })
        def validator = configuration.validator
        def address = addressIntrospection.instantiate("Main Street")
        def person = personIntrospection.instantiate(address)

        expect:
        personIntrospection.getRequiredProperty("address", address.class).hasAnnotation("jakarta.validation.Valid")
        validator.validate(personIntrospection, person).isEmpty()

        when:
        address.street = " "
        def violations = validator.validate(personIntrospection, person)

        then:
        violations.size() == 1
        violations.first().propertyPath.toString() == "address.street"
        violations.first().message == "must not be blank"

        cleanup:
        context?.close()
    }
}
