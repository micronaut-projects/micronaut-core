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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.FieldElement

/**
 * Class attributes without a type annotation get their type from the assigned literal.
 */
class UntypedClassAttributeSpec extends AbstractPythonTypeElementSpec {

    void "test untyped class attributes are typed from their literal value"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton

@Singleton
class Customizer:
    CONNECTION_TIMEOUT = 25000
    READ_TIMEOUT = 35_000
    RATIO = 0.5
    NAME = "customizer"
    ENABLED = True
    TAGS = ["a", "b"]
    OPTIONS = {"key": "value"}
    NOTHING = None
    RAW = b"bytes"
    COMPUTED = len("abc")

    def timeout(self) -> int:
        return self.CONNECTION_TIMEOUT
'''

        when:
        Map<String, ClassElement> fieldTypes = buildClassElement(pythonCode, "Customizer") { ClassElement element ->
            element.getFields().collectEntries { FieldElement field ->
                [(field.name): field.type]
            }
        }

        then: "every attribute has a type"
        fieldTypes.values().every { it != null }
        fieldTypes.CONNECTION_TIMEOUT.name == "int"
        fieldTypes.READ_TIMEOUT.name == "int"
        fieldTypes.RATIO.name == "double"
        fieldTypes.NAME.name == String.name
        fieldTypes.ENABLED.name == "boolean"
        fieldTypes.TAGS.name == List.name
        fieldTypes.OPTIONS.name == Map.name
        fieldTypes.NOTHING.name == Object.name
        fieldTypes.RAW.name == "byte"
        fieldTypes.RAW.isArray()
        fieldTypes.COMPUTED.name == Object.name
    }

    void "test an untyped class attribute does not break the stubs of the other classes"() {
        given:
        def pythonCode = '''
from jakarta.inject import Singleton

@Singleton
class Customizer:
    CONNECTION_TIMEOUT = 25000

    def timeout(self) -> int:
        return self.CONNECTION_TIMEOUT

@Singleton
class Sibling:
    def __init__(self, customizer: Customizer):
        self.customizer = customizer

    def timeout(self) -> int:
        return self.customizer.timeout()
'''

        when:
        def context = buildContext(pythonCode)
        def customizer = getBean(context, "python.Customizer")
        def sibling = getBean(context, "python.Sibling")

        then:
        customizer.asPolyglotValue().invokeMember("timeout").asInt() == 25000
        sibling.asPolyglotValue().invokeMember("timeout").asInt() == 25000
        customizer.asPolyglotValue().getMember("CONNECTION_TIMEOUT").asInt() == 25000

        cleanup:
        context?.close()
    }
}
