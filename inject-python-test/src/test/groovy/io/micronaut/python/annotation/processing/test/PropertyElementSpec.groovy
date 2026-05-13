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
