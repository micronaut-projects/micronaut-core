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
package io.micronaut.python.annotation.processing.test.visitorintegration

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * Visitors that inspect a class through its fields (Micronaut Data's JSON view validation, Micronaut
 * Serialization's mixins and JAXB field access) see the declared attributes of a Python class as fields.
 */
class PythonFieldQuerySpec extends AbstractPythonTypeElementSpec {

    void "test declared attributes are found by field queries"() {
        when:
        def fields = buildClassElement('''
from dataclasses import dataclass
from typing import Annotated

from jakarta.validation.constraints import NotBlank
from micronaut.core.annotation import Introspected


class Base:
    created: str = "today"


@Introspected
@dataclass
class Contact(Base):
    id: int
    name: Annotated[str, NotBlank] = "unknown"
    VERSION: int = 1
''', "Contact") { ClassElement element ->
            [
                declared: element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared()).collect { FieldElement f -> f.name },
                all: element.getEnclosedElements(ElementQuery.ALL_FIELDS).collect { FieldElement f -> f.name },
                instance: element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyAccessible()).collect { FieldElement f -> f.name },
                id: element.findField("id").map { FieldElement f -> f.type.name }.orElse(null),
                name: element.findField("name").map { FieldElement f -> f.hasStereotype("jakarta.validation.Constraint") }.orElse(null),
                created: element.findField("created").map { FieldElement f -> f.declaringType.name }.orElse(null),
                constrained: element.getEnclosedElement(ElementQuery.ALL_FIELDS.annotated { it.hasStereotype("jakarta.validation.Constraint") }).map { it.name }.orElse(null)
            ]
        }

        then:
        fields.declared == ["id", "name", "VERSION"]
        fields.all.toSet() == ["id", "name", "VERSION", "created"].toSet()
        fields.instance.toSet() == ["id", "name", "VERSION", "created"].toSet()
        fields.id == "int"
        fields.name == true
        fields.created == "python.Base"
        fields.constrained == "name"
    }

    void "test a JSON view can reference the properties of a Python entity"() {
        given:
        def context = buildContext('''
from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, JsonView, MappedEntity


@MappedEntity
@dataclass
class Contact:
    id: Annotated[int | None, Id, GeneratedValue] = None
    name: str | None = None
    age: int | None = None


@JsonView(entity=Contact)
@dataclass
class ContactView:
    id: Annotated[int | None, Id] = None
    name: str | None = None
''')

        when:
        def introspection = getBeanIntrospection(context, "python.ContactView")

        then:
        introspection != null
        introspection.getAnnotation("io.micronaut.data.annotation.JsonView").annotationClassValue("entity").get().name == "python.Contact"
        introspection.getProperty("name").present

        cleanup:
        context?.close()
    }
}
