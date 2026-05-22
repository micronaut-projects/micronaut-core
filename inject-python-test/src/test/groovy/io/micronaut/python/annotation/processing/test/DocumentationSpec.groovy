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
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement

class DocumentationSpec extends AbstractPythonTypeElementSpec {

    void "test class method property and parameter documentation"() {
        expect:
        buildClassElement('''
class Documented:
    """Class level docs.

    Notes:
        Additional class notes.
    """

    title: str
    """Title docs."""

    count: int

    def __init__(self, title: str, count: int):
        """Create a documented instance.

        Args:
            title: The title doc
            count: The count doc
        """
        self.title = title
        self.count = count

    def calculate(self, amount: int) -> int:
        """Calculate the value.

        Args:
            amount: The amount doc

        Returns:
            The calculated value
        """
        return amount + self.count

    @property
    def display_name(self) -> str:
        """Display name docs.

        Returns:
            The display name
        """
        return self.title

    @display_name.setter
    def display_name(self, value: str):
        """Set display name docs.

        Args:
            value: The display value
        """
        self.title = value
''') { ClassElement element ->
            assert element.getDocumentation(true).get() == "Class level docs."
            assert element.getDocumentation(false).get().contains("Additional class notes.")

            def constructor = element.getPrimaryConstructor().get()
            assert constructor.getDocumentation(true).get() == "Create a documented instance."
            assert constructor.parameters*.name == ["title", "count"]
            assert constructor.parameters[0].getDocumentation(true).get() == "The title doc"
            assert constructor.parameters[1].getDocumentation(true).get() == "The count doc"

            MethodElement method = element.getEnclosedElement(ElementQuery.ALL_METHODS.named("calculate")).get()
            assert method.getDocumentation(true).get() == "Calculate the value."
            assert method.getDocumentation(false).get().contains("Returns:")
            assert method.parameters[0].getDocumentation(true).get() == "The amount doc"

            def properties = element.beanProperties.collectEntries { [it.name, it] }
            assert properties["display_name"].getDocumentation(true).get() == "Display name docs."
            return element
        }
    }

    void "test attribute documentation is exposed on bean properties"() {
        expect:
        buildClassElement('''
class Documented:
    title: str
    """Title docs."""
''') { ClassElement element ->
            def properties = element.beanProperties.collectEntries { [it.name, it] }
            assert properties["title"].getDocumentation(true).get() == "Title docs."
            return element
        }
    }
}
