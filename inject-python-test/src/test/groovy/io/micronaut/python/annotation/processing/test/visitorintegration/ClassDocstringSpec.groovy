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
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

/**
 * Class and function docstrings are reported the way {@code inspect.cleandoc} renders them, like attribute
 * docstrings: without the indentation of the source and the surrounding blank lines.
 */
class ClassDocstringSpec extends AbstractPythonTypeElementSpec {

    void "test class and method docstrings are trimmed like attribute docstrings"() {
        when:
        def docs = buildClassElement('''
from dataclasses import dataclass


@dataclass
class Llama:
    """
    A llama. <4>

    Llamas are domesticated South American camelids.
        Indented example line.
    """

    name: str
    """The name of the llama."""

    age: int
    """
        The age of the llama.
    """

    def speak(self) -> str:
        """
        Says hello.

        Returns:
            The greeting.
        """
        return "hello"
''', "Llama") { ClassElement element ->
            def speak = element.getEnclosedElement(ElementQuery.ALL_METHODS.named("speak")).get()
            [
                clazz: element.getDocumentation().get(),
                parsed: element.getDocumentation(true).get(),
                name: element.fields.find { it.name == "name" }.getDocumentation().get(),
                age: element.fields.find { it.name == "age" }.getDocumentation().get(),
                speak: speak.getDocumentation().get(),
                speakParsed: speak.getDocumentation(true).get(),
            ]
        }

        then:
        docs.clazz == "A llama. <4>\n\nLlamas are domesticated South American camelids.\n    Indented example line."
        docs.parsed == "A llama. <4>\n\nLlamas are domesticated South American camelids.\n    Indented example line."
        docs.name == "The name of the llama."
        docs.age == "The age of the llama."
        docs.speak == "Says hello.\n\nReturns:\n    The greeting."
        docs.speakParsed == "Says hello."
    }
}
