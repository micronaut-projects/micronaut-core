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
import io.micronaut.inject.ast.EnumElement

class EnumElementSpec extends AbstractPythonTypeElementSpec {

    void "test is enum"() {
        expect:
        buildClassElement('''
from enum import Enum

class Status(Enum):
    ACTIVE = "active"
    DISABLED = "disabled"
''') { ClassElement element ->
            assert element instanceof EnumElement
            assert element.isEnum()
            return element
        }
    }

    void "test enum values and constant elements"() {
        expect:
        buildClassElement('''
import enum

class Status(enum.Enum):
    ACTIVE = "active"
    DISABLED = "disabled"
''') { EnumElement element ->
            assert element.values() == ["ACTIVE", "DISABLED"]

            def constants = element.elements()
            assert constants*.name == ["ACTIVE", "DISABLED"]
            assert constants.every { it.type.isEnum() }
            assert constants.every { it.declaringType == element }
            assert constants.every { it.owningType == element }
            return element
        }
    }

    void "test enum constants are not bean properties"() {
        expect:
        buildClassElement('''
from enum import Enum

class Player(Enum):
    WHITE = "w"
    BLACK = "b"

    def to_string(self) -> str:
        return self.value
''') { EnumElement element ->
            assert element.values() == ["WHITE", "BLACK"]
            assert element.elements()*.name == ["WHITE", "BLACK"]
            assert !element.getBeanProperties()*.name.contains("WHITE")
            assert !element.getBeanProperties()*.name.contains("BLACK")
            assert element.findMethod("to_string").isPresent()
            return element
        }
    }

    void "test enum JsonValue method is preserved as annotation metadata and backs Java toString"() {
        given:
        def source = '''
from enum import Enum

from com.fasterxml.jackson.annotation import JsonValue

class Player(Enum):
    WHITE = "w"
    BLACK = "b"

    @JsonValue
    def to_string(self) -> str:
        return self.value
'''

        expect:
        buildClassElement(source) { EnumElement element ->
            def method = element.findMethod("to_string").get()
            assert method.hasAnnotation("com.fasterxml.jackson.annotation.JsonValue")
            return element
        }

        when:
        def context = buildContext(source)
        Class<? extends Enum> playerType = context.classLoader.loadClass("python.Player")
        def white = Enum.valueOf(playerType, "WHITE")
        def black = Enum.valueOf(playerType, "BLACK")

        then:
        white.toString() == "w"
        black.toString() == "b"

        cleanup:
        context?.close()
    }

    void "test enum method return and parameter types"() {
        expect:
        buildClassElement('''
from micronaut.http import HttpMethod

class Route:
    def handle(self, argument: HttpMethod) -> HttpMethod:
        return argument
''', "Route") { ClassElement element ->
            def method = element.findMethod("handle").get()

            assert method.returnType instanceof EnumElement
            assert method.returnType.values().contains("GET")
            assert method.parameters.size() == 1
            assert method.parameters[0].type instanceof EnumElement
            assert method.parameters[0].type.values().contains("POST")
            return element
        }
    }

    void "test enum method parameter collection type argument"() {
        expect:
        buildClassElement('''
from micronaut.http import HttpMethod

class Route:
    def handle(self, methods: list[HttpMethod]) -> None:
        pass
''', "Route") { ClassElement element ->
            def method = element.findMethod("handle").get()
            def argumentType = method.parameters[0].genericType.firstTypeArgument.get()

            assert argumentType instanceof EnumElement
            assert argumentType.isEnum()
            assert argumentType.values().contains("GET")
            return element
        }
    }
}
