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

import io.micronaut.inject.ast.ClassElement
import spock.lang.PendingFeature

class PropertyElementSpec extends AbstractPythonTypeElementSpec {

    void "test simple bean properties"() {
        expect:
        buildClassElement('''
class Book:
    title: str
    pages: int

    @property
    def display_name(self) -> str:
        return self.title

    @display_name.setter
    def display_name(self, value: str):
        self.title = value
''') { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }

            assert properties.keySet().containsAll(["title", "pages", "display_name"])
            assert properties["title"].type.name == String.name
            assert properties["title"].field.isEmpty()
            assert properties["title"].readMethod.isPresent()
            assert properties["title"].writeMethod.isPresent()
            assert properties["title"].readType.get().name == String.name
            assert properties["title"].writeType.get().name == String.name
            assert !properties["title"].isReadOnly()

            assert properties["pages"].type.name == "int"
            assert properties["pages"].field.isEmpty()
            assert !properties["pages"].isReadOnly()

            assert properties["display_name"].type.name == String.name
            assert properties["display_name"].field.isEmpty()
            assert properties["display_name"].readMethod.isPresent()
            assert properties["display_name"].writeMethod.isPresent()
            assert !properties["display_name"].isReadOnly()
            return element
        }
    }

    void "test property type annotations remain after bean properties are resolved"() {
        expect:
        buildClassElement('''
from micronaut.core.annotation import Introspected

@Introspected
class Parameters:
    stamp_width: int
    stamp_height: int
    page_number: int

class MyDto:
    parameters: Parameters

    @property
    def settings(self) -> Parameters:
        return self.parameters

    @settings.setter
    def settings(self, value: Parameters):
        self.parameters = value
''', "MyDto") { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }
            def parameters = properties["parameters"]
            def settings = properties["settings"]
            def expectedAnnotations = ["io.micronaut.core.annotation.Introspected"]

            assert parameters != null
            assert parameters.field.isEmpty()
            assert parameters.type.annotationNames.sort() == expectedAnnotations
            assert parameters.genericType.annotationNames.sort() == expectedAnnotations
            assert parameters.readType.get().annotationNames.sort() == expectedAnnotations
            assert parameters.writeType.get().annotationNames.sort() == expectedAnnotations

            assert settings != null
            assert settings.field.isEmpty()
            assert settings.type.annotationNames.sort() == expectedAnnotations
            assert settings.genericType.annotationNames.sort() == expectedAnnotations
            assert settings.readType.get().annotationNames.sort() == expectedAnnotations
            assert settings.writeType.get().annotationNames.sort() == expectedAnnotations
            return element
        }
    }

    void "test protocol bean properties"() {
        expect:
        buildClassElement('''
from typing import Protocol

class HealthResult(Protocol):
    name: str
    status: object
    details: object
''', "HealthResult") { ClassElement element ->
            def properties = element.beanProperties

            assert properties
            assert properties*.name as Set == ["name", "status", "details"] as Set
            assert properties.find { it.name == "name" }.type.name == String.name
            return element
        }
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0034")
    void "test inherited properties keep declaring and owning types"() {
        expect:
        buildClassElement('''
class EntityDto:
    id: str
    tenant_id: int
    version: int

class PersonDto(EntityDto):
    name: str
    parent_name: str
''', "PersonDto") { ClassElement element ->
            def properties = element.beanProperties.findAll {
                it.name in ["id", "tenant_id", "version", "name", "parent_name"]
            }
            def propertyNames = properties*.name as Set
            def declaredByChild = properties.findAll { it.declaringType.name == "python.PersonDto" }*.name as Set
            def declaredByParent = properties.findAll { it.declaringType.name == "python.EntityDto" }*.name as Set
            def ownedByChild = properties.findAll { it.owningType.name == "python.PersonDto" }*.name as Set

            assert propertyNames == ["id", "tenant_id", "version", "name", "parent_name"] as Set
            assert declaredByChild == ["name", "parent_name"] as Set
            assert declaredByParent == ["id", "tenant_id", "version"] as Set
            assert ownedByChild == propertyNames
            return element
        }
    }

    @PendingFeature(reason = "Tracked in inject-python-test/DISABLED_TESTS.md: PY-INJECT-0032")
    void "test bean properties with generics"() {
        expect:
        buildClassElement('''
from typing import Generic, TypeVar

T = TypeVar("T")

class Response(Generic[T]):
    result: T

class BookController:
    response: Response[int]
''', "BookController") { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }
            def response = properties["response"]

            assert response != null
            assert response.type.name == "python.Response"
            assert response.genericType.firstTypeArgument.get().name == Integer.name

            def responseProperty = response.genericType.beanProperties.find { it.name == "result" }
            assert responseProperty != null
            assert responseProperty.genericType.name == Integer.name
            return element
        }
    }
}
