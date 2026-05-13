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
}
