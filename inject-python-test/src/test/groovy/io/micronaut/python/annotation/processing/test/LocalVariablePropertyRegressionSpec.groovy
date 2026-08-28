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

class LocalVariablePropertyRegressionSpec extends AbstractPythonTypeElementSpec {

    void "local variables inside method are not class properties"() {
        given:
        String python = '''
class VehicleSpec:
    def test_start_vehicle(self) -> None:
        Vehicle = java.type("micronaut.docs.qualifiers.annotationmember.Vehicle")
        tmp = 1
        msg: str = "hello"
        return None
'''

        expect:
        buildClassElement(python) { ClassElement classElement ->
            assert classElement.simpleName == 'VehicleSpec'
            // No bean properties should be inferred from local variables inside methods
            assert classElement.beanProperties.isEmpty()
            return classElement
        }


    }
}
